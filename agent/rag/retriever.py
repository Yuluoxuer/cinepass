"""知识库检索：query → embedding → Chroma top-k → 拼装文本。

供 ``agent.tools.rag_tools`` 等调用。检索逻辑独立于此，便于测试与复用；
embedding 模型与 chroma client 用 lru_cache 做单例懒加载，避免每次查询
都重新加载模型。
"""
from __future__ import annotations

from functools import lru_cache

import chromadb
from fastembed import TextEmbedding

from agent.rag.config import (
    CHROMA_DIR,
    COLLECTION_NAME,
    EMBEDDING_MODEL,
    MODEL_CACHE_DIR,
    TOP_K,
)


@lru_cache
def _get_embedder() -> TextEmbedding:
    """本地 embedding 模型单例：首次调用时加载，之后复用。

    cache_dir 指向项目内目录，便于离线/内网部署时预置模型。
    """
    MODEL_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    return TextEmbedding(model_name=EMBEDDING_MODEL, cache_dir=str(MODEL_CACHE_DIR))


@lru_cache
def _get_collection():
    """Chroma collection 单例：指向磁盘持久化库，数据实时读取。"""
    client = chromadb.PersistentClient(path=str(CHROMA_DIR))
    return client.get_or_create_collection(COLLECTION_NAME)


def _collection_count() -> int:
    """collection 中的块数量；库不存在时返回 0。"""
    if not CHROMA_DIR.exists():
        return 0
    try:
        return _get_collection().count()
    except Exception:
        return 0


def retrieve(query: str, top_k: int = TOP_K) -> str:
    """检索知识库，返回拼装好的文本；异常/空库时返回可读提示而非抛出。"""
    if not query or not query.strip():
        return "检索内容为空，请提供要查询的问题。"

    if _collection_count() == 0:
        return (
            "知识库当前为空（尚未灌入文档）。"
            "请先运行 python -m agent.rag.ingest 灌入知识文档。"
        )

    try:
        vector = list(_get_embedder().embed([query]))[0].tolist()
        result = _get_collection().query(query_embeddings=[vector], n_results=top_k)
    except Exception as exc:  # 检索失败不应把对话搞崩
        return f"知识库检索失败：{exc}"

    docs = (result.get("documents") or [[]])[0]
    metas = (result.get("metadatas") or [[]])[0]
    if not docs:
        return "知识库中未找到相关内容。"

    parts = ["【知识库检索结果】"]
    for i, (doc, meta) in enumerate(zip(docs, metas), start=1):
        source = meta.get("source") or "未知来源"
        section = meta.get("section") or ""
        origin = f"{source} / {section}" if section else source
        parts.append(f"\n[{i}] 来源: {origin}\n{doc.strip()}")
    return "\n".join(parts)


__all__ = ["retrieve"]
