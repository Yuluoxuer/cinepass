"""购票主图骨架 — 节点对应系分 §5.1 流水线；后续用 langgraph StateGraph 接线。"""
from __future__ import annotations

from typing import Any

from app.langchain.composer.card_composer import compose_cards
from app.langchain.graph.state import BookingGraphState
from app.langchain.nlp.langchain_nlp import extract_intent_slots
from app.langchain.planner.planner import plan_next_actions
from app.langchain.rag.retriever import retrieve_faq


async def run_booking_graph(state: BookingGraphState) -> BookingGraphState:
    """MVP：顺序执行节点；正式实现改为 StateGraph.add_node / add_edge。"""
    events: list[str] = list(state.get("events") or [])

    # 1) 点卡优先于 NLP
    card_action = state.get("card_action")
    if card_action:
        events.append("card_action_applied")
        state["slot_patch"] = dict(card_action.get("draftPatch") or {})
        state["intent"] = "buy_ticket"
    elif state.get("message"):
        nlp = await extract_intent_slots(state["message"] or "")
        state["intent"] = nlp.get("intent", "chitchat")
        state["slot_patch"] = nlp.get("slots", {})
        events.append("intent_parsed")

    # 2) RAG（FAQ / 政策，P1；事实仍走 Tools）
    if state.get("message"):
        state["rag_hits"] = await retrieve_faq(state["message"] or "")
        if state["rag_hits"]:
            events.append("rag_hit")

    # 3) 确定性 Planner
    plan = plan_next_actions(
        draft=state.get("draft") or {},
        intent=state.get("intent", "buy_ticket"),
        slot_patch=state.get("slot_patch") or {},
        card_action=card_action,
    )
    state["plan"] = plan
    events.append("planned")

    # 4) 子 Agent Tools 由 plan 驱动（骨架：不真正调中台）
    state["tool_results"] = []
    state["tool_traces"] = []

    # 5) 卡片与话术
    composed = compose_cards(
        draft=state.get("draft") or {},
        plan=plan,
        tool_results=state.get("tool_results") or [],
        rag_hits=state.get("rag_hits") or [],
    )
    state["reply_text"] = composed["reply_text"]
    state["cards"] = composed["cards"]
    state["progress"] = composed["progress"]
    state["need_login"] = composed.get("need_login", False)
    if composed.get("draft"):
        state["draft"] = composed["draft"]
    events.extend(composed.get("events") or [])
    state["events"] = events
    return state


def build_graph() -> Any:
    """预留：返回编译后的 LangGraph；当前用 run_booking_graph 同步编排。"""
    return None
