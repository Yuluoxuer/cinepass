"""FAQ / 政策 Retriever 骨架 — 影片/场次/座位事实必须走 Tools，不经本模块编造。"""
from __future__ import annotations

from typing import Any


async def retrieve_faq(query: str, *, top_k: int = 3) -> list[dict[str, Any]]:
    """P1：接向量库；MVP 返回空，避免幻觉库存字段。"""
    _ = (query, top_k)
    return []
