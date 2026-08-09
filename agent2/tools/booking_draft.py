"""BookingDraft 工具：记录购票流程中用户提供的信息，按 session_id 持久化到 PostgreSQL。

配合 create_react_agent + PostgresSaver 使用：
agent 在购票对话中把已收集的电影/影院/时间/座位等信息结构化写入 draft，
跨轮读取，避免遗漏用户一句话里的多个要点。
"""
from __future__ import annotations

import json
from contextlib import contextmanager
from contextvars import ContextVar
from typing import Any, Iterator

import asyncpg
from langchain.tools import tool

from ..config import get_settings

# 当前会话的 session_id，由 agent.run() 注入
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


@tool
async def get_booking_draft() -> str:
    """查看当前购票草稿中已记录的信息。返回原始 JSON（movieId/cinemaId/showId/seatIds 等字段）。"""
    draft = await load_draft()
    # 过滤内部字段（如 _history），避免暴露给 LLM
    public = {k: v for k, v in draft.items() if not str(k).startswith("_")}
    return json.dumps(public, ensure_ascii=False) if public else "{}"


@tool
async def update_booking_draft(field: str, value: str) -> str:
    """记录购票草稿中的一个字段（已设置的字段不允许覆盖）。
    可用字段：
    - movieId / filmTitle：影片（先调用 searchMovies 查到 movieId 后填写）
    - cinemaId / cinemaName：影院（先调用 searchCinemas 查到 cinemaId 后填写）
    - showId：场次ID（先调用 listShows 查到后填写）
    - date：观影日期（YYYY-MM-DD）
    - timeWindow：时间段（morning/afternoon/evening）
    - count：票数
    - seatIds：座位ID（逗号分隔）
    每次用户补充信息后都要调用本工具记录。
    注意：字段一旦设置就不能覆盖。若用户明确要求更换（如"换一部/换影院"），
    需先调用 clearBookingDraft 清空后再重新设置。"""
    field = field.strip()
    value = value.strip()
    if not field:
        return "字段名不能为空。"
    draft = await load_draft()
    # 草稿锁定：已设置的字段禁止覆盖，防止误改用户已确认的选择
    if field in draft and draft[field]:
        return f"字段 {field} 已设置为 {draft[field]}，请不要覆盖。如需更换，请先调用 clearBookingDraft 清空后重设。"
    draft[field] = value
    await save_draft(draft)
    return json.dumps(draft, ensure_ascii=False)


@tool
async def clear_booking_draft() -> str:
    """清空当前购票草稿（下单完成后调用）。"""
    await save_draft({})
    return "购票草稿已清空。"


BOOKING_DRAFT_TOOLS = [get_booking_draft, update_booking_draft, clear_booking_draft]

__all__ = [
    "BOOKING_DRAFT_TOOLS",
    "ensure_table",
    "load_draft",
    "save_draft",
    "use_session_id",
]
