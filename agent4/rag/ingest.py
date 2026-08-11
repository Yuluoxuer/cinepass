"""知识库管理脚本：读取 Markdown 文档 → 切块 → 向量化 → 写入 Chroma。

用法（在 agent 仓库根目录）：
    python -m agent4.rag.ingest                          # 灌入默认退票政策文档
    python -m agent4.rag.ingest --file faq.md            # 灌入 knowledge/ 下其他文件（多文档可共存）
    python -m agent4.rag.ingest --delete faq.md          # 从向量库删除该文档的全部块（不动其他文档）
    python -m agent4.rag.ingest --list                   # 列出知识库中已有的源文档与块数

跑完后可通过三种方式查看分块：
    1. 控制台打印每块概览（数量 / 字数 / 开头）
    2. 导出完整分块到 agent4/rag/chunks_preview.md，逐字检查
    3. 检索自检：用测试 query 验证能否命中正确的块
"""
from __future__ import annotations

import argparse
import logging
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

logger = logging.getLogger(__name__)

# Windows 控制台默认 GBK，强制 UTF-8 以正确输出中文与符号
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from agent4.rag.config import (
    CHROMA_DIR,
    CHUNK_OVERLAP,
    CHUNK_SIZE,
    COLLECTION_NAME,
    KNOWLEDGE_DIR,
    PREVIEW_FILE,
)

DEFAULT_FILE = "refund_policy.md"

# 检索自检的测试 query → 期望命中的关键词（用于粗略判断是否命中正确块）
SELF_CHECK_QUERIES = [
    ("退票手续费多少", "退票时限"),
    ("退款多久到账", "到账时间"),
    ("可以改签几次", "改签规则"),
]


# ---------------------------------------------------------------------------
# 数据结构
# ---------------------------------------------------------------------------
@dataclass
class Chunk:
    """一个知识块：原文 + 溯源信息。"""

    text: str
    source: str                       # 来源文件名
    index: int                        # 在本文档中的块序号
    metadata: dict = field(default_factory=dict)


# ---------------------------------------------------------------------------
# ① 读取文件
# ---------------------------------------------------------------------------
def read_file(path: Path) -> str:
    """读取 Markdown 文档，返回纯文本。"""
    if not path.exists():
        raise FileNotFoundError(f"知识文档不存在：{path}")
    return path.read_text(encoding="utf-8")


# ---------------------------------------------------------------------------
# ② 切块（策略注册表：人工指定，当前仅实现 markdown）
# ---------------------------------------------------------------------------
def _markdown_split(text: str, source: str) -> list[Chunk]:
    """按 Markdown 标题层级切块：每个 `##` 章节独立成一块。

    用 MarkdownHeaderTextSplitter 严格按标题边界切，保证每块语义聚焦、
    章节标题自动写入 metadata（便于溯源与调试）。若某块超长，再用递归
    切块器二次细分，避免超出 chunk_size。
    """
    from langchain_text_splitters import (
        MarkdownHeaderTextSplitter,
        RecursiveCharacterTextSplitter,
    )

    header_splitter = MarkdownHeaderTextSplitter(
        headers_to_split_on=[("#", "h1"), ("##", "h2"), ("###", "h3")],
        strip_headers=False,  # 保留标题行在正文里，块内容自带上下文
    )
    # 超长块兜底细分
    fallback = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
    )

    chunks: list[Chunk] = []
    for doc in header_splitter.split_text(text):
        # 章节标题拼接，如 "一、退票时限"
        section = " / ".join(
            doc.metadata.get(key) for key in ("h1", "h2", "h3") if doc.metadata.get(key)
        )
        for piece in fallback.split_text(doc.page_content):
            if not piece.strip():
                continue
            index = len(chunks)
            chunks.append(
                Chunk(
                    text=piece,
                    source=source,
                    index=index,
                    metadata={
                        "source": source,
                        "chunk_index": index,
                        "section": section,
                    },
                )
            )
    return chunks


# 策略注册表：新增切块方式时在此注册，不改主流程
SPLITTERS: dict[str, Callable[[str, str], list[Chunk]]] = {
    "markdown": _markdown_split,
    # "recursive": ...,   # 预留：结构不清晰文档的兜底策略，暂未实现
}


def split(text: str, source: str, strategy: str) -> list[Chunk]:
    """按人工指定的策略切块。"""
    if strategy not in SPLITTERS:
        raise ValueError(
            f"未支持的切块策略：{strategy}，当前仅支持 {list(SPLITTERS)}"
        )
    return SPLITTERS[strategy](text, source)


# ---------------------------------------------------------------------------
# ③ 向量化
# ---------------------------------------------------------------------------
def _get_client():
    """单一 Chroma client 单例：ingest/retriever 全模块共用。

    避免「每次新建 PersistentClient 指向同一 sqlite 路径」造成锁竞争 / database is locked。
    """
    import chromadb

    CHROMA_DIR.mkdir(parents=True, exist_ok=True)
    return chromadb.PersistentClient(path=str(CHROMA_DIR))


def embed(chunks: list[Chunk]) -> list[list[float]]:
    """用共享 fastembed 模型批量计算向量，返回 list[float] 列表。

    复用 ``retriever._get_embedder`` 单例，避免每次灌入都重新加载模型。
    """
    from agent4.rag.retriever import _get_embedder

    embedder = _get_embedder()
    texts = [c.text for c in chunks]
    vectors = list(embedder.embed(texts))
    return [v.tolist() for v in vectors]


# ---------------------------------------------------------------------------
# ④ 存储（按来源文件覆盖，支持多文档共存）
# ---------------------------------------------------------------------------
def store(chunks: list[Chunk], vectors: list[list[float]], collection: str | None = None) -> None:
    """写入 Chroma 本地持久化库。显式传 embeddings，禁用 Chroma 默认模型。

    只删除「同一 source」的旧块再写入本次分块：不同文档的块保留，
    实现多文档共存；同一文档重跑即为最新（幂等更新）。
    """
    collection = collection or COLLECTION_NAME
    coll = _get_client().get_or_create_collection(collection)

    source = chunks[0].source if chunks else ""
    logger.info(
        "RAG 灌入 collection=%s source=%s 块数=%d",
        collection, source, len(chunks),
    )
    stale = coll.get(where={"source": source}) if source else {}
    if stale and stale.get("ids"):
        coll.delete(ids=stale["ids"])

    coll.add(
        ids=[f"{c.source}#{c.index}" for c in chunks],
        embeddings=vectors,
        documents=[c.text for c in chunks],
        metadatas=[c.metadata for c in chunks],
    )


# ---------------------------------------------------------------------------
# ⑤ 知识库管理：删除单文件 / 列出已有文档
# ---------------------------------------------------------------------------
def delete_file(file_name: str, collection: str | None = None) -> None:
    """删除知识库中指定来源文档的全部块（不影响其他文档）。"""
    collection = collection or COLLECTION_NAME
    if not CHROMA_DIR.exists():
        print(f"向量库不存在（{CHROMA_DIR}），无需删除。")
        return
    coll = _get_client().get_or_create_collection(collection)
    stale = coll.get(where={"source": file_name})
    ids = (stale or {}).get("ids") or []
    if ids:
        coll.delete(ids=ids)
        print(f"已从知识库删除「{file_name}」的 {len(ids)} 个分块。")
    else:
        print(f"知识库中没有「{file_name}」的分块。")


def list_sources_data(collection: str | None = None) -> list[dict[str, object]]:
    """列出知识库中已有的来源文档与分块数量（结构化返回，供 API 使用）。"""
    from collections import Counter

    if not CHROMA_DIR.exists():
        return []
    coll = _get_client().get_or_create_collection(collection or COLLECTION_NAME)
    data = coll.get(include=["metadatas"])
    counts = Counter(
        (m or {}).get("source", "?") for m in (data.get("metadatas") or [])
    )
    return [
        {"source": src, "chunkCount": n}
        for src, n in sorted(counts.items())
        if src != "?"
    ]


def list_sources(collection: str | None = None) -> None:
    """列出知识库中已有的来源文档与分块数量（控制台打印，CLI 用）。"""
    if not CHROMA_DIR.exists():
        print("向量库不存在，知识库为空。")
        return
    items = list_sources_data(collection)
    if not items:
        print("知识库为空（尚无分块）。")
        return
    total = sum(it["chunkCount"] for it in items)
    print(f"知识库共 {total} 个分块：")
    for it in items:
        print(f"  - {it['source']}: {it['chunkCount']} 块")


# ---------------------------------------------------------------------------
# ⑤′ 程序化灌入（供管理 API 使用，内容来自上传而非磁盘）
# ---------------------------------------------------------------------------
def ingest_content(
    content: str,
    source: str,
    strategy: str = "markdown",
    collection: str | None = None,
) -> int:
    """以传入内容切块 → 向量化 → 写入指定 collection。

    返回写入的分块数；内容为空或无可切分内容时抛 ``ValueError``，
    由调用方（API）转成 4xx 错误。
    """
    collection = collection or COLLECTION_NAME
    text = content or ""
    if not text.strip():
        raise ValueError("文档内容为空，无法写入知识库。")
    chunks = split(text, source=source, strategy=strategy)
    if not chunks:
        raise ValueError("文档无可切分内容，无法写入知识库。")
    vectors = embed(chunks)
    store(chunks, vectors, collection=collection)
    return len(chunks)


# ---------------------------------------------------------------------------
# 查看方式 1：控制台打印分块概览
# ---------------------------------------------------------------------------
def print_overview(chunks: list[Chunk]) -> None:
    print("=" * 50)
    print(f"分块结果（共 {len(chunks)} 块）")
    print("=" * 50)
    for c in chunks:
        section = c.metadata.get("section") or "(无章节)"
        preview = c.text.strip().replace("\n", " ")[:40]
        print(f"[块 {c.index}] {len(c.text)} 字 | {section}")
        print(f"  开头: {preview}")
    if chunks:
        sizes = [len(c.text) for c in chunks]
        print("=" * 50)
        print(
            f"字数分布: min={min(sizes)}  max={max(sizes)}  "
            f"平均={sum(sizes) // len(sizes)}"
        )
    print()


# ---------------------------------------------------------------------------
# 查看方式 2：导出完整分块到文件，逐字检查
# ---------------------------------------------------------------------------
def export_preview(chunks: list[Chunk]) -> None:
    lines = ["# 分块预览（完整内容，用于人工检查切块质量）", ""]
    for c in chunks:
        section = c.metadata.get("section") or "(无章节)"
        lines.append(f"## 块 {c.index}（{len(c.text)} 字）｜{c.source}｜{section}")
        lines.append("---")
        lines.append(c.text.strip())
        lines.append("")
    PREVIEW_FILE.write_text("\n".join(lines), encoding="utf-8")
    print(f"完整分块已导出：{PREVIEW_FILE}\n")


# ---------------------------------------------------------------------------
# 查看方式 3：检索自检，验证能否命中正确的块
# ---------------------------------------------------------------------------
def self_check(collection: str | None = None) -> None:
    from agent4.rag.retriever import _get_embedder

    coll = _get_client().get_or_create_collection(collection or COLLECTION_NAME)
    embedder = _get_embedder()

    print("=" * 50)
    print("检索自检（top-3，目标块进入前 3 即视为命中）")
    print("=" * 50)
    for query, expect_keyword in SELF_CHECK_QUERIES:
        q = list(embedder.embed([query]))[0].tolist()
        result = coll.query(query_embeddings=[q], n_results=3)
        docs = (result.get("documents") or [[]])[0]
        metas = (result.get("metadatas") or [[]])[0]
        # 目标块是否出现在 top-3
        hit_rank = next(
            (i for i, doc in enumerate(docs) if expect_keyword in doc), None
        )
        mark = "[OK]" if hit_rank is not None else "[MISS]"
        print(f"query「{query}」 {mark}")
        for i, (doc, meta) in enumerate(zip(docs, metas)):
            section = meta.get("section") or "(无章节)"
            star = " ← 目标" if i == hit_rank else ""
            print(f"  top{i+1} 块#{meta.get('chunk_index')} {section}{star}")
    print()


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def ingest(file_name: str, strategy: str) -> None:
    # 优先读系统作用域目录 knowledge/system/，不存在则兼容旧顶层 knowledge/
    path = KNOWLEDGE_DIR / "system" / file_name
    if not path.exists():
        legacy = KNOWLEDGE_DIR / file_name
        if legacy.exists():
            path = legacy
    print(f"读取文档：{path}")
    text = read_file(path)
    print(f"原文 {len(text)} 字\n")

    chunks = split(text, source=file_name, strategy=strategy)
    print_overview(chunks)          # 查看方式 1

    export_preview(chunks)          # 查看方式 2

    print("计算向量并写入 Chroma ...")
    vectors = embed(chunks)
    store(chunks, vectors, collection=COLLECTION_NAME)
    print(f"已写入 {len(chunks)} 块 → {CHROMA_DIR}（collection: {COLLECTION_NAME}）\n")

    self_check()                    # 查看方式 3


def main() -> None:
    parser = argparse.ArgumentParser(description="知识库管理：灌入 / 删除 / 列表")
    parser.add_argument("--file", default=DEFAULT_FILE, help="knowledge/ 下的文件名（灌入用）")
    parser.add_argument("--strategy", default="markdown", help="切块策略（人工指定）")
    parser.add_argument("--delete", metavar="FILE", help="从知识库删除该文档的全部块")
    parser.add_argument("--list", action="store_true", help="列出知识库已有的源文档与块数")
    args = parser.parse_args()
    if args.list:
        list_sources()
        return
    if args.delete:
        delete_file(args.delete)
        return
    ingest(args.file, args.strategy)


if __name__ == "__main__":
    main()
