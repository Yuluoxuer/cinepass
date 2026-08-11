"""知识库检索：query → embedding → Chroma top-k → 拼装文本。

供 ``agent4.tools.AgentTools.rag_tools`` 等调用。检索逻辑独立于此，便于测试与复用；
embedding 模型与 chroma client 用 lru_cache 做单例懒加载，避免每次查询
都重新加载模型。
"""
from __future__ import annotations

import logging
from functools import lru_cache

import chromadb
from fastembed import TextEmbedding

logger = logging.getLogger(__name__)

from agent4.rag.config import (
    CHROMA_DIR,
    COLLECTION_NAME,
    EMBEDDING_MODEL,
    MODEL_CACHE_DIR,
    SCOPE_CINEMA,
    SCOPE_SYSTEM,
    TOP_K,
    collection_name,
    ensure_offline_if_cached,
)


@lru_cache
def _get_embedder() -> TextEmbedding:
    """本地 embedding 模型单例：首次调用时加载，之后复用。

    cache_dir 指向项目内目录，便于离线/内网部署时预置模型。
    """
    MODEL_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    ensure_offline_if_cached()
    return TextEmbedding(model_name=EMBEDDING_MODEL, cache_dir=str(MODEL_CACHE_DIR))


@lru_cache
def _get_client():
    """Chroma client 单例：与 ingest 共用同一持久化库，避免多 client 锁竞争。"""
    CHROMA_DIR.mkdir(parents=True, exist_ok=True)
    return chromadb.PersistentClient(path=str(CHROMA_DIR))


@lru_cache(maxsize=32)
def _get_collection(name: str):
    """按名取 collection（按名缓存；maxsize 限制防止影院一多缓存无界增长）。"""
    return _get_client().get_or_create_collection(name)


def _collection_count(name: str) -> int:
    """指定 collection 中的块数量；库不存在时返回 0。"""
    if not CHROMA_DIR.exists():
        return 0
    try:
        return _get_collection(name).count()
    except Exception:
        return 0


# 未绑定影院时回退检索的影院库数量上限（防止影院极多时一次查询扇出过大）
MAX_FALLBACK_CINEMA_COLLECTIONS = 20


def _all_cinema_targets() -> list[tuple[str, str]]:
    """所有有数据的影院知识库 → [(collection 名, 影院ID)]。

    知识库面向 C 端全员开放：对话未关联影院时（匿名 / 未选影院），
    回退检索全部影院知识库，而不是只查系统库。
    """
    prefix = f"{COLLECTION_NAME}_cinema_"
    out: list[tuple[str, str]] = []
    try:
        for col in _get_client().list_collections():
            name = getattr(col, "name", None) or str(col)
            if not name.startswith(prefix):
                continue
            if _collection_count(name) > 0:
                out.append((name, name[len(prefix):]))
    except Exception as exc:
        logger.warning("RAG 枚举影院知识库失败：%s", exc)
    if len(out) > MAX_FALLBACK_CINEMA_COLLECTIONS:
        logger.warning(
            "RAG 影院知识库 %d 个超过上限 %d，未绑定影院时只检索前 %d 个",
            len(out), MAX_FALLBACK_CINEMA_COLLECTIONS, MAX_FALLBACK_CINEMA_COLLECTIONS,
        )
        out = out[:MAX_FALLBACK_CINEMA_COLLECTIONS]
    return out


@lru_cache(maxsize=128)
def _resolve_cinema_name(cinema_id: str) -> str | None:
    """按 cinemaId 调中台解析影院名称（带缓存）；失败返回 None，调用方回退显示 ID。"""
    try:
        import httpx

        from agent4.config import get_settings

        base = get_settings().backend_base_url.rstrip("/")
        resp = httpx.get(f"{base}/cinemas/{cinema_id}", timeout=1.0)
        resp.raise_for_status()
        data = resp.json().get("data") or {}
        name = str(data.get("name") or "").strip()
        return name or None
    except Exception as exc:
        logger.info("RAG 解析影院名称失败 cinema_id=%r：%s", cinema_id, exc)
        return None


def retrieve(query: str, top_k: int = TOP_K, cinema_id: str | None = None) -> str:
    """检索知识库，返回拼装好的文本；异常/空库时返回可读提示而非抛出。

    系统知识库始终检索；传入 ``cinema_id`` 时额外检索该影院的独立知识库；
    未传 ``cinema_id``（匿名 / 未选影院）时回退检索所有影院知识库（C 端全员开放）。
    来源标注 ``[系统]`` / ``[影院:名称或ID]`` 前缀，便于区分不同影院来源。
    """
    if not query or not query.strip():
        return "检索内容为空，请提供要查询的问题。"

    # (collection 名, scope, 影院ID | None)
    targets: list[tuple[str, str, str | None]] = [(COLLECTION_NAME, SCOPE_SYSTEM, None)]
    if cinema_id:
        try:
            targets.append((collection_name("cinema", cinema_id), SCOPE_CINEMA, cinema_id))
        except ValueError:
            pass  # 非法 cinemaId 忽略，仅检索系统库
    else:
        targets.extend((n, SCOPE_CINEMA, cid) for n, cid in _all_cinema_targets())

    # 只查询存在分块的 collection（避免对空库无效调用）
    existing = [t for t in targets if _collection_count(t[0]) > 0]
    logger.info(
        "RAG 检索 query=%r cinema_id=%r 目标集合=%s 有数据集合=%s",
        query[:60], cinema_id,
        [t[0] for t in targets], [t[0] for t in existing],
    )
    if not existing:
        return (
            "知识库当前为空（尚未灌入文档）。"
            "请先运行 python -m agent4.rag.ingest 灌入知识文档。"
        )

    try:
        vector = list(_get_embedder().embed([query]))[0].tolist()
        parts = ["【知识库检索结果】"]
        seq = 1
        for coll_name, scope, cid in existing:
            result = _get_collection(coll_name).query(
                query_embeddings=[vector], n_results=top_k
            )
            docs = (result.get("documents") or [[]])[0]
            metas = (result.get("metadatas") or [[]])[0]
            logger.info(
                "RAG 检索 collection=%s 返回 %d 块；来源: %s",
                coll_name, len(docs),
                [m.get("source") for m in metas if isinstance(m, dict)][:5],
            )
            for doc, meta in zip(docs, metas):
                source = meta.get("source") or "未知来源"
                section = meta.get("section") or ""
                if scope == SCOPE_SYSTEM:
                    scope_tag = "系统"
                else:
                    scope_tag = f"影院:{_resolve_cinema_name(cid) or cid}"
                origin = f"{source} / {section}" if section else source
                parts.append(
                    f"\n[{seq}] 来源: [{scope_tag}] {origin}\n{doc.strip()}"
                )
                seq += 1
    except Exception as exc:  # 检索失败不应把对话搞崩
        return f"知识库检索失败：{exc}"

    if seq == 1:
        return "知识库中未找到相关内容。"
    return "\n".join(parts)


__all__ = ["retrieve"]
