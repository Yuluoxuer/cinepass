"""agent3 前端契约模型与辅助函数（自包含复制自 fapi/api/agent.py）。

仅包含 agent3/api.py 用到的部分：Pydantic 契约模型 + 会话/草稿辅助函数，
不包含 /agent router 与端点（那些依赖 agent2 主 agent，agent3 不需要）。
"""
from __future__ import annotations

import base64
import datetime
import json
import uuid
from typing import Any

from pydantic import BaseModel, Field


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
    # 前端手动页面/中台草稿快照，用于同步到草稿（避免 Agent 不知道手动选片）
    clientDraft: dict[str, Any] | None = None


class AgentTurnDraftVO(BaseModel):
    """前端 BookingDraft 必填字段。"""

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
    try:
        from agent3.booking_draft import _get_pool
        pool = await _get_pool()
    except Exception:
        return
    async with pool.acquire() as conn:
        await conn.execute(
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
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_agent_sessions_user ON agent_sessions(user_id, last_message_at DESC)"
        )
    _session_table_ready = True


async def _record_session(session_id: str, user_id: str, title: str | None = None) -> None:
    """插入或更新 session 记录。"""
    try:
        from agent3.booking_draft import _get_pool
        pool = await _get_pool()
        await _ensure_session_table()
        async with pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO agent_sessions (session_id, user_id, title, created_at, last_message_at)
                VALUES ($1, $2, $3, NOW(), NOW())
                ON CONFLICT (session_id)
                DO UPDATE SET last_message_at = NOW(),
                              title = COALESCE(EXCLUDED.title, agent_sessions.title)
                """,
                session_id,
                user_id,
                title,
            )
    except Exception:
        pass


async def _list_sessions(user_id: str, limit: int = 20) -> list[dict[str, Any]]:
    """获取用户的会话列表。"""
    try:
        from agent3.booking_draft import _get_pool
        pool = await _get_pool()
        await _ensure_session_table()
        async with pool.acquire() as conn:
            rows = await conn.fetch(
                """
                SELECT session_id, user_id, title, created_at, last_message_at
                FROM agent_sessions
                WHERE user_id = $1
                ORDER BY last_message_at DESC
                LIMIT $2
                """,
                user_id,
                limit,
            )
        return [dict(r) for r in rows] if rows else []
    except Exception:
        return []


def _derive_state(draft: dict[str, Any]) -> str:
    """根据草稿完备度推导前端 BookingState。"""
    if draft.get("orderId"):
        return "PayMock"
    if draft.get("lockId"):
        return "ConfirmOrder"
    if draft.get("seatIds"):
        return "ConfirmOrder"
    if draft.get("showId"):
        return "SelectSeat"
    if draft.get("cinemaId"):
        return "SelectShow"
    if draft.get("movieId"):
        return "SelectCinema"
    return "SelectMovie"


def _to_draft_vo(sid: str, draft: dict[str, Any]) -> AgentTurnDraftVO:
    seat_ids = draft.get("seatIds") or []
    if isinstance(seat_ids, str):
        seat_ids = [s.strip() for s in seat_ids.split(",") if s.strip()]
    return AgentTurnDraftVO(
        sessionId=sid,
        source="agent",
        state=_derive_state(draft),
        movieId=draft.get("movieId"),
        filmTitle=draft.get("filmTitle"),
        cinemaId=draft.get("cinemaId"),
        cinemaName=draft.get("cinemaName"),
        showId=draft.get("showId"),
        date=draft.get("date"),
        timeWindow=draft.get("timeWindow"),
        count=int(draft.get("count") or 2),
        seatIds=list(seat_ids),
        preferRow=draft.get("preferRow"),
        preferSide=draft.get("preferSide"),
        together=draft.get("together"),
        lockId=draft.get("lockId"),
        orderId=draft.get("orderId"),
        expireAt=draft.get("expireAt"),
        version=1,
    )


def _compose_message(body: AgentTurnRequest) -> str:
    """把 message 或 cardAction 合成为发给 agent 的自然语言。

    卡片操作合成为自然口语，不携带 movieId/cinemaId/showId 等内部技术字段——
    选择信息已预先写入草稿，agent 读草稿即可继续流程，不必把技术话术带进对话历史。
    """
    if body.message and body.message.strip():
        return body.message.strip()
    if body.cardAction:
        ca = body.cardAction
        patch = ca.draftPatch or {}
        if patch.get("filmTitle"):
            return f"我选择了电影《{patch['filmTitle']}》，请继续帮我完成购票。"
        if patch.get("cinemaName"):
            return f"我选择了影院 {patch['cinemaName']}，请继续。"
        if patch.get("seatIds"):
            seats = ", ".join(str(s) for s in patch["seatIds"])
            return f"我选择了座位 {seats}，请帮我锁座并确认下单。"
        if patch.get("showId"):
            if patch.get("date"):
                return f"我选择了 {patch['date']} 的场次，请帮我查询座位并选座。"
            return "我选择了场次，请帮我查询座位并选座。"
        if patch:
            kv = ", ".join(f"{k}={v}" for k, v in patch.items())
            return f"（点卡操作：{kv}）"
        return f"（点卡操作 {ca.actionId}）"
    return ""


# ---------- 会话管理模型 ----------


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
