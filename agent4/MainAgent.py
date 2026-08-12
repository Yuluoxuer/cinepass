"""MainAgent：监督者（Supervisor）agent。

职责：
- 每轮用 LLM 决定路由到哪个子 agent（chat/movie/cinema/show/seat/order/helper）
- 执行子 agent 节点，累积回复/卡片/草稿/历史
- LLM 决策失败时降级为确定性规则路由
"""
from __future__ import annotations

import re
from typing import Any, Literal

from pydantic import BaseModel

from agent4.api.cards import build_cards
from agent4.llm import get_llm
from agent4.state import Agent4State
from agent4.tools.AgentTools import load_middle_draft

MAX_STEPS = 6

_SUBAGENT_KEYS = ("chat", "movie", "cinema", "show", "seat", "order", "helper")
_NEXT_LITERAL = Literal["chat", "movie", "cinema", "show", "seat", "order", "helper", "FINISH"]


class SupervisorDecision(BaseModel):
    next: _NEXT_LITERAL
    reason: str = ""


_SUPERVISOR_PROMPT = """你是电影购票助手的监督者（Supervisor），负责把每轮请求分派给合适的子 agent。

可用子 agent：
- chat：闲聊、普通问答、信息性追问
- movie：搜索/推荐电影、看详情、选片
- cinema：按位置/影片查影院、选影院
- show：查某影院某影片的场次、选场次
- seat：查座位图、推荐座位、锁座、选座
- order：创建/查询/取消订单、支付
- helper：查登录用户、查看/管理草稿

当前购票草稿：{draft}
用户消息：{message}
上一步子 agent 输出：{last_reply}

决策规则（按优先级）：
1. 闲聊/普通提问/信息追问 → chat。
2. 上一步子 agent 已针对当前消息给出查询/推荐结果（last_reply 非空）→ 说明结果已展示给用户，请输出 FINISH 等待用户选择，不要重复调用子 agent 查询同样的内容。
3. 用户要看电影/搜索电影/选片，且草稿缺 movieId → movie。
4. 草稿已有 movieId 但缺 cinemaId → cinema。
5. 已有 cinemaId 但缺 showId → show。
6. 已有 showId 但缺 seatIds → seat。
7. 用户明确确认下单/支付 → order。
8. 用户要查登录信息/草稿 → helper。
只返回一个 next 决策，不要解释。"""


def _rule_route(message: str, draft: dict[str, Any], last_reply: str = "") -> str:
    """无 LLM 时的确定性路由降级。"""
    text = message or ""
    if re.search(r"你好|谢谢|再见|你是谁|帮忙|请问|吗$", text) and not re.search(r"看|电影|片|选|订|买|影院|场次|座位", text):
        return "chat"
    if re.search(r"登录|我是谁|我的信息|查.*草稿|看.*草稿", text):
        return "helper"
    if re.search(r"确认|下单|支付|付款|买单", text) and (draft.get("lockId") or draft.get("orderId")):
        return "order"
    # 子 agent 已查询展示但未推进草稿关键字段 → 等待用户选择，避免重复查询
    if last_reply and not any(draft.get(k) for k in ("movieId", "cinemaId", "showId", "seatIds")):
        return "FINISH"
    if not draft.get("movieId"):
        return "movie"
    if not draft.get("cinemaId"):
        return "cinema"
    if not draft.get("showId"):
        return "show"
    if not draft.get("seatIds"):
        return "seat"
    if re.search(r"确认|下单|支付|付款", text):
        return "order"
    return "FINISH"


def _extract_ai_reply(messages: list[Any]) -> str:
    for m in reversed(messages):
        if getattr(m, "type", "") == "ai" and getattr(m, "content", None):
            content = getattr(m, "content", "")
            return content if isinstance(content, str) else str(content)
    return ""


def _extract_tool_calls(messages: list[Any]) -> list[dict[str, Any]]:
    out = []
    for m in messages:
        if getattr(m, "type", "") == "tool":
            content = getattr(m, "content", "")
            out.append({
                "name": getattr(m, "name", ""),
                "content": content if isinstance(content, str) else str(content),
            })
    return out


class MainAgent:
    """监督者 agent：持有全部子 agent，负责决策与子 agent 节点执行。"""

    def __init__(self, sub_agents: dict[str, Any]):
        self.sub_agents = sub_agents
        self.subagent_keys = set(sub_agents.keys())
        self.max_steps = MAX_STEPS

    async def _decide(self, message: str, draft: dict[str, Any], last_reply: str) -> str:
        llm = get_llm()
        draft_summary = ", ".join(f"{k}={v}" for k, v in draft.items() if v) if draft else "空"
        try:
            structured = llm.with_structured_output(SupervisorDecision)
            decision = await structured.ainvoke(
                _SUPERVISOR_PROMPT.format(
                    draft=draft_summary, message=message or "", last_reply=last_reply or "无"
                )
            )
            nxt = decision.next
            return nxt if nxt in self.subagent_keys or nxt == "FINISH" else "FINISH"
        except Exception:
            return _rule_route(message, draft, last_reply)

    async def supervisor_node(self, state: Agent4State) -> dict[str, Any]:
        nxt = await self._decide(
            state.get("message") or "",
            state.get("bookingdraft") or {},
            state.get("last_reply") or "",
        )
        return {"next": nxt}

    async def subagent_node(self, state: Agent4State, key: str) -> dict[str, Any]:
        message = state.get("message") or ""
        draft = state.get("bookingdraft") or {}
        hist = state.get("history") or []
        sid = state.get("sessionId") or ""
        lat = state.get("latitude")
        lng = state.get("longitude")

        msgs: list[dict[str, str]] = []
        for h in hist[-4:]:
            if h.get("role") in ("user", "assistant") and h.get("content"):
                msgs.append({"role": h["role"], "content": h["content"]})
        task = message
        if draft:
            task += f"\n当前购票草稿：{draft}"
        if lat is not None and lng is not None:
            task += f"\n用户位置：纬度{lat}，经度{lng}"
        msgs.append({"role": "user", "content": task})

        agent = self.sub_agents[key]
        result = await agent.ainvoke({"messages": msgs})
        messages = result.get("messages", []) if isinstance(result, dict) else []
        reply = _extract_ai_reply(messages) or ""
        tool_calls = _extract_tool_calls(messages)
        cards = build_cards(tool_calls, draft)

        new_draft = draft
        if sid:
            try:
                new_draft = await load_middle_draft(sid) or draft
                # 过滤内部字段（如 _history），避免泄露给上层/回复
                new_draft = {k: v for k, v in new_draft.items() if not str(k).startswith("_")}
            except Exception:
                pass

        return {
            "reply_parts": (state.get("reply_parts") or []) + [reply],
            "cards": (state.get("cards") or []) + cards,
            "bookingdraft": new_draft,
            "history": (state.get("history") or []) + [
                {"role": "user", "content": message},
                {"role": "assistant", "content": reply},
            ],
            "last_reply": reply,
            "steps": (state.get("steps") or 0) + 1,
        }

    def make_subagent_node(self, key: str):
        """生成 async 闭包节点（LangGraph 只对 coroutine function 自动 await）。"""
        async def _node(state: Agent4State) -> dict[str, Any]:
            return await self.subagent_node(state, key)
        return _node


__all__ = ["MainAgent", "MAX_STEPS", "SupervisorDecision", "_rule_route", "SUBAGENT_KEYS"]
