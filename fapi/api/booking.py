"""购票 Agent API：通过 booking_graph 多轮对话补全 BookingDraft。"""
from __future__ import annotations

import uuid
from typing import Any

from fastapi import APIRouter, Depends, Query

from agent.langgraph.booking_graph import build_booking_graph
from agent.langgraph.checkpoint import get_checkpointer
from agent.request_context import use_authorization
from fapi.api._draft_merge import resolve_draft_merge
from fapi.api.agent import (
    HistoryEnvelope,
    HistoryMessage,
    SessionCreateEnvelope,
    SessionCreateRequest,
    SessionListEnvelope,
    SessionMeta,
    _ensure_session_table,
    _extract_user_id,
    _generate_session_id,
    _list_sessions,
    _record_session,
)
from fapi.deps import get_authorization
from fapi.models.booking import (
    BookingChatEnvelope,
    BookingChatRequest,
    BookingChatResponse,
    BookingDraftVO,
)

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


def _to_draft_vo(draft: dict[str, Any] | None, sid: str = "") -> BookingDraftVO:
    if not draft:
        return BookingDraftVO(sessionId=sid)
    return BookingDraftVO(
        sessionId=sid,
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
        version=int(draft.get("version") or 0),
    )


@router.post("/turns", response_model=BookingChatEnvelope)
@router.post("/turn", response_model=BookingChatEnvelope, include_in_schema=False)
async def booking_turn(
    body: BookingChatRequest,
    authorization: str | None = Depends(get_authorization),
) -> BookingChatEnvelope:
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

    # 手动页面/中台草稿同步：乐观锁合并 clientDraft + clientDraftVersion（对齐 agent2/agent3）
    from agent.langgraph.booking_graph import _load_draft, _save_draft
    try:
        existing = await _load_draft(sid) or {}
        merged, _new_v = resolve_draft_merge(existing, body.clientDraft, body.clientDraftVersion)
        await _save_draft(sid, merged)
    except Exception:
        pass  # 草稿库未启用则忽略

    # 卡片点击：先把 draftPatch 写入草稿，再合成自然语言消息（对齐 agent2）
    message = body.message or ""
    if body.cardAction:
        ca = body.cardAction
        patch = ca.draftPatch or {}
        if patch:
            try:
                existing = await _load_draft(sid) or {}
                merged = {**existing, **patch}
                await _save_draft(sid, merged)
            except Exception:
                pass  # 草稿库未启用则忽略
        # 合成消息
        if patch.get("filmTitle"):
            message = f"我选择了电影《{patch['filmTitle']}》，请继续帮我完成购票。"
        elif patch.get("cinemaName"):
            message = f"我选择了影院 {patch['cinemaName']}，请继续。"
        elif patch.get("seatIds"):
            seats = ", ".join(str(s) for s in patch["seatIds"])
            message = f"我选择了座位 {seats}，请帮我锁座并确认下单。"
        elif patch.get("showId"):
            message = f"我选择了场次，请帮我查询座位并选座。"
        elif patch:
            kv = ", ".join(f"{k}={v}" for k, v in patch.items())
            message = f"（点卡操作：{kv}）"
        else:
            message = f"（点卡操作 {ca.actionId}）"

    with use_authorization(authorization):
        # 读取中台草稿（已合并 clientDraft/点卡），作为图初始状态，
        # 修复"手动页面选的字段不进入 agent 对话状态"的问题
        draft_for_graph: dict[str, Any] = {}
        try:
            draft_for_graph = await _load_draft(sid) or {}
        except Exception:
            pass
        payload: dict[str, Any] = {
            "message": message,
            "authorization": authorization,
            "latitude": body.latitude,
            "longitude": body.longitude,
            "sessionId": sid,
            "bookingdraft": draft_for_graph,
        }
        result = await graph.ainvoke(payload, config)

    draft = result.get("bookingdraft") or {}
    reply = result.get("reply", "")
    cards = result.get("cards") or []
    # 记录/更新 session 元数据（对齐 /agent/turns，让前端会话列表随对话刷新排序）
    user_id = _extract_user_id(authorization)
    title = message[:50] if message else None
    await _record_session(sid, user_id, title)
    data = BookingChatResponse(
        intent=result.get("intent", "chitchat"),
        reply=reply,
        replyText=reply,
        draft=_to_draft_vo(draft, sid),
        draft_complete=result.get("draft_complete", False),
        missing_fields=result.get("missing_fields") or [],
        cards=cards,
        needLogin=(authorization is None),
        events=result.get("events") or [],
        session_id=sid,
    )
    return BookingChatEnvelope(data=data)


@router.get("/draft/{session_id}", response_model=BookingDraftVO)
async def get_draft(
    session_id: str,
    authorization: str | None = Depends(get_authorization),
) -> BookingDraftVO:
    """查看当前会话的 BookingDraft 快照（从 checkpoint 读取）。

    仅返回当前用户拥有的会话；防止 IDOR 越权读取他人草稿。
    """
    user_id = _extract_user_id(authorization)
    rows = await _list_sessions(user_id)
    owned_ids = {r["session_id"] for r in rows}
    if session_id not in owned_ids:
        return BookingDraftVO(sessionId=session_id)

    graph = _graph()
    config = {"configurable": {"thread_id": session_id}}
    snap = await graph.aget_state(config)
    draft = (snap.values or {}).get("bookingdraft") if snap else None
    return _to_draft_vo(draft, session_id)


@router.delete("/draft/{session_id}")
async def reset_draft(
    session_id: str,
    authorization: str | None = Depends(get_authorization),
) -> dict:
    """重置会话的 BookingDraft（清空所有购票信息，重新开始）。

    仅允许重置当前用户拥有的会话；防止 IDOR 越权清空他人草稿。
    """
    user_id = _extract_user_id(authorization)
    rows = await _list_sessions(user_id)
    owned_ids = {r["session_id"] for r in rows}
    if session_id not in owned_ids:
        return {"status": "error", "message": "无权访问该会话"}

    graph = _graph()
    config = {"configurable": {"thread_id": session_id}}
    await graph.aupdate_state(config, {"bookingdraft": {}}, as_node="modify")
    return {"status": "ok", "message": "草稿已重置"}


# ---------- 会话管理（对齐 agent2 的 /agent/sessions 契约，供前端切换后使用） ----------


@router.get("/sessions", response_model=SessionListEnvelope)
async def list_sessions(
    authorization: str | None = Depends(get_authorization),
) -> SessionListEnvelope:
    """获取当前用户的会话列表（按最后消息时间降序）。"""
    user_id = _extract_user_id(authorization)
    rows = await _list_sessions(user_id)
    return SessionListEnvelope(
        data=[
            SessionMeta(
                sessionId=r["session_id"],
                userId=r["user_id"],
                title=r.get("title"),
                createdAt=str(r["created_at"]) if r.get("created_at") else None,
                lastMessageAt=str(r["last_message_at"]) if r.get("last_message_at") else None,
            )
            for r in rows
        ]
    )


@router.post("/sessions", response_model=SessionCreateEnvelope)
async def create_session(
    body: SessionCreateRequest | None = None,
    authorization: str | None = Depends(get_authorization),
) -> SessionCreateEnvelope:
    """新建会话，返回 session_id。"""
    user_id = _extract_user_id(authorization)
    sid = _generate_session_id(user_id)
    title = (body.title if body else None) or "新对话"
    await _record_session(sid, user_id, title)
    import datetime as _dt
    now = _dt.datetime.now().isoformat()
    return SessionCreateEnvelope(
        data=SessionMeta(
            sessionId=sid,
            userId=user_id,
            title=title,
            createdAt=now,
            lastMessageAt=now,
        )
    )


@router.get("/sessions/{session_id}/messages", response_model=HistoryEnvelope)
async def get_session_messages(
    session_id: str,
    limit: int = Query(default=5, ge=1, le=50),
    offset: int = Query(default=0, ge=0),
    authorization: str | None = Depends(get_authorization),
) -> HistoryEnvelope:
    """获取某会话的历史消息（从 booking_graph checkpoint 读取）。

    仅返回当前用户拥有的会话；session_id 归属校验防止 IDOR 越权读取他人对话。
    """
    user_id = _extract_user_id(authorization)
    rows = await _list_sessions(user_id)
    owned_ids = {r["session_id"] for r in rows}
    # 若会话不在该用户会话表中，拒绝访问（防止越权读取他人历史）
    if session_id not in owned_ids:
        return HistoryEnvelope(data=[])

    graph = _graph()
    config = {"configurable": {"thread_id": session_id}}
    try:
        snap = await graph.aget_state(config)
        history = (snap.values or {}).get("history") or []
    except Exception:
        history = []
    if offset:
        page = history[offset:offset + limit]
    else:
        page = history[-limit:]
    return HistoryEnvelope(data=[HistoryMessage(role=m["role"], content=m["content"]) for m in page if isinstance(m, dict)])
