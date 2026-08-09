"""主Agent类 - 使用LangGraph create_react_agent + PostgresSaver + BookingDraft"""
from __future__ import annotations
import re
from typing import Any, AsyncIterator, Optional
from langgraph.prebuilt import create_react_agent
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
from psycopg_pool import AsyncConnectionPool
from .config import get_settings
from .tools import get_all_tools, ensure_table as ensure_draft_table
from .tools.booking_draft import use_session_id
from .llm import get_llm
from .request_context import use_authorization, use_location

SYSTEM_PROMPT = """你是「购票助手」，一个专业的电影票务智能助手。

## 工具能力
- searchMovies/getMovie/recommendMovies：搜索电影、获取详情
- searchCinemas/getCinema：按经纬度查附近影院
- listShows/getShow：查场次
- getSeatMap/recommendSeats/lockSeats/unlockSeats：座位图、推荐座位、锁座、解锁
- createOrder/getOrder/cancelOrder：创建/查看/取消订单
- getCurrentUser：查当前登录用户
- updateBookingDraft/getBookingDraft/clearBookingDraft：读写购票草稿

## 普通问答模式
用户没有购票意图时直接友好回答。消息开头包含用户当前位置经纬度，回答"我在哪里"时直接使用这些坐标。

## 购票模式 — 完整流程
逐步收集信息，缺什么问什么，一次只问一项：
1. 电影 → searchMovies 搜索 → updateBookingDraft 记录 movieId+filmTitle
2. 影院 → searchCinemas 查询 → updateBookingDraft 记录 cinemaId+cinemaName
3. 场次 → listShows 查询，返回场次卡片（选场次后系统自动带日期）
4. **票数（座位数量）→ 选场次后、选座前，必须询问用户需要几张票，确认后再记录 count。**
   **无论草稿里是否已有 count，只要用户还没在本轮对话明确说过要几张，都必须先问一次。**
5. 座位 → getSeatMap 获取座位图 → **必须调用 recommendSeats 获取推荐座位并展示给用户**，
   推荐给用户后再让用户决定是否调整，不要只展示空座位图让用户自己选
6. 锁座 → 用户确认选座后 lockSeats → 系统会自动创建订单并生成付款二维码，
   你只需要展示下单摘要（影片、影院、场次、座位位置、票数、总金额）即可，不要再调用 searchMovies 或其他无关工具
7. 若用户觉得太贵想换座位区 → 用户重新点选座位卡片即可，不要重新搜索电影

## 关键规则
- 每轮先 getBookingDraft 查看已有信息，用工具查数据不编造
- **严禁向用户提及"草稿/draft/bookingDraft"等内部概念**。需要确认票数时用自然语言，
  如"请问需要买几张票呢？"，而不是"您的购票草稿显示需要 1 张票"。
- 消息开头 [系统] 标注了登录状态和经纬度，据此判断是否需要提示登录
- 不要输出原始 JSON，整理成清晰易懂的文字
- 用户已选择的内容禁止重新搜索改选，除非用户明确要求更换
- **锁座成功后禁止再调用 searchMovies / recommendSeats / getSeatMap 等工具**，直接展示摘要即可
- 展示座位时优先展示推荐方案（recommendSeats 的 plans），给用户看已选好的推荐座位，
  不要只罗列可选座位让用户自己挑"""



# 历史消息清洗：剥离内部注入/点卡话术，避免历史记录出现乱码
_COORD_PATTERN = re.compile(
    r"\s*（用户当前位置：纬度 [\d.\-]+，经度 [\d.\-]+。查询附近影院、场次时请使用这个坐标。）\s*"
)
_AUTH_HINT_PATTERN = re.compile(
    r"\s*\[系统\] 当前用户(?:已登录，可正常锁座/下单|未登录（匿名用户），锁座/下单需先提示登录)。\s*"
)
_LOC_HINT_PATTERN = re.compile(
    r"\s*\[系统\] 用户当前位置：纬度 [\d.\-]+，经度 [\d.\-]+。查询附近影院/场次时请使用此坐标。\s*"
)
_ID_PATTERN = re.compile(r"（(?:movieId|cinemaId|showId)=[^）]*）")
_INSTRUCTION_PATTERN = re.compile(r"，请直接用这个 (?:movieId|cinemaId|showId) [^。]*。")


def _clean_history_text(text: str) -> str:
    """去掉消息中的内部机制话术，只保留用户可读的自然语言。"""
    text = _COORD_PATTERN.sub("", text)
    text = _AUTH_HINT_PATTERN.sub("", text)
    text = _LOC_HINT_PATTERN.sub("", text)
    text = _ID_PATTERN.sub("", text)
    text = _INSTRUCTION_PATTERN.sub("。", text)
    return text.strip()


class MovieTicketAgent:
    """电影票务Agent"""

    def __init__(self):
        self.settings = get_settings()
        self.llm = get_llm()
        self.tools = get_all_tools()
        self.checkpointer: Optional[AsyncPostgresSaver] = None
        self._pool: Optional[AsyncConnectionPool] = None
        self.agent = None

    async def initialize(self):
        """初始化Agent和PostgresSaver"""
        # 创建连接池 + PostgresSaver（autocommit 以支持 CREATE INDEX CONCURRENTLY）
        self._pool = AsyncConnectionPool(
            self.settings.postgres_uri, open=False, kwargs={"autocommit": True}
        )
        await self._pool.open()
        self.checkpointer = AsyncPostgresSaver(self._pool)
        await self.checkpointer.setup()
        # 初始化 booking_drafts 表
        await ensure_draft_table()

        # 使用 create_react_agent 创建带 checkpoint 的 agent
        self.agent = create_react_agent(
            model=self.llm,
            tools=self.tools,
            checkpointer=self.checkpointer,
            prompt=SYSTEM_PROMPT,
        )

    async def run(
        self,
        message: str,
        session_id: str,
        user_id: Optional[str] = None,
        **context
    ) -> str:
        """运行Agent处理用户消息。
        context 可传 authorization(JWT)、latitude、longitude 等。"""
        if self.agent is None:
            raise RuntimeError("Agent未初始化，请先调用initialize()")

        authorization = context.get("authorization")
        try:
            # thread_id 作为会话标识；同时注入 JWT 与 session_id 上下文
            config = {
                "configurable": {
                    "thread_id": session_id,
                    "user_id": user_id or "anonymous",
                }
            }

            with (
                use_authorization(authorization),
                use_session_id(session_id),
                use_location(context.get("latitude"), context.get("longitude")),
            ):
                result = await self.agent.ainvoke(
                    {"messages": [{"role": "user", "content": message}]},
                    config=config,
                )

            # 获取最后一条 assistant 消息
            response = ""
            if result and "messages" in result:
                for msg in reversed(result["messages"]):
                    if hasattr(msg, "content") and hasattr(msg, "type"):
                        if msg.type == "ai":
                            response = msg.content
                            break
                    elif isinstance(msg, dict) and msg.get("role") == "assistant":
                        response = msg.get("content", "")
                        break

            if not response:
                response = "抱歉，我无法处理这个请求。"

            return response

        except Exception as e:
            import logging
            import traceback
            logging.getLogger(__name__).error(
                "Agent run error: %s\n%s", e, traceback.format_exc()
            )
            return "处理请求时出错，请稍后重试。"

    async def stream(
        self,
        message: str,
        session_id: str,
        user_id: Optional[str] = None,
        **context,
    ) -> AsyncIterator[dict[str, Any]]:
        """流式运行Agent处理用户消息，供 FastAPI SSE 消费。

        context 可传 authorization(JWT)、latitude、longitude 等。
        产出事件 dict：
          {"type": "token", "content": "..."}                 — LLM 文本片段
          {"type": "done",  "reply": "...", "session_id": "..."} — 整轮结束
          {"type": "error", "message": "..."}                 — 出错（含 traceback）
        """
        if self.agent is None:
            raise RuntimeError("Agent未初始化，请先调用initialize()")

        authorization = context.get("authorization")
        config = {
            "configurable": {
                "thread_id": session_id,
                "user_id": user_id or "anonymous",
            }
        }

        parts: list[str] = []
        try:
            with (
                use_authorization(authorization),
                use_session_id(session_id),
                use_location(context.get("latitude"), context.get("longitude")),
            ):
                async for event in self.agent.astream_events(
                    {"messages": [{"role": "user", "content": message}]},
                    config=config,
                    version="v2",
                ):
                    # 只取 LLM 文本 token；工具调用的 chunk.content 为空，自动跳过
                    if event.get("event") == "on_chat_model_stream":
                        chunk = event.get("data", {}).get("chunk")
                        content = getattr(chunk, "content", None)
                        if content:
                            parts.append(content)
                            yield {"type": "token", "content": content}

            reply = "".join(parts) or "抱歉，我无法处理这个请求。"
            yield {"type": "done", "reply": reply, "session_id": session_id}

        except Exception as e:
            import logging
            import traceback
            logging.getLogger(__name__).error(
                "Agent stream error: %s\n%s", e, traceback.format_exc()
            )
            yield {
                "type": "error",
                "message": "处理请求时出错，请稍后重试",
            }

    async def run_with_trace(
        self,
        message: str,
        session_id: str,
        user_id: Optional[str] = None,
        **context,
    ) -> dict[str, Any]:
        """运行 Agent 并返回回复 + 工具调用轨迹（供前端生成动态卡片）。

        返回 ``{"reply": str, "tool_calls": [{"name", "content"}, ...]}``：
        ``tool_calls`` 取自本轮 LangGraph 消息中的 ToolMessage（name=工具名，content=原始返回）。
        """
        if self.agent is None:
            raise RuntimeError("Agent未初始化，请先调用initialize()")

        authorization = context.get("authorization")
        config = {
            "configurable": {
                "thread_id": session_id,
                "user_id": user_id or "anonymous",
            }
        }

        tool_calls: list[dict[str, Any]] = []
        try:
            with (
                use_authorization(authorization),
                use_session_id(session_id),
                use_location(context.get("latitude"), context.get("longitude")),
            ):
                # 注入登录状态和位置坐标，让 LLM 知道用户上下文
                auth_hint = "[系统] 当前用户已登录，可正常锁座/下单。" if authorization else "[系统] 当前用户未登录（匿名用户），锁座/下单需先提示登录。"
                lat = context.get("latitude")
                lng = context.get("longitude")
                loc_hint = f"[系统] 用户当前位置：纬度 {lat}，经度 {lng}。查询附近影院/场次时请使用此坐标。" if (lat is not None and lng is not None) else ""
                hint = f"{auth_hint}\n{loc_hint}".strip()
                auth_msg = f"{hint}\n{message}" if hint else message
                result = await self.agent.ainvoke(
                    {"messages": [{"role": "user", "content": auth_msg}]},
                    config=config,
                )
            messages = result.get("messages", []) if isinstance(result, dict) else []

            reply = ""
            for msg in reversed(messages):
                if getattr(msg, "type", "") == "ai" and getattr(msg, "content", None):
                    content = getattr(msg, "content", "")
                    reply = content if isinstance(content, str) else str(content)
                    break

            for msg in messages:
                if getattr(msg, "type", "") == "tool":
                    content = getattr(msg, "content", "")
                    tool_calls.append({
                        "name": getattr(msg, "name", ""),
                        "content": content if isinstance(content, str) else str(content),
                    })

            return {"reply": reply or "抱歉，我暂时无法处理这个请求。", "tool_calls": tool_calls}

        except Exception as e:
            import logging
            import traceback
            logging.getLogger(__name__).error(
                "Agent run_with_trace error: %s\n%s", e, traceback.format_exc()
            )
            return {"reply": "处理请求时出错，请稍后重试。", "tool_calls": tool_calls}

    async def get_history(self, session_id: str, limit: int = 20) -> list[dict[str, str]]:
        """从 PostgresSaver 读取该会话的消息历史（供前端历史记录）。

        只返回真实对话（user/assistant 最终输出）：过滤工具调用（ToolMessage）、系统提示词，
        并剥离注入的坐标/内部点卡话术，避免历史记录出现乱码。
        """
        if self.agent is None:
            return []
        config = {"configurable": {"thread_id": session_id}}
        try:
            state = await self.agent.aget_state(config)
            messages = (state.values or {}).get("messages", []) if state else []
            history: list[dict[str, str]] = []
            for m in messages:
                content = getattr(m, "content", "")
                if not content:
                    continue
                t = getattr(m, "type", "")
                # 只保留用户输入与助手最终回复；过滤工具结果、系统提示等内部消息
                if t not in ("human", "ai"):
                    continue
                role = "assistant" if t == "ai" else "user"
                text = content if isinstance(content, str) else str(content)
                text = _clean_history_text(text)
                if not text:
                    continue
                history.append({"role": role, "content": text})
            return history[-limit:]
        except Exception:
            return []

    async def close(self):
        """关闭Agent资源"""
        if self._pool:
            await self._pool.close()
            self._pool = None


_agent: Optional[MovieTicketAgent] = None

async def get_agent() -> MovieTicketAgent:
    """获取全局Agent实例"""
    global _agent
    if _agent is None:
        _agent = MovieTicketAgent()
        await _agent.initialize()
    return _agent


async def close_agent() -> None:
    """关闭全局Agent实例（释放 Postgres 连接池）。"""
    global _agent
    if _agent is not None:
        await _agent.close()
        _agent = None
