"""购票 Agent API：通过 booking_graph 多轮对话补全 BookingDraft。"""
from __future__ import annotations

import uuid
from typing import Any

from fastapi import APIRouter, Depends

from agent.langgraph.booking_graph import build_booking_graph
from agent.langgraph.checkpoint import get_checkpointer
from agent.request_context import use_authorization
from fapi.deps import get_authorization
from fapi.models.booking import BookingChatRequest, BookingChatResponse, BookingDraftVO

router = APIRouter(prefix="/booking", tags=["booking"])

# 编译缓存（与 runner.py 类似，lifespan 重置）
_compiled = None
_compiled_with_cp = None


def reset_booking_graph_cache() -> None:
    """lifespan 启停时清空编译缓存，避免挂上已关闭的 checkpointer。"""
    global _compiled, _compiled_with_cp
    _compiled = None
    _compiled_with_cp = None


def _graph():
    global _compiled, _compiled_with_cp
    cp = get_checkpointer()
    if cp is None:
        if _compiled is None:
            _compiled = build_booking_graph(checkpointer=None)
        return _compiled
    if _compiled_with_cp is None:
        _compiled_with_cp = build_booking_graph(checkpointer=cp)
    return _compiled_with_cp


def _to_draft_vo(draft: dict[str, Any] | None) -> BookingDraftVO:
    if not draft:
        return BookingDraftVO()
    return BookingDraftVO(
        movieId=draft.get("movieId"),
        filmTitle=draft.get("filmTitle"),
        cinemaId=draft.get("cinemaId"),
        cinemaName=draft.get("cinemaName"),
        showId=draft.get("showId"),
        date=draft.get("date"),
        timeWindow=draft.get("timeWindow"),
        count=draft.get("count"),
        seatIds=draft.get("seatIds"),
        preferRow=draft.get("preferRow"),
        preferSide=draft.get("preferSide"),
        together=draft.get("together"),
        lockId=draft.get("lockId"),
        orderId=draft.get("orderId"),
        expireAt=draft.get("expireAt"),
    )


@router.post("/turn", response_model=BookingChatResponse)
async def booking_turn(
    body: BookingChatRequest,
    authorization: str | None = Depends(get_authorization),
) -> BookingChatResponse:
    """购票对话一轮。

    每次调用执行 booking_graph 一轮（intent → main_agent/modify/order → END），
    bookingdraft 由 checkpointer 跨轮持久化。

    **多轮测试方法**：每轮带上同一个 `session_id`，图会自动加载上一轮的草稿。

    示例流程：
    - 第1轮: `{"message": "想看哪吒2", "session_id": "test001"}` → 提取影片，追问影院
    - 第2轮: `{"message": "湘潭万达", "session_id": "test001"}` → 提取影院，追问场次
    - 第3轮: `{"message": "明天下午", "session_id": "test001"}` → 提取日期+时间段，自动查场次
    - 第4轮: `{"message": "两张", "session_id": "test001"}` → 填票数，追问座位
    - 第5轮: `{"message": "A5 A6", "session_id": "test001"}` → 填座位，草稿完整，确认
    - 第6轮: `{"message": "确认", "session_id": "test001"}` → 下单
    """
    graph = _graph()
    sid = (body.session_id or "").strip() or str(uuid.uuid4())
    config = {"configurable": {"thread_id": sid}}

    with use_authorization(authorization):
        payload: dict[str, Any] = {
            "message": body.message,
            "authorization": authorization,
            "latitude": body.latitude,
            "longitude": body.longitude,
            "sessionId": sid,
        }
        result = await graph.ainvoke(payload, config)

    draft = result.get("bookingdraft") or {}
    return BookingChatResponse(
        intent=result.get("intent", "chitchat"),
        reply=result.get("reply", ""),
        draft=_to_draft_vo(draft),
        draft_complete=result.get("draft_complete", False),
        missing_fields=result.get("missing_fields") or [],
        events=result.get("events") or [],
        session_id=sid,
    )


@router.get("/draft/{session_id}", response_model=BookingDraftVO)
async def get_draft(session_id: str) -> BookingDraftVO:
    """查看当前会话的 BookingDraft 快照（从 checkpoint 读取）。"""
    graph = _graph()
    config = {"configurable": {"thread_id": session_id}}
    snap = await graph.aget_state(config)
    draft = (snap.values or {}).get("bookingdraft") if snap else None
    return _to_draft_vo(draft)


@router.delete("/draft/{session_id}")
async def reset_draft(session_id: str) -> dict:
    """重置会话的 BookingDraft（清空所有购票信息，重新开始）。"""
    graph = _graph()
    config = {"configurable": {"thread_id": session_id}}
    await graph.aupdate_state(config, {"bookingdraft": {}}, as_node="modify")
    return {"status": "ok", "message": "草稿已重置"}
