"""前端契约端点 /api/v1/agent/turns：内部走 booking_graph。

系分 §8.1 定义的前端契约端点；本模块把 booking_graph 的输出映射为前端
``AgentTurnResponse`` 形状（replyText/sessionId/draft 等），让前端零改动接入
booking_graph 的多轮购票流程。
"""
from __future__ import annotations

import base64
import datetime
import json
import uuid
from typing import Any

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from agent.langgraph.booking_graph import build_booking_graph
from agent.langgraph.checkpoint import get_checkpointer, get_pool
from agent.request_context import use_authorization
from fapi.deps import get_authorization

router = APIRouter(prefix="/agent", tags=["agent"])

# 编译缓存：与 booking.py 一致，lifespan 启停时由 reset_agent_graph_cache 清空
_compiled: Any = None
_compiled_with_cp: Any = None


def reset_agent_graph_cache() -> None:
    """lifespan 启停时清空编译缓存，避免挂上已关闭的 checkpointer。"""
    global _compiled, _compiled_with_cp
    _compiled = None
    _compiled_with_cp = None


def _graph() -> Any:
    global _compiled, _compiled_with_cp
    cp = get_checkpointer()
    if cp is None:
        if _compiled is None:
            _compiled = build_booking_graph(checkpointer=None)
        return _compiled
    if _compiled_with_cp is None:
        _compiled_with_cp = build_booking_graph(checkpointer=cp)
    return _compiled_with_cp


# ---------- 前端契约模型（对齐 src/types/index.ts） ----------


class CardActionBody(BaseModel):
    cardId: str
    actionId: str
    itemId: str | None = None
    draftPatch: dict[str, Any] | None = None


class AgentTurnRequest(BaseModel):
    """前端 AgentTurnRequest。message 与 cardAction 至少二选一。"""

    sessionId: str | None = None
    message: str | None = None
    cardAction: CardActionBody | None = None
    clientDraftVersion: int | None = None
    debug: bool = False
    latitude: float | None = None
    longitude: float | None = None


class AgentTurnDraftVO(BaseModel):
    """前端 BookingDraft 必填字段（booking_graph 子集 + 必填补全）。"""

    sessionId: str
    source: str = "agent"
    state: str = "Idle"
    movieId: str | None = None
    filmTitle: str | None = None
    cinemaId: str | None = None
    cinemaName: str | None = None
    showId: str | None = None
    date: str | None = None
    timeWindow: str | None = None
    count: int = 2
    seatIds: list[str] = Field(default_factory=list)
    preferRow: str | None = None
    preferSide: str | None = None
    together: bool | None = None
    lockId: str | None = None
    orderId: str | None = None
    expireAt: str | None = None
    version: int = 1


class AgentTurnResponse(BaseModel):
    sessionId: str
    replyText: str
    draft: AgentTurnDraftVO
    cards: list[dict[str, Any]] = Field(default_factory=list)
    progress: dict[str, Any] = Field(default_factory=dict)
    needLogin: bool = False
    events: list[str] = Field(default_factory=list)
    toolTraces: list[dict[str, Any]] = Field(default_factory=list)


class AgentTurnEnvelope(BaseModel):
    """前端 client.ts 期望的 {code, message, data} 信封格式。"""

    code: int = 200
    message: str = "ok"
    data: AgentTurnResponse


# ---------- 辅助 ----------


def _extract_user_id(authorization: str | None) -> str:
    """从 JWT payload 解析 userId（仅 decode，不验证签名）。"""
    if not authorization:
        return "anon"
    token = authorization.removeprefix("Bearer ").strip()
    parts = token.split(".")
    if len(parts) < 2:
        return "anon"
    try:
        payload = parts[1]
        payload += "=" * (4 - len(payload) % 4)
        data = json.loads(base64.urlsafe_b64decode(payload))
        return str(data.get("userId") or data.get("user_id") or data.get("sub") or "anon")
    except Exception:
        return "anon"


def _generate_session_id(user_id: str) -> str:
    """生成 session_id：sess_{userId前8位}_{时间戳}_{uuid前8位}。"""
    prefix = (user_id or "anon")[:8]
    ts = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    suffix = uuid.uuid4().hex[:8]
    return f"sess_{prefix}_{ts}_{suffix}"


_session_table_ready = False


async def _ensure_session_table() -> None:
    """确保 agent_sessions 表存在（进程内只建一次）。"""
    global _session_table_ready
    if _session_table_ready:
        return
    pool = get_pool()
    if pool is None:
        return
    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """
                CREATE TABLE IF NOT EXISTS agent_sessions (
                    session_id     VARCHAR(128) PRIMARY KEY,
                    user_id        VARCHAR(64)  NOT NULL,
                    title          VARCHAR(200),
                    created_at     TIMESTAMPTZ  DEFAULT NOW(),
                    last_message_at TIMESTAMPTZ  DEFAULT NOW()
                )
                """
            )
            await cur.execute(
                "CREATE INDEX IF NOT EXISTS idx_agent_sessions_user ON agent_sessions(user_id, last_message_at DESC)"
            )
    _session_table_ready = True


async def _record_session(session_id: str, user_id: str, title: str | None = None) -> None:
    """插入或更新 session 记录。"""
    pool = get_pool()
    if pool is None:
        return
    await _ensure_session_table()
    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """
                INSERT INTO agent_sessions (session_id, user_id, title, created_at, last_message_at)
                VALUES (%s, %s, %s, NOW(), NOW())
                ON CONFLICT (session_id)
                DO UPDATE SET last_message_at = NOW(),
                              title = COALESCE(EXCLUDED.title, agent_sessions.title)
                """,
                (session_id, user_id, title),
            )


async def _list_sessions(user_id: str, limit: int = 20) -> list[dict[str, Any]]:
    """获取用户的会话列表。"""
    pool = get_pool()
    if pool is None:
        return []
    await _ensure_session_table()
    async with pool.connection() as conn:
        async with conn.cursor() as cur:
            await cur.execute(
                """
                SELECT session_id, user_id, title, created_at, last_message_at
                FROM agent_sessions
                WHERE user_id = %s
                ORDER BY last_message_at DESC
                LIMIT %s
                """,
                (user_id, limit),
            )
            rows = await cur.fetchall()
    return [dict(r) for r in rows] if rows else []


async def _get_history_messages(session_id: str, limit: int = 5, offset: int = 0) -> list[dict[str, str]]:
    """从 LangGraph checkpoint 的 state.history 获取历史消息（分页，从最新往最旧）。

    ``offset`` 表示从末尾起已加载的消息数：
    - offset=0  返回最近 ``limit`` 条（chronological order，最旧在前）
    - offset=5  返回再往前 ``limit`` 条
    """
    graph = _graph()
    config: dict[str, Any] = {"configurable": {"thread_id": session_id}}
    try:
        state = await graph.aget_state(config)
    except Exception:
        return []
    if state is None or not state.values:
        return []
    history = state.values.get("history") or []
    total = len(history)
    end = total - offset
    if end <= 0:
        return []
    start = max(0, end - limit)
    page = history[start:end]
    return [{"role": h.get("role", "user"), "content": h.get("content", "")} for h in page]


def _derive_state(draft_complete: bool, draft: dict[str, Any]) -> str:
    """根据 draft 完备度推导前端 BookingState。"""
    if draft.get("orderId"):
        return "PayMock"
    if draft_complete:
        return "ConfirmOrder"
    if draft.get("showId") or draft.get("lockId"):
        return "SelectSeat"
    if draft.get("cinemaId"):
        return "SelectShow"
    if draft.get("movieId"):
        return "SelectCinema"
    return "SelectMovie"


def _compose_message(body: AgentTurnRequest) -> str:
    """前端可能只发 cardAction 不发 message；统一合成 booking_graph 可读文本。

    卡片点击 ``select`` 时，前端会在 ``draftPatch`` 中带上 movieId/filmTitle、
    cinemaId/cinemaName 或 showId。这里把它们翻译成自然语言，让 booking_graph
    的字段提取能正确识别并填入 BookingDraft。
    """
    if body.message and body.message.strip():
        return body.message.strip()
    if body.cardAction:
        ca = body.cardAction
        patch = ca.draftPatch or {}

        # 选电影卡片 → "看{片名}"
        if patch.get("filmTitle"):
            return f"看{patch['filmTitle']}"
        # 选影院卡片 → "{影院名}"
        if patch.get("cinemaName"):
            return f"选{patch['cinemaName']}影院"
        # 选场次卡片 → "选{showId}这场"
        if patch.get("showId"):
            return f"选{patch['showId']}这场"
        # 座位方案确认 → draftPatch 里已有 seatIds
        if patch.get("seatIds"):
            seats = ", ".join(str(s) for s in patch["seatIds"])
            return f"选座位 {seats}"

        # 兜底：把 draftPatch 的 kv 拼出来
        if patch:
            kv = ", ".join(f"{k}={v}" for k, v in patch.items())
            return f"（点卡操作：{kv}）"
        return f"（点卡操作 {ca.actionId}）"
    return ""


def _to_draft_vo(sid: str, draft: dict[str, Any], draft_complete: bool) -> AgentTurnDraftVO:
    return AgentTurnDraftVO(
        sessionId=sid,
        source="agent",
        state=_derive_state(draft_complete, draft),
        movieId=draft.get("movieId"),
        filmTitle=draft.get("filmTitle"),
        cinemaId=draft.get("cinemaId"),
        cinemaName=draft.get("cinemaName"),
        showId=draft.get("showId"),
        date=draft.get("date"),
        timeWindow=draft.get("timeWindow"),
        count=draft.get("count") or 2,
        seatIds=list(draft.get("seatIds") or []),
        preferRow=draft.get("preferRow"),
        preferSide=draft.get("preferSide"),
        together=draft.get("together"),
        lockId=draft.get("lockId"),
        orderId=draft.get("orderId"),
        expireAt=draft.get("expireAt"),
        version=1,
    )


# ---------- 端点 ----------


@router.post("/turns", response_model=AgentTurnEnvelope)
async def agent_turns(
    body: AgentTurnRequest,
    authorization: str | None = Depends(get_authorization),
) -> AgentTurnEnvelope:
    """前端契约端点：内部走 booking_graph 多轮购票流程。

    - ``message`` 直接送入 booking_graph
    - ``cardAction`` 的 draftPatch 会先写入 checkpoint 的 bookingdraft，再合成自然语言消息
    - ``cards``/``progress`` 留空，前端 fallback 到 ``progressFromDraft``
    - ``needLogin`` 在 JWT 缺失且 booking_graph 触发鉴权失败时由事件推断
    - 响应包装为 ``{code, message, data}`` 信封，与后端 Java 一致
    """
    user_id = _extract_user_id(authorization)
    sid = (body.sessionId or "").strip() or _generate_session_id(user_id)
    message = _compose_message(body)

    if not message:
        return AgentTurnEnvelope(
            data=AgentTurnResponse(
                sessionId=sid,
                replyText="请告诉我您想看什么电影，或者有什么需要帮您处理的？",
                draft=_to_draft_vo(sid, {}, False),
            )
        )

    graph = _graph()
    config: dict[str, Any] = {"configurable": {"thread_id": sid}}

    # 卡片操作：先把 draftPatch 直接写入 checkpoint 的 bookingdraft，
    # 这样 graph 运行时 draft 已有 movieId/cinemaId 等，直接跳到问下一个缺失字段
    patch = body.cardAction.draftPatch if body.cardAction else None
    if patch:
        try:
            snap = await graph.aget_state(config)
            existing_draft = (snap.values or {}).get("bookingdraft") or {}
            merged = {**existing_draft, **patch}
            await graph.aupdate_state(config, {"bookingdraft": merged})
        except Exception:
            pass  # checkpoint 未启用或状态不存在，忽略

    with use_authorization(authorization):
        payload: dict[str, Any] = {
            "message": message,
            "authorization": authorization,
            "latitude": body.latitude,
            "longitude": body.longitude,
            "sessionId": sid,
        }
        result = await graph.ainvoke(payload, config)

    # 记录/更新 session 元数据
    title = message[:50] if message else None
    await _record_session(sid, user_id, title)

    draft = result.get("bookingdraft") or {}
    draft_complete = bool(result.get("draft_complete", False))
    events = list(result.get("events") or [])
    need_login = any("auth" in e or "unauthorized" in e.lower() for e in events)
    cards = list(result.get("cards") or [])

    return AgentTurnEnvelope(
        data=AgentTurnResponse(
            sessionId=sid,
            replyText=result.get("reply", "") or "",
            draft=_to_draft_vo(sid, draft, draft_complete),
            cards=cards,
            progress={},
            needLogin=need_login,
            events=events,
            toolTraces=[],
        )
    )


# ---------- 会话管理 ----------


class SessionMeta(BaseModel):
    sessionId: str
    userId: str
    title: str | None = None
    createdAt: str | None = None
    lastMessageAt: str | None = None


class SessionListEnvelope(BaseModel):
    code: int = 200
    message: str = "ok"
    data: list[SessionMeta]


class SessionCreateRequest(BaseModel):
    title: str | None = None


class SessionCreateEnvelope(BaseModel):
    code: int = 200
    message: str = "ok"
    data: SessionMeta


class HistoryMessage(BaseModel):
    role: str
    content: str


class HistoryEnvelope(BaseModel):
    code: int = 200
    message: str = "ok"
    data: list[HistoryMessage]


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
    return SessionCreateEnvelope(
        data=SessionMeta(
            sessionId=sid,
            userId=user_id,
            title=title,
            createdAt=datetime.datetime.now().isoformat(),
            lastMessageAt=datetime.datetime.now().isoformat(),
        )
    )


@router.get("/sessions/{session_id}/messages", response_model=HistoryEnvelope)
async def get_session_messages(
    session_id: str,
    limit: int = Query(default=5, ge=1, le=50),
    offset: int = Query(default=0, ge=0),
    authorization: str | None = Depends(get_authorization),
) -> HistoryEnvelope:
    """获取某会话的历史消息（分页，从 LangGraph checkpoint 读取）。"""
    messages = await _get_history_messages(session_id, limit, offset)
    return HistoryEnvelope(data=[HistoryMessage(role=m["role"], content=m["content"]) for m in messages])
