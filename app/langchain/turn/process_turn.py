"""ProcessAgentTurn（系分 §5.1）— hydrate Draft(中台) → Graph → messages(Agent 库)。

调用方：api/v1/agent.py。覆盖已有 process_turn.py。
用户指令：去掉 X-Internal-Api-Key；Agent 自有库 SQLAlchemy。
"""
from __future__ import annotations

import uuid
from typing import Any

from app.api.deps import RequestContext
from app.clients.ticket_api import TicketApiClient
from app.db import SessionLocal
from app.db import message_repo
from app.langchain.graph.booking_graph import run_booking_graph
from app.langchain.graph.state import BookingGraphState
from app.models.turn import (
    AgentCardVO,
    AgentTurnRequest,
    AgentTurnResponse,
    ProgressVO,
    ToolTraceVO,
)


def _empty_draft(session_id: str) -> dict[str, Any]:
    return {
        "sessionId": session_id,
        "userId": None,
        "source": "agent",
        "state": "Idle",
        "intent": "buy_ticket",
        "seatIds": [],
        "version": 0,
    }


async def process_agent_turn(
    req: AgentTurnRequest,
    ctx: RequestContext,
) -> AgentTurnResponse:
    session_id = req.session_id or f"sess_{uuid.uuid4().hex[:12]}"
    api = TicketApiClient(authorization=ctx.authorization)

    draft: dict[str, Any] = _empty_draft(session_id)
    messages: list[dict[str, Any]] = []

    # Draft 仍经中台 hydrate
    try:
        draft_resp = await api.get_draft(session_id)
        data = draft_resp.get("data") if isinstance(draft_resp, dict) else draft_resp
        if isinstance(data, dict) and data:
            draft = data
    except Exception:
        pass

    # 对话记忆：Agent 自有库（SQLAlchemy）
    db = SessionLocal()
    try:
        messages = message_repo.list_messages(db, session_id, limit=20)
        db.commit()
    except Exception:
        db.rollback()
        messages = []
    finally:
        db.close()

    card_action = None
    if req.card_action is not None:
        card_action = req.card_action.model_dump(by_alias=True, exclude_none=True)

    state: BookingGraphState = {
        "session_id": session_id,
        "message": req.message,
        "card_action": card_action,
        "client_draft_version": req.client_draft_version,
        "authorization": ctx.authorization,
        "debug": req.debug,
        "draft": draft,
        "messages": messages,
        "events": [],
    }
    state = await run_booking_graph(state)

    composed_draft = state.get("draft") or draft
    if state.get("plan") and state["plan"].get("merged_draft"):
        composed_draft = {
            **state["plan"]["merged_draft"],
            "state": state["plan"].get("target_state", composed_draft.get("state")),
            "sessionId": session_id,
        }

    # Draft 写回中台
    try:
        version = req.client_draft_version
        if version is None:
            version = composed_draft.get("version", 0)
        await api.put_draft(
            session_id,
            {"version": version, "patch": composed_draft},
        )
    except Exception:
        pass

    # Messages 写入 Agent 自有库
    db = SessionLocal()
    try:
        message_repo.append_messages(
            db,
            session_id,
            [
                {"role": "user", "content": req.message or "[cardAction]"},
                {
                    "role": "assistant",
                    "content": state.get("reply_text") or "",
                    "cards": state.get("cards") or [],
                },
            ],
        )
        db.commit()
    except Exception:
        db.rollback()
    finally:
        db.close()

    progress_raw = state.get("progress") or {}
    progress = ProgressVO(
        steps=progress_raw.get("steps") or ["选片", "影院", "场次", "选座", "支付"],
        currentIndex=progress_raw.get("currentIndex", 0),
        state=progress_raw.get("state", "Idle"),
    )

    cards = [
        AgentCardVO.model_validate(c) if not isinstance(c, AgentCardVO) else c
        for c in (state.get("cards") or [])
    ]

    traces = None
    if req.debug:
        traces = [
            ToolTraceVO.model_validate(t) if isinstance(t, dict) else t
            for t in (state.get("tool_traces") or [])
        ]

    return AgentTurnResponse(
        sessionId=session_id,
        replyText=state.get("reply_text") or "",
        draft=composed_draft,
        cards=cards,
        progress=progress,
        needLogin=bool(state.get("need_login")),
        events=list(state.get("events") or []),
        toolTraces=traces,
    )
