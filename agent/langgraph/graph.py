"""主工作流：LLM 意图路由 → chat | helper | movie | show | cinema | seat | order SubAgent。"""
from __future__ import annotations

import re
from typing import Any, Literal

from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel

from agent.langgraph.checkpoint import get_checkpointer
from agent.langgraph.state import GraphState
from agent.llm import get_chat_model
from agent.subagent import get_subagents

Route = Literal["chat", "helper", "movie", "show", "cinema", "seat", "order"]

_ROUTER_PROMPT = """你是购票助手的意图分类器。根据用户消息判断路由：
- movie: 查电影/影片信息、评分、推荐、上映情况
- show: 查场次/排片/放映时间/几点有场
- cinema: 查影院/影城/附近电影院
- seat: 选座/座位图/推荐座位/锁座/解锁
- order: 创建订单/查订单/取消订单
- helper: echo/当前时间/jwt/token/鉴权/whoami 等工具调用
- chat: 闲聊、问候、其他

只返回 RouteDecision，不要多余解释。"""


class RouteDecision(BaseModel):
    """LLM 意图分类的结构化输出。"""
    route: Route
    reason: str = ""


def _regex_fallback(message: str) -> Route:
    """无 LLM 或 LLM 异常时的确定性 regex 降级路由。"""
    if re.search(r"电影|movie|影片|上映|想看|热映|待映|类型|评分|推荐", message, re.I):
        return "movie"
    if re.search(r"场次|show|排片|几点|多少点|下午|晚上|明天|今天", message, re.I):
        return "show"
    if re.search(r"影院|影城|电影院|附近|cinema(?:id)?", message, re.I):
        return "cinema"
    if re.search(r"座位|选座|座位图|锁座|解锁|seat|推荐座", message, re.I):
        return "seat"
    if re.search(r"订单|下单|取消单|查单|order|取票码", message, re.I):
        return "order"
    if re.search(
        r"echo\s|回显|now|time|jwt|token|鉴权|authorization|我是谁|当前用户|whoami",
        message,
        re.I,
    ):
        return "helper"
    return "chat"


async def _route_message(
    message: str, history: list[dict[str, str]] | None = None
) -> Route:
    """LLM 结构化路由：无 API Key 或异常时降级为 regex。"""
    llm = get_chat_model()
    if llm is None:
        return _regex_fallback(message)
    try:
        structured = llm.with_structured_output(RouteDecision)
        # 拼接最近几轮历史作为上下文，帮助 LLM 理解省略语
        context = ""
        if history:
            recent = history[-4:]  # 最近 2 轮（user + assistant）
            context = "\n最近对话:\n" + "\n".join(
                f"{h['role']}: {h['content']}" for h in recent
            )
        decision = await structured.ainvoke(
            f"{_ROUTER_PROMPT}{context}\n用户消息: {message}"
        )
        return decision.route
    except Exception:
        return _regex_fallback(message)


async def _agent_node(
    state: GraphState, name: str, **extra: Any
) -> dict[str, Any]:
    """通用 SubAgent 调用：取 Agent → run → 返回 reply + history + events。

    ``**extra`` 透传给 ``SubAgent.run``（如 cinema 的 latitude/longitude）。
    """
    agent = get_subagents().get(name) or get_subagents()["chat"]
    message = state.get("message") or ""
    reply = await agent.run(message, history=state.get("history"), **extra)
    return {
        "reply": reply,
        "history": [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ],
        "events": [f"{name}_done"],
    }


async def router_node(state: GraphState) -> dict[str, Any]:
    route = await _route_message(
        state.get("message") or "", history=state.get("history")
    )
    return {"route": route, "events": [f"routed:{route}"]}


async def chat_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "chat")


async def helper_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "helper")


async def cinema_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(
        state, "cinema",
        latitude=state.get("latitude"),
        longitude=state.get("longitude"),
    )


async def movie_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "movie")


async def show_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "show")


async def seat_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "seat")


async def order_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "order")


def _select_route(state: GraphState) -> str:
    return state.get("route") or "chat"


def build_graph(checkpointer: Any | None = None) -> Any:
    """编译 StateGraph：START → router → (chat|helper|movie|show|cinema|seat|order) → END。

    ``checkpointer`` 为官方 PostgresSaver 时，按 ``thread_id`` 持久化短期记忆。
    未传入时尝试 ``get_checkpointer()``；仍为 ``None`` 则无状态编译。
    """
    graph = StateGraph(GraphState)
    graph.add_node("router", router_node)
    graph.add_node("chat", chat_node)
    graph.add_node("cinema", cinema_node)
    graph.add_node("helper", helper_node)
    graph.add_node("movie", movie_node)
    graph.add_node("show", show_node)
    graph.add_node("seat", seat_node)
    graph.add_node("order", order_node)
    graph.add_edge(START, "router")
    graph.add_conditional_edges(
        "router",
        _select_route,
        {
            "chat": "chat",
            "cinema": "cinema",
            "helper": "helper",
            "movie": "movie",
            "show": "show",
            "seat": "seat",
            "order": "order",
        },
    )
    graph.add_edge("chat", END)
    graph.add_edge("cinema", END)
    graph.add_edge("helper", END)
    graph.add_edge("movie", END)
    graph.add_edge("show", END)
    graph.add_edge("seat", END)
    graph.add_edge("order", END)
    cp = checkpointer if checkpointer is not None else get_checkpointer()
    return graph.compile(checkpointer=cp)
