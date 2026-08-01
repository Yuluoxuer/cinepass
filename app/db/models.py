"""Agent 自有库 ORM：agent_session / agent_message。

调用方：message_repo、init_db。Glob：无已有 models。
字段：session_id/message_id (str)、role、content、cards_json、created_at (UTC ISO)。
用户指令：「去掉X-Internal-Api-Key…agent…单独使用一个数据库…sqlalchemy」
"""
from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import DateTime, String, Text
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.types import JSON

from app.db import Base


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class AgentSession(Base):
    __tablename__ = "agent_session"

    session_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=_utcnow, onupdate=_utcnow
    )


class AgentMessage(Base):
    __tablename__ = "agent_message"

    message_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    session_id: Mapped[str] = mapped_column(String(64), index=True, nullable=False)
    role: Mapped[str] = mapped_column(String(16), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    cards_json: Mapped[dict | list | None] = mapped_column(JSON, nullable=True)
    events_json: Mapped[dict | list | None] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=_utcnow, index=True
    )
