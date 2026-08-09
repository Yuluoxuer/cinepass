"""agent3 对话 API：监督者（Supervisor）模式，契约对齐 /agent/turns。

前端只需把 ``src/api/agent.ts`` 的 ``BASE`` 改成 ``/agent3`` 即可接入测试。
- 短期记忆：图状态经 ``agent3.checkpoint``（PostgresSaver）以 thread_id=sessionId 落库；
- 对话历史：user 输入 + assistant 回复存 ``agent_messages`` 表（``agent3.memory``）；
- 购票草稿：与手动页面共用中台 ``/booking-drafts/{sid}``。
"""
from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query

from agent3.request_context import use_authorization, use_location
from agent3.booking_draft import use_session_id
from agent3 import get_agent3
from agent3.draft_merge import resolve_draft_merge
from agent3.memory import append_turn, ensure_messages_table, list_messages, load_history
from agent3.contract import (
    AgentTurnEnvelope,
    AgentTurnRequest,
    AgentTurnResponse,
    HistoryEnvelope,
    HistoryMessage,
    SessionCreateEnvelope,
    SessionCreateRequest,
    SessionListEnvelope,
    SessionMeta,
    _compose_message,
    _extract_user_id,
    _generate_session_id,
    _list_sessions,
    _record_session,
    _to_draft_vo,
)
from agent3.deps import get_authorization
from agent3.draft_tools import load_draft as _load_middle, save_draft as _save_middle

router = APIRouter(prefix="/agent3", tags=["agent3"])


async def _session_owner(session_id: str) -> str | None:
    """查询会话归属用户；会话不存在或查询失败返回 None。"""
    try:
        from agent3.booking_draft import _get_pool
        pool = await _get_pool()
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT user_id FROM agent_sessions WHERE session_id = $1", session_id
            )
            return row["user_id"] if row else None
    except Exception:
        return None


async def _run_agent3(body: AgentTurnRequest, authorization: str | None, sid: str) -> dict[str, Any]:
    """跑一轮监督者图，返回 {reply, cards, draft, history, events}。"""
    message = _compose_message(body)

    # 历史：从 agent_messages 表读取最近对话（短期记忆窗口，供子 agent 上下文）
    history = []
    try:
        await ensure_messages_table()
        history = await load_history(sid)
    except Exception:
        history = []

    # 草稿：从中台读取（与手动页面共用同一份），再合并 clientDraft + 点卡（需 JWT 上下文）
    _DRAFT_KEYS = (
        "movieId", "filmTitle", "cinemaId", "cinemaName", "showId",
        "date", "timeWindow", "count", "seatIds",
        "preferRow", "preferSide", "together", "lockId", "orderId", "expireAt",
    )

    def _pick(src: dict[str, Any] | None) -> dict[str, Any]:
        return {k: v for k, v in (src or {}).items() if k in _DRAFT_KEYS and v not in (None, "", [])}

    draft: dict[str, Any] = {}
    graph = get_agent3()
    with use_authorization(authorization), use_session_id(sid), use_location(
        body.latitude, body.longitude
    ):
        # ① 页面草稿同步：乐观锁合并 clientDraft + clientDraftVersion（对齐 /agent、/booking）
        try:
            existing = await _load_middle(sid) or {}
            merged, _new_v = resolve_draft_merge(existing, body.clientDraft, body.clientDraftVersion)
            # ② 点卡操作：明确选择优先（覆盖）
            patch = body.cardAction.draftPatch if body.cardAction else None
            if patch:
                merged = {**merged, **_pick(patch)}
            await _save_middle(merged, sid)
            draft = merged
        except Exception:
            pass  # 中台草稿不可用时忽略

        # thread_id=sessionId：短期记忆经 PostgresSaver 落库；authorization 不入图状态（避免 JWT 落库）
        config = {"configurable": {"thread_id": sid}}
        result = await graph.ainvoke({
            "message": message,
            "history": history,
            "reply_parts": [],
            "cards": [],
            "bookingdraft": draft,
            "sessionId": sid,
            "latitude": body.latitude,
            "longitude": body.longitude,
            "steps": 0,
            "last_reply": "",
        }, config)

    reply_parts = result.get("reply_parts") or []
    reply = "\n".join(part for part in reply_parts if part).strip() or "抱歉，我暂时无法处理这个请求。"
    cards = result.get("cards") or []
    new_draft = result.get("bookingdraft") or draft
    new_history = result.get("history") or history

    # 回写：草稿 → 中台；本轮对话（user + assistant）→ agent_messages 表
    deduped: list[dict[str, str]] = []
    seen: set[str] = set()
    for h in new_history:
        key = f"{h.get('role')}|{h.get('content')}"
        if key in seen:
            continue
        seen.add(key)
        deduped.append(h)
    if sid:
        try:
            with use_authorization(authorization):
                await _save_middle(new_draft, sid)
        except Exception:
            pass
        try:
            await append_turn(sid, message, reply)
        except Exception:
            pass

    return {
        "reply": reply,
        "cards": cards,
        "draft": new_draft,
        "history": deduped,
        "events": result.get("steps", 0),
    }


@router.post("/turns", response_model=AgentTurnEnvelope)
async def agent3_turns(
    body: AgentTurnRequest,
    authorization: str | None = Depends(get_authorization),
) -> AgentTurnEnvelope:
    """监督者模式对话一轮。"""
    user_id = _extract_user_id(authorization)
    sid = (body.sessionId or "").strip() or _generate_session_id(user_id)
    message = _compose_message(body)

    # 会话归属校验（IDOR 防护）：已存在的会话必须属于当前用户
    if body.sessionId and body.sessionId.strip():
        owner = await _session_owner(sid)
        if owner is not None and owner != user_id:
            raise HTTPException(status_code=403, detail="无权访问该会话")

    if not message:
        return AgentTurnEnvelope(
            data=AgentTurnResponse(
                sessionId=sid,
                replyText="请告诉我您想看什么电影，或者有什么需要帮您处理的？",
                draft=_to_draft_vo(sid, {}),
            )
        )

    out = await _run_agent3(body, authorization, sid)
    await _record_session(sid, user_id, message[:50] if message else None)

    need_login = any(kw in out["reply"] for kw in ("登录", "JWT", "未携带", "鉴权"))
    return AgentTurnEnvelope(
        data=AgentTurnResponse(
            sessionId=sid,
            replyText=out["reply"],
            draft=_to_draft_vo(sid, out["draft"]),
            cards=out["cards"],
            progress={},
            needLogin=need_login,
            events=[f"agent3_done:{out['events']}"],
            toolTraces=[],
        )
    )


# ---------- 会话管理（契约对齐 /agent） ----------


@router.get("/sessions", response_model=SessionListEnvelope)
async def list_sessions(
    authorization: str | None = Depends(get_authorization),
) -> SessionListEnvelope:
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
    user_id = _extract_user_id(authorization)
    rows = await _list_sessions(user_id)
    owned_ids = {r["session_id"] for r in rows}
    if session_id not in owned_ids:
        return HistoryEnvelope(data=[])
    try:
        await ensure_messages_table()
        page = await list_messages(session_id, limit=limit, offset=offset)
    except Exception:
        page = []
    return HistoryEnvelope(
        data=[HistoryMessage(role=m["role"], content=m["content"]) for m in page]
    )
