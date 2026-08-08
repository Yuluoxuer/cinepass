"""RAG 知识库检索 Tool：调用 agent.rag.retriever 做真实向量检索。

检索本地 Chroma 知识库（退票政策、改签规则等），返回相关原文块供 LLM 参考。
"""
from __future__ import annotations

from langchain.tools import tool

from agent.rag.retriever import retrieve


@tool
def search_knowledge_base(query: str) -> str:
    """搜索业务知识库，获取退票政策、改签规则、操作指南等信息。

    当用户询问影院政策、退票规则、使用方法、常见问题时应调用此工具。
    """
    return retrieve(query)


RAG_TOOLS = [search_knowledge_base]

__all__ = [
    "RAG_TOOLS",
    "search_knowledge_base",
]
