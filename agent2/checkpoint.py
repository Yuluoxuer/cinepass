"""基于PostgreSQL的Checkpoint存储系统"""
from __future__ import annotations
import json
from datetime import datetime
from typing import List, Dict, Any, Optional
import asyncpg
from config import get_settings

_pool: Optional[asyncpg.Pool] = None

async def init_pool():
    """初始化数据库连接池"""
    global _pool
    if _pool is None:
        settings = get_settings()
        _pool = await asyncpg.create_pool(settings.postgres_uri, min_size=2, max_size=10)
    return _pool

async def close_pool():
    """关闭数据库连接池"""
    global _pool
    if _pool is not None:
        await _pool.close()
        _pool = None

async def create_tables():
    """创建sessions和messages表"""
    pool = await init_pool()
    async with pool.acquire() as conn:
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                session_id TEXT PRIMARY KEY,
                user_id TEXT,
                created_at TIMESTAMP DEFAULT NOW(),
                updated_at TIMESTAMP DEFAULT NOW(),
                metadata JSONB DEFAULT '{}'
            )
        """)
        await conn.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                id SERIAL PRIMARY KEY,
                session_id TEXT REFERENCES sessions(session_id) ON DELETE CASCADE,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp TIMESTAMP DEFAULT NOW(),
                tool_calls JSONB
            )
        """)
        await conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_messages_session_id ON messages(session_id)
        """)
        await conn.execute("""
            CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp)
        """)

async def create_session(session_id: str, user_id: Optional[str] = None, metadata: Optional[Dict[str, Any]] = None):
    """创建新会话"""
    pool = await init_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO sessions (session_id, user_id, metadata)
            VALUES ($1, $2, $3)
            ON CONFLICT (session_id) DO UPDATE SET updated_at = NOW()
            """,
            session_id,
            user_id,
            json.dumps(metadata or {})
        )

async def save_message(session_id: str, role: str, content: str, tool_calls: Optional[List[Dict]] = None):
    """保存消息到数据库"""
    pool = await init_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            """
            INSERT INTO messages (session_id, role, content, tool_calls)
            VALUES ($1, $2, $3, $4)
            """,
            session_id,
            role,
            content,
            json.dumps(tool_calls) if tool_calls else None
        )
        await conn.execute(
            "UPDATE sessions SET updated_at = NOW() WHERE session_id = $1",
            session_id
        )

async def load_messages(session_id: str, limit: Optional[int] = None) -> List[Dict[str, Any]]:
    """加载会话的消息历史"""
    pool = await init_pool()
    async with pool.acquire() as conn:
        if limit:
            rows = await conn.fetch(
                """
                SELECT role, content, timestamp, tool_calls
                FROM messages
                WHERE session_id = $1
                ORDER BY timestamp DESC
                LIMIT $2
                """,
                session_id,
                limit
            )
            rows = list(reversed(rows))
        else:
            rows = await conn.fetch(
                """
                SELECT role, content, timestamp, tool_calls
                FROM messages
                WHERE session_id = $1
                ORDER BY timestamp ASC
                """,
                session_id
            )
        
        messages = []
        for row in rows:
            msg = {
                "role": row["role"],
                "content": row["content"],
                "timestamp": row["timestamp"].isoformat()
            }
            if row["tool_calls"]:
                msg["tool_calls"] = json.loads(row["tool_calls"])
            messages.append(msg)
        return messages

async def get_session_metadata(session_id: str) -> Optional[Dict[str, Any]]:
    """获取会话元数据"""
    pool = await init_pool()
    async with pool.acquire() as conn:
        row = await conn.fetchrow(
            "SELECT metadata FROM sessions WHERE session_id = $1",
            session_id
        )
        if row:
            return json.loads(row["metadata"])
        return None

async def update_session_metadata(session_id: str, metadata: Dict[str, Any]):
    """更新会话元数据"""
    pool = await init_pool()
    async with pool.acquire() as conn:
        await conn.execute(
            "UPDATE sessions SET metadata = $1, updated_at = NOW() WHERE session_id = $2",
            json.dumps(metadata),
            session_id
        )

async def delete_session(session_id: str):
    """删除会话及其所有消息"""
    pool = await init_pool()
    async with pool.acquire() as conn:
        await conn.execute("DELETE FROM sessions WHERE session_id = $1", session_id)
