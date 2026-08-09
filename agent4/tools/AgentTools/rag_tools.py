"""RAG 知识库检索 Tool：调用 agent4.rag.retriever 做真实向量检索。

检索本地 Chroma 知识库（退票政策、改签规则、操作指南等），返回相关原文块供 LLM 参考。

检索作用域：
- 系统知识库始终检索（购票流程、平台规则等）。
- 当前关联影院知识库（影院位置、活动等）：staff 取 JWT cinemaId；C 端用户
  取会话中台草稿（booking-drafts）里的 cinemaId，即用户当前选中的影院。
"""
from __future__ import annotations

import asyncio

from langchain.tools import tool

from agent4.rag.retriever import retrieve


def _cinema_id_from_jwt() -> str | None:
    """从当前请求 JWT（ContextVar）解析 staff 绑定的 cinemaId。"""
    from agent4.api.contract import decode_jwt_claims
    from agent4.tools.Http2BackendTools.auth import get_authorization

    claims = decode_jwt_claims(get_authorization())
    if not claims:
        return None
    cid = claims.get("cinemaId")
    return str(cid) if cid else None


async def _cinema_id_from_draft() -> str | None:
    """从当前会话中台草稿读取用户选中的 cinemaId（C 端购票流程）。"""
    from agent4.tools.AgentTools.draft_tools import load_draft as load_middle_draft

    try:
        draft = await load_middle_draft()
        cid = (draft or {}).get("cinemaId")
        return str(cid) if cid else None
    except Exception:
        return None


async def _resolve_cinema_id() -> str | None:
    """解析当前对话应额外检索的影院ID：优先 JWT，其次会话中台草稿。"""
    try:
        cid = _cinema_id_from_jwt()
        if cid:
            return cid
    except Exception:
        pass
    return await _cinema_id_from_draft()


@tool
async def search_knowledge_base(query: str) -> str:
    """搜索业务知识库，获取退票政策、改签规则、使用方法、操作指南、影院位置/活动等信息。

    当用户询问影院政策、退票规则、改签次数、使用方法、常见问题，或某个影院的位置、
    活动等运营信息时应调用此工具。
    """
    cinema_id = await _resolve_cinema_id()
    # retrieve 为同步重活（embedding + Chroma 查询），丢线程池避免阻塞事件循环
    return await asyncio.to_thread(retrieve, query, cinema_id=cinema_id)


RAG_TOOLS = [search_knowledge_base]

__all__ = [
    "RAG_TOOLS",
    "search_knowledge_base",
]
