"""BookingDraft 本地持久化：按 session_id 存 PostgreSQL ``booking_drafts`` 表。

跨轮状态（草稿 + 会话上下文）经此模块落库；与中台 /booking-drafts 无直接关系
（中台草稿见 ``draft_tools.py``）。
"""
from __future__ import annotations

import json
from contextlib import contextmanager
from contextvars import ContextVar
from typing import Any, Iterator

import asyncpg

from agent4.config import get_settings

_session_id: ContextVar[str] = ContextVar("booking_session_id", default="")

_pool: asyncpg.Pool | None = None


def get_session_id() -> str:
    return _session_id.get()


@contextmanager
def use_session_id(session_id: str) -> Iterator[str]:
    token = _session_id.set(session_id or "")
    try:
        yield _session_id.get()
    finally:
        _session_id.reset(token)


async def _get_pool() -> asyncpg.Pool:
    global _pool
    if _pool is None:
        settings = get_settings()
        _pool = await asyncpg.create_pool(settings.postgres_uri, min_size=1, max_size=5)
    return _pool


async def ensure_table() -> None:
    """初始化 booking_drafts 表。"""
    pool = await _get_pool()
    async with pool.acquire() as conn:
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS booking_drafts (
                session_id TEXT PRIMARY KEY,
                draft JSONB NOT NULL DEFAULT '{}',
                updated_at TIMESTAMP DEFAULT NOW()
            )
        """)


async def load_draft(session_id: str | None = None) -> dict[str, Any]:
    sid = session_id or _session_id.get()
    if not sid:
        return {}
    pool = await _get_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT draft FROM booking_drafts WHERE session_id = $1", sid
        )
        if row and row["draft"]:
            return json.loads(row["draft"])
    return {}


async def save_draft(draft: dict[str, Any], session_id: str | None = None) -> dict[str, Any]:
    sid = session_id or _session_id.get()
    if not sid:
        return draft
    pool = await _get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO booking_drafts (session_id, draft, updated_at)
            VALUES ($1, $2, NOW())
            ON CONFLICT (session_id) DO UPDATE SET draft = $2, updated_at = NOW()
            """,
            sid,
            json.dumps(draft, ensure_ascii=False),
        )
    return draft


__all__ = [
    "get_session_id",
    "use_session_id",
    "_get_pool",
    "ensure_table",
    "load_draft",
    "save_draft",
]
