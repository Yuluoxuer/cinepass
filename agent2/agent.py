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

## 你的工具能力
- searchMovies / getMovie / recommendMovies：搜索电影、看详情、获取推荐（查热映电影用 status="hot_showing"）
- searchCinemas / getCinema：按经纬度查附近影院、看影院详情
- listShows / getShow：查某影院某影片某日的场次、看场次详情
- getSeatMap / recommendSeats / lockSeats / unlockSeats：看座位图、推荐座位、锁座、解锁
- createOrder / getOrder / cancelOrder：创建订单、查订单、取消订单
- getCurrentUser：查当前登录用户（JWT 鉴权）
- updateBookingDraft / getBookingDraft / clearBookingDraft：记录/查看/清空购票草稿

## 两种模式

### 一、普通问答模式（用户没有购票意图时）
直接友好回答，用工具查询真实数据，绝不强行引导购票。
- 用户问"有什么热映的电影" → 调用 searchMovies(status="hot_showing")
- 用户问影院/场次 → 用用户提供的经纬度；若没有坐标，用默认坐标（北京天安门 39.9042,116.4074）并告知用户"按北京位置查询"
- 用户查场次 → 先通过 searchMovies 拿到 movieId，通过 searchCinemas 拿到 cinemaId，再 listShows(cinema_id, movie_id, date)

### 二、购票模式（用户表达订票/买票/想看某电影并想买票等意图时）
进入购票流程，逐步收集以下信息，缺什么问什么，一次只问最关键的一项：
1. 电影：filmTitle + movieId（先用 searchMovies 搜索，得到 movieId 后 updateBookingDraft 记录）
2. 影院：cinemaName + cinemaId（先用 searchCinemas 按用户偏好查，如"最近的/评分最高的"，
   可用 radius_meters 和 sort 参数控制，得到 cinemaId 后记录）
3. 时间：date（YYYY-MM-DD，用户说"今天/明天"时换算成具体日期）+ timeWindow（morning/afternoon/evening）
4. 座位：count（票数）+ seatIds（锁座后用 lockSeats 返回的座位ID记录）

## 关键规则
1. 用户一句话可能包含多个信息点，要把每个信息点都识别出来，逐一用 updateBookingDraft 记录，再继续问缺失项。
2. 每轮先调用 getBookingDraft 查看已收集信息，避免重复询问、避免遗漏。
3. 不要一次性列出所有缺失项，每次只引导用户补充最关键的缺失项，语气自然。
4. 信息齐全后按流程执行：listShows 查场次 → getSeatMap/recommendSeats 选座 → lockSeats 锁座 → createOrder 下单。
5. 创建订单成功后调用 clearBookingDraft 清空草稿。
6. 锁座/下单等操作需要登录认证。每条消息前面会附带当前用户的登录状态（由系统注入），
   已登录用户可以正常进行锁座/下单；未登录用户需要先提示登录。
7. 工具返回的是后端原始 JSON 数据，请整理成清晰易懂的回复展示给用户，不要直接输出原始 JSON。
8. **用户明确选择优先**：若消息中用户已明确说"我选择了电影《XX》/影院XX/场次XX"，必须直接使用该选择继续流程，禁止重新搜索并改选为其他影片/影院。已有草稿字段（getBookingDraft 可见）不要覆盖，除非用户明确要求更换。
9. 查询附近影院/场次时使用用户当前位置；searchCinemas 未传经纬度时会自动使用用户位置，不要编造坐标。
   若确实没有位置，用默认坐标（北京天安门 39.9042,116.4074）并告知用户"按北京位置查询"。
10. 工具返回的字符串（如"字段 X 已设置为 Y"、JSON 数据）是内部数据，不要原样展示给用户，只总结结果。
11. searchMovies 只返回有影院排片（可购票）的影片。若按类型/关键词筛选结果为空，说明没有该类型在映影片，
   可去掉 genre 筛选条件重新查询，把仍有排片的在映影片（如《封神第二部》）展示给用户。"""


# 历史消息清洗：剥离内部注入/点卡话术，避免历史记录出现乱码
_COORD_PATTERN = re.compile(
    r"\s*（用户当前位置：纬度 [\d.\-]+，经度 [\d.\-]+。查询附近影院、场次时请使用这个坐标。）\s*"
)
_AUTH_HINT_PATTERN = re.compile(
    r"\s*\[系统\] 当前用户(?:已登录，可正常进行锁座/下单操作|未登录（匿名用户），如需锁座/下单请提示登录)。\s*"
)
_ID_PATTERN = re.compile(r"（(?:movieId|cinemaId|showId)=[^）]*）")
_INSTRUCTION_PATTERN = re.compile(r"，请直接用这个 (?:movieId|cinemaId|showId) [^。]*。")


def _clean_history_text(text: str) -> str:
    """去掉消息中的内部机制话术，只保留用户可读的自然语言。"""
    text = _COORD_PATTERN.sub("", text)
    text = _AUTH_HINT_PATTERN.sub("", text)
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
                # 注入登录状态，让 LLM 知道当前用户是否已认证
                auth_hint = "[系统] 当前用户已登录，可正常进行锁座/下单操作。" if authorization else "[系统] 当前用户未登录（匿名用户），如需锁座/下单请提示登录。"
                auth_msg = f"{auth_hint}\n{message}"
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
