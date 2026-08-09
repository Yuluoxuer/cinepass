"""agent4 对话历史存储：``agent_messages`` 表（用户输入 + Agent 输出）。

短期记忆（图的运行状态，含 history）由 LangGraph PostgresSaver（``agent4.graph.checkpoint``）
持久化；本模块把每轮对话落到独立的 ``agent_messages`` 表，供会话历史端点读取与分页。
"""
from __future__ import annotations

from typing import Any

from agent4.config import get_settings
from agent4.tools.AgentTools.booking_draft import _get_pool

_messages_table_ready = False


async def ensure_messages_table() -> None:
    """初始化 agent_messages 表（幂等，进程内只建一次）。"""
    global _messages_table_ready
    if _messages_table_ready:
        return
    pool = await _get_pool()
    async with pool.acquire() as conn:
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS agent_messages (
                id BIGSERIAL PRIMARY KEY,
                session_id VARCHAR(128) NOT NULL,
                role VARCHAR(16) NOT NULL,
                content TEXT NOT NULL,
                created_at TIMESTAMPTZ DEFAULT NOW()
            )
        """)
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_agent_messages_session ON agent_messages(session_id, id)"
        )
    _messages_table_ready = True


async def append_message(session_id: str, role: str, content: str) -> None:
    """追加一条消息；session_id 或 content 为空时跳过。"""
    if not session_id or not content:
        return
    pool = await _get_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            "INSERT INTO agent_messages (session_id, role, content) VALUES ($1, $2, $3)",
            session_id, role, content,
        )


async def append_turn(session_id: str, user_text: str, assistant_text: str) -> None:
    """记录一轮对话：user 输入 + assistant 回复。"""
    await append_message(session_id, "user", user_text)
    await append_message(session_id, "assistant", assistant_text)


async def list_messages(session_id: str, limit: int = 40, offset: int = 0) -> list[dict[str, Any]]:
    """按时间正序返回某会话的消息窗口（旧→新），用于会话历史端点。"""
    pool = await _get_pool()
    async with pool.acquire() as conn:
        rows = await conn.fetch(
            """SELECT role, content, created_at FROM agent_messages
               WHERE session_id = $1 ORDER BY id DESC LIMIT $2 OFFSET $3""",
            session_id, limit, offset,
        )
    return [
        {"role": r["role"], "content": r["content"]}
        for r in reversed(rows or [])
    ]


async def load_history(session_id: str, limit: int | None = None) -> list[dict[str, str]]:
    """最近 N 条消息（旧→新），作为图输入的对话历史（短期记忆窗口）。"""
    if limit is None:
        limit = max(get_settings().memory_window * 2, 10)
    return await list_messages(session_id, limit=limit, offset=0)


__all__ = [
    "ensure_messages_table",
    "append_message",
    "append_turn",
    "list_messages",
    "load_history",
]
