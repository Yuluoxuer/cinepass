"""主工作流：router → chat | helper SubAgent。"""
from __future__ import annotations

import re
from typing import Any, Literal

from langgraph.graph import END, START, StateGraph

from agent.langgraph.state import GraphState
from agent.subagent import get_subagents

Route = Literal["chat", "helper"]


def _route_message(message: str) -> Route:
    """确定性路由：工具意图 → helper，否则 → chat。"""
    if re.search(r"时间|几点|echo\s|回显|now|time|jwt|token|鉴权|authorization", message, re.I):
        return "helper"
    return "chat"


async def router_node(state: GraphState) -> dict[str, Any]:
    route = _route_message(state.get("message") or "")
    return {"route": route, "events": [f"routed:{route}"]}


async def chat_node(state: GraphState) -> dict[str, Any]:
    agent = get_subagents()["chat"]
    reply = await agent.run(state.get("message") or "", history=state.get("history"))
    return {"reply": reply, "events": ["chat_done"]}


async def helper_node(state: GraphState) -> dict[str, Any]:
    agent = get_subagents()["helper"]
    reply = await agent.run(state.get("message") or "", history=state.get("history"))
    return {"reply": reply, "events": ["helper_done"]}


def _select_route(state: GraphState) -> Route:
    route = state.get("route") or "chat"
    return "helper" if route == "helper" else "chat"


def build_graph() -> Any:
    """编译 StateGraph：START → router → (chat|helper) → END。"""
    graph = StateGraph(GraphState)
    graph.add_node("router", router_node)
    graph.add_node("chat", chat_node)
    graph.add_node("helper", helper_node)
    graph.add_edge(START, "router")
    graph.add_conditional_edges(
        "router",
        _select_route,
        {"chat": "chat", "helper": "helper"},
    )
    graph.add_edge("chat", END)
    graph.add_edge("helper", END)
    return graph.compile()
