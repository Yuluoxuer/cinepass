"""Agent HTTP 接口 — 前端系分 §7：POST /api/v1/agent/turns。"""
from __future__ import annotations

from fastapi import APIRouter, Depends

from app.api.deps import RequestContext, get_request_context
from app.langchain.turn.process_turn import process_agent_turn
from app.models.response import ok
from app.models.turn import AgentTurnRequest

router = APIRouter(prefix="/agent", tags=["agent"])


@router.post("/turns")
async def agent_turns(
    body: AgentTurnRequest,
    ctx: RequestContext = Depends(get_request_context),
) -> dict:
    """一轮对话 Turn：NLP → Planner/LangGraph → Tools → CardComposer。"""
    result = await process_agent_turn(body, ctx)
    return ok(result.model_dump(by_alias=True, exclude_none=True))
