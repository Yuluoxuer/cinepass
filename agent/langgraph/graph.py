"""主工作流：router → chat | helper | movie | show SubAgent。"""
from __future__ import annotations

import re
from typing import Any, Literal

from langgraph.graph import END, START, StateGraph

from agent.langgraph.state import GraphState
from agent.subagent import get_subagents

Route = Literal["chat", "helper", "movie", "show"]


def _route_message(message: str) -> Route:
    """确定性路由：电影→movie，场次→show，工具→helper，否则→chat。"""
    if re.search(r"电影|movie|影片|上映|想看|热映|待映|类型|评分|推荐", message, re.I):
        return "movie"
    if re.search(r"场次|show|排片|场|几点|多少点|下午|晚上|明天|今天", message, re.I):
        return "show"
    if re.search(r"echo\s|回显|now|time|jwt|token|鉴权|authorization", message, re.I):
        return "helper"
    return "chat"


async def _agent_node(state: GraphState, name: str) -> dict[str, Any]:
    agent = get_subagents().get(name)
    if agent is None:
        agent = get_subagents()["chat"]
    reply = await agent.run(state.get("message") or "", history=state.get("history"))
    return {"reply": reply, "events": [f"{name}_done"]}


async def router_node(state: GraphState) -> dict[str, Any]:
    route = _route_message(state.get("message") or "")
    return {"route": route, "events": [f"routed:{route}"]}


async def chat_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "chat")


async def helper_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "helper")


async def movie_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "movie")


async def show_node(state: GraphState) -> dict[str, Any]:
    return await _agent_node(state, "show")


def _select_route(state: GraphState) -> str:
    route = state.get("route") or "chat"
    valid = {"chat", "helper", "movie", "show"}
    return route if route in valid else "chat"


def build_graph() -> Any:
    """编译 StateGraph：START → router → (chat|helper|movie|show) → END。"""
    graph = StateGraph(GraphState)
    graph.add_node("router", router_node)
    graph.add_node("chat", chat_node)
    graph.add_node("helper", helper_node)
    graph.add_node("movie", movie_node)
    graph.add_node("show", show_node)
    graph.add_edge(START, "router")
    graph.add_conditional_edges(
        "router",
        _select_route,
        {"chat": "chat", "helper": "helper", "movie": "movie", "show": "show"},
    )
    for route in ("chat", "helper", "movie", "show"):
        graph.add_edge(route, END)
    return graph.compile()
