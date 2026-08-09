"""agent3 监督者（Supervisor）图：supervisor 决定路由到哪个子 agent，子 agent 执行后回到 supervisor，直到 FINISH。

子 agent 均为独立 ``create_react_agent``，各自带工具与提示词；工具调用结果被提取为动态卡片。
"""
from __future__ import annotations

import re
from typing import Any, Literal

from langgraph.graph import END, START, StateGraph
from langgraph.prebuilt import create_react_agent
from pydantic import BaseModel

from agent3.checkpoint import get_checkpointer
from agent3.llm import get_llm
from agent3.tools import (
    cancel_order,
    create_order,
    get_cinema,
    get_current_user,
    get_movie,
    get_order,
    get_seat_map,
    get_show,
    list_shows,
    lock_seats,
    recommend_movies,
    recommend_seats,
    search_cinemas,
    search_movies,
    unlock_seats,
)
from agent3.cards import build_cards
from agent3.draft_tools import clear_booking_draft, get_booking_draft, load_draft, update_booking_draft
from agent3.state import Agent3State

MAX_STEPS = 6

# ---------- 子 agent 配置：各自的工具子集 + 提示词 ----------

_BOOKING_TOOLS = [get_booking_draft, update_booking_draft, clear_booking_draft]

SUBAGENT_CONFIGS: dict[str, dict[str, Any]] = {
    "chat": {
        "tools": [get_current_user, search_movies, search_cinemas, list_shows, *_BOOKING_TOOLS],
        "prompt": (
            "你是电影购票助手的「对话」子agent。负责闲聊、普通问答、信息性追问。"
            "需要真实数据时用工具查询，整理成清晰回复，不要输出原始 JSON。"
        ),
    },
    "movie": {
        "tools": [search_movies, get_movie, recommend_movies, *_BOOKING_TOOLS],
        "prompt": (
            "你是「电影」子agent，负责搜索/推荐电影、查看详情、选片。"
            "用户要选片/看喜剧/看某类型时，用 search_movies 搜索（注意 search_movies 只返回有排片可购票的影片）。"
            "用户明确选定某部后，用 update_booking_draft 记录 movieId 和 filmTitle。"
            "不要重复搜索已选影片，不要输出原始 JSON。"
        ),
    },
    "cinema": {
        "tools": [search_cinemas, get_cinema, *_BOOKING_TOOLS],
        "prompt": (
            "你是「影院」子agent，负责按用户位置/影片查附近影院、选影院。"
            "用 searchCinemas 查询（未传经纬度会自动用用户位置）。"
            "用户选定后，用 update_booking_draft 记录 cinemaId 和 cinemaName。不要输出原始 JSON。"
        ),
    },
    "show": {
        "tools": [list_shows, get_show, *_BOOKING_TOOLS],
        "prompt": (
            "你是「场次」子agent，负责查某影院某影片的场次。"
            "用 list_shows(cinemaId, movieId, date) 查询，date 用 YYYY-MM-DD（用户说今天/明天时换算）。"
            "用户选定场次后，用 update_booking_draft 记录 showId 和 date。不要输出原始 JSON。"
        ),
    },
    "seat": {
        "tools": [get_seat_map, recommend_seats, lock_seats, unlock_seats, *_BOOKING_TOOLS],
        "prompt": (
            "你是「座位」子agent，负责查座位图、推荐座位、锁座。"
            "用 getSeatMap 查座位图，recommendSeats 推荐。用户确认座位后 lockSeats 锁座并 update_booking_draft 记录 seatIds。"
            "不要输出原始 JSON。"
        ),
    },
    "order": {
        "tools": [create_order, get_order, cancel_order, *_BOOKING_TOOLS],
        "prompt": (
            "你是「订单」子agent，负责创建订单、查订单、取消订单。"
            "用户确认下单后 createOrder，成功后 clear_booking_draft 清空草稿。不要输出原始 JSON。"
        ),
    },
    "helper": {
        "tools": [get_current_user, *_BOOKING_TOOLS],
        "prompt": (
            "你是「助手」子agent，负责查当前登录用户、查看/管理购票草稿。"
            "不要输出原始 JSON。"
        ),
    },
}


# ---------- 监督者 ----------

_SUBAGENT_KEYS = set(SUBAGENT_CONFIGS.keys())
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


async def _decide(message: str, draft: dict[str, Any], last_reply: str) -> str:
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
        return nxt if nxt in _SUBAGENT_KEYS or nxt == "FINISH" else "FINISH"
    except Exception:
        return _rule_route(message, draft, last_reply)


async def supervisor_node(state: Agent3State) -> dict[str, Any]:
    nxt = await _decide(state.get("message") or "", state.get("bookingdraft") or {}, state.get("last_reply") or "")
    return {"next": nxt}


# ---------- 子 agent 节点 ----------


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


async def subagent_node(state: Agent3State, agent: Any, key: str) -> dict[str, Any]:
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

    result = await agent.ainvoke({"messages": msgs})
    messages = result.get("messages", []) if isinstance(result, dict) else []
    reply = _extract_ai_reply(messages) or ""
    tool_calls = _extract_tool_calls(messages)
    cards = build_cards(tool_calls, draft)

    new_draft = draft
    if sid:
        try:
            new_draft = await load_draft(sid) or draft
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


# ---------- 图构建 ----------

_compiled = None


def _make_subagent_node(agent: Any, key: str):
    """为每个子 agent 生成 async 节点闭包。

    LangGraph 只对 coroutine function（async def）节点自动 await；
    用 lambda 包装 async 函数会返回未 await 的 coroutine 对象，触发
    ``InvalidUpdateError: Expected dict, got <coroutine object>``。
    """
    async def _node(state: Agent3State) -> dict[str, Any]:
        return await subagent_node(state, agent, key)
    return _node


def reset_agent3_graph_cache() -> None:
    """lifespan 启停时清空编译缓存，避免挂上已关闭的 checkpointer。"""
    global _compiled
    _compiled = None


def get_agent3() -> Any:
    """构建并缓存监督者图；挂上 PostgresSaver 短期记忆（checkpointer，thread_id=sessionId）。"""
    global _compiled
    if _compiled is not None:
        return _compiled

    llm = get_llm()
    sub_agents: dict[str, Any] = {}
    for key, cfg in SUBAGENT_CONFIGS.items():
        sub_agents[key] = create_react_agent(model=llm, tools=cfg["tools"], prompt=cfg["prompt"])

    g = StateGraph(Agent3State)
    g.add_node("supervisor", supervisor_node)
    for key, sub in sub_agents.items():
        g.add_node(key, _make_subagent_node(sub, key))

    def _route(state: Agent3State) -> str:
        if (state.get("steps") or 0) >= MAX_STEPS:
            return "FINISH"
        nxt = state.get("next") or "FINISH"
        return nxt if nxt in _SUBAGENT_KEYS else "FINISH"

    g.add_edge(START, "supervisor")
    g.add_conditional_edges(
        "supervisor",
        _route,
        {**{k: k for k in SUBAGENT_CONFIGS}, "FINISH": END},
    )
    for k in SUBAGENT_CONFIGS:
        g.add_edge(k, "supervisor")

    _compiled = g.compile(checkpointer=get_checkpointer())
    return _compiled
