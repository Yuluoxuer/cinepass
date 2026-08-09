"""agent3 短期记忆：LangGraph PostgresSaver（checkpointer）生命周期管理。

与 ``agent/langgraph/checkpoint.py`` 同构，但使用 agent3 自包含配置
（``agent3.config.get_settings().postgres_uri``）。未配置 Postgres 时返回 ``None``，
图以无状态模式运行（与旧行为一致）。
"""
from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

from agent3.config import get_settings

_checkpointer = None
_pool = None


def get_checkpointer():
    """当前进程内的 AsyncPostgresSaver；未启用则为 ``None``。"""
    return _checkpointer


def get_pool():
    """当前进程内的 Postgres 连接池；未启用则为 ``None``。"""
    return _pool


@asynccontextmanager
async def checkpoint_lifespan() -> AsyncIterator[None]:
    """FastAPI lifespan：打开连接池、初始化 checkpointer 表、挂到全局；退出时关闭。"""
    global _checkpointer, _pool

    uri = (get_settings().postgres_uri or "").strip()
    if not uri:
        _checkpointer = None
        _pool = None
        yield
        return

    from psycopg.rows import dict_row
    from psycopg_pool import AsyncConnectionPool
    from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

    pool = AsyncConnectionPool(
        conninfo=uri,
        max_size=10,
        kwargs={"autocommit": True, "prepare_threshold": 0, "row_factory": dict_row},
        open=False,
    )
    await pool.open()
    saver = AsyncPostgresSaver(pool)
    await saver.setup()

    _pool = pool
    _checkpointer = saver
    try:
        yield
    finally:
        _checkpointer = None
        _pool = None
        await pool.close()
