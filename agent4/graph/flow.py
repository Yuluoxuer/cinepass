"""agent4 购票流程图：意图识别 → 提取草稿 → 追问补齐 → 确认 → 锁座支付。

流程拓扑：:

    START
      │  (route_start 按 stage 分派)
      ▼
  optimize ──▶ intent ──(chat)──▶ chat ──▶ END
                    │
                    └──(booking)──▶ extract ──(缺字段)──▶ collect ──▶ END（等用户补充，下轮回 extract）
                                      │
                                      └──(完整)──▶ confirm ──(未确认)──▶ END（等用户确认）
                                                     │
                                                     └──(已确认)──▶ pay ──▶ END

跨轮推进：每轮由 api 层注入 ``stage``（intent/collect/confirm/pay），
图只执行当前阶段链；追问/确认阶段以 END 结束等待用户下一轮输入。
"""
from __future__ import annotations

from typing import Any

from langgraph.graph import END, START, StateGraph

from agent4.graph.nodes import (
    chat_node,
    collect_node,
    confirm_node,
    extract_node,
    intent_node,
    optimize_node,
    pay_node,
)
from agent4.state import Agent4State


def route_start(state: Agent4State) -> str:
    """按当前阶段分派入口节点。"""
    stage = state.get("stage") or "intent"
    return {"collect": "extract", "confirm": "confirm", "pay": "pay"}.get(stage, "optimize")


def route_intent(state: Agent4State) -> str:
    """意图路由：聊天 → chat 节点；购票 → extract 节点。"""
    return "chat" if state.get("intent") == "chat" else "extract"


def route_extract(state: Agent4State) -> str:
    """草稿完整度路由：缺字段 → collect（追问）；完整 → confirm（确认）。
    若 extract_node 判定为闲聊（intent=chat），路由到 chat_node 检索知识库作答。"""
    if state.get("intent") == "chat":
        return "chat"
    return "collect" if (state.get("missing") or []) else "confirm"


def route_confirm(state: Agent4State) -> str:
    """确认路由：用户已确认 → pay；否则等待下一轮。"""
    return "pay" if state.get("confirmed") else "finish"


def build_booking_graph(max_steps: int = 10) -> StateGraph:
    """构建购票流程图（返回编译前的 StateGraph）。

    - ``max_steps``：保留参数，防止极端循环（当前由 API 层控制跨轮轮次）
    """
    g = StateGraph(Agent4State)
    g.add_node("optimize", optimize_node)
    g.add_node("intent", intent_node)
    g.add_node("chat", chat_node)
    g.add_node("extract", extract_node)
    g.add_node("collect", collect_node)
    g.add_node("confirm", confirm_node)
    g.add_node("pay", pay_node)

    # 入口分派
    g.add_conditional_edges(
        START,
        route_start,
        {"optimize": "optimize", "extract": "extract", "confirm": "confirm", "pay": "pay"},
    )
    # 意图识别链
    g.add_edge("optimize", "intent")
    g.add_conditional_edges("intent", route_intent, {"chat": "chat", "extract": "extract"})
    g.add_edge("chat", END)
    # 提取 → 追问 / 确认
    g.add_conditional_edges("extract", route_extract, {"collect": "collect", "confirm": "confirm", "chat": "chat"})
    g.add_edge("collect", END)
    # 确认 → 支付 / 等待
    g.add_conditional_edges("confirm", route_confirm, {"pay": "pay", "finish": END})
    g.add_edge("pay", END)
    return g


__all__ = ["build_booking_graph", "route_start", "route_intent", "route_extract", "route_confirm"]
