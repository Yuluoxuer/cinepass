"""主工作流：router → chat | helper SubAgent。"""
from __future__ import annotations

import re
from typing import Any, Literal

from langgraph.graph import END, START, StateGraph

from agent.langgraph.checkpoint import get_checkpointer
from agent.langgraph.state import GraphState
from agent.subagent import get_subagents

Route = Literal["chat", "cinema", "helper"]


def _route_message(message: str) -> Route:
    """确定性路由：影院查询优先，其次是通用工具，否则普通聊天。"""
    if re.search(r"影院|影城|电影院|附近|cinema(?:id)?", message, re.I):
        return "cinema"
    if re.search(
        r"时间|几点|echo\s|回显|now|time|jwt|token|鉴权|authorization|我是谁|当前用户|用户信息|whoami",
        message,
        re.I,
    ):
        return "helper"
    return "chat"


async def router_node(state: GraphState) -> dict[str, Any]:
    route = _route_message(state.get("message") or "")
    return {"route": route, "events": [f"routed:{route}"]}


async def chat_node(state: GraphState) -> dict[str, Any]:
    agent = get_subagents()["chat"]
    message = state.get("message") or ""
    reply = await agent.run(message, history=state.get("history"))
    return {
        "reply": reply,
        "history": [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ],
        "events": ["chat_done"],
    }


async def helper_node(state: GraphState) -> dict[str, Any]:
    agent = get_subagents()["helper"]
    message = state.get("message") or ""
    reply = await agent.run(message, history=state.get("history"))
    return {
        "reply": reply,
        "history": [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ],
        "events": ["helper_done"],
    }


async def cinema_node(state: GraphState) -> dict[str, Any]:
    """调用只读影院查询子 Agent，并保留本轮上下文中的定位坐标。"""
    agent = get_subagents()["cinema"]
    message = state.get("message") or ""
    reply = await agent.run(
        message,
        history=state.get("history"),
        latitude=state.get("latitude"),
        longitude=state.get("longitude"),
    )
    return {
        "reply": reply,
        "history": [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ],
        "events": ["cinema_done"],
    }


def _select_route(state: GraphState) -> Route:
    route = state.get("route") or "chat"
    if route == "cinema":
        return "cinema"
    return "helper" if route == "helper" else "chat"


def build_graph(checkpointer: Any | None = None) -> Any:
    """编译 StateGraph：START → router → (chat|helper) → END。

    ``checkpointer`` 为官方 PostgresSaver 时，按 ``thread_id`` 持久化短期记忆。
    未传入时尝试 ``get_checkpointer()``；仍为 ``None`` 则无状态编译。
    """
    graph = StateGraph(GraphState)
    graph.add_node("router", router_node)
    graph.add_node("chat", chat_node)
    graph.add_node("cinema", cinema_node)
    graph.add_node("helper", helper_node)
    graph.add_edge(START, "router")
    graph.add_conditional_edges(
        "router",
        _select_route,
        {"chat": "chat", "cinema": "cinema", "helper": "helper"},
    )
    graph.add_edge("chat", END)
    graph.add_edge("cinema", END)
    graph.add_edge("helper", END)
    cp = checkpointer if checkpointer is not None else get_checkpointer()
    return graph.compile(checkpointer=cp)
