"""对话消息仓储 — 读写 Agent 自有库。

调用方：process_turn.py list_messages / append_messages。
Glob：无已有 message_repo。不经中台 REST。
用户指令：「去掉X-Internal-Api-Key…agent…单独数据库…sqlalchemy」
"""
from __future__ import annotations

import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models import AgentMessage, AgentSession


def ensure_session(db: Session, session_id: str, user_id: str | None = None) -> AgentSession:
    row = db.get(AgentSession, session_id)
    if row is None:
        row = AgentSession(session_id=session_id, user_id=user_id)
        db.add(row)
        db.flush()
    elif user_id and not row.user_id:
        row.user_id = user_id
    return row


def list_messages(
    db: Session, session_id: str, *, limit: int = 20
) -> list[dict[str, Any]]:
    stmt = (
        select(AgentMessage)
        .where(AgentMessage.session_id == session_id)
        .order_by(AgentMessage.created_at.asc())
        .limit(min(max(limit, 1), 50))
    )
    rows = db.scalars(stmt).all()
    return [
        {
            "id": r.message_id,
            "role": r.role,
            "text": r.content,
            "content": r.content,
            "cards": r.cards_json,
            "createdAt": r.created_at.isoformat() if r.created_at else None,
        }
        for r in rows
    ]


def append_messages(
    db: Session,
    session_id: str,
    messages: list[dict[str, Any]],
    *,
    user_id: str | None = None,
) -> int:
    ensure_session(db, session_id, user_id=user_id)
    saved = 0
    for item in messages:
        role = str(item.get("role") or "user")
        content = str(item.get("content") or item.get("text") or "")
        cards = item.get("cards") or item.get("cardsJson")
        events = item.get("events") or item.get("eventsJson")
        msg = AgentMessage(
            message_id=f"msg_{uuid.uuid4().hex[:16]}",
            session_id=session_id,
            role=role,
            content=content,
            cards_json=cards,
            events_json=events,
        )
        db.add(msg)
        saved += 1
    db.flush()
    return saved
