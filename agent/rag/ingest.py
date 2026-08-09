"""知识库灌入脚本：读取 Markdown 文档 → 切块 → 向量化 → 写入 Chroma。

用法（在 agent 仓库根目录）：
    python -m agent.rag.ingest                          # 灌入默认退票政策文档
    python -m agent.rag.ingest --file faq.md            # 指定知识库里的其他文件
    python -m agent.rag.ingest --strategy markdown      # 人工指定切块策略

跑完后可通过三种方式查看分块：
    1. 控制台打印每块概览（数量 / 字数 / 开头）
    2. 导出完整分块到 agent/rag/chunks_preview.md，逐字检查
    3. 检索自检：用测试 query 验证能否命中正确的块
"""
from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

# Windows 控制台默认 GBK，强制 UTF-8 以正确输出中文与符号
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from agent.rag.config import (
    CHROMA_DIR,
    CHUNK_OVERLAP,
    CHUNK_SIZE,
    COLLECTION_NAME,
    EMBEDDING_MODEL,
    KNOWLEDGE_DIR,
    MODEL_CACHE_DIR,
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
def embed(chunks: list[Chunk]) -> list[list[float]]:
    """用本地 fastembed 模型批量计算向量，返回 list[float] 列表。

    模型只加载一次，所有块批量计算；显式转 list 以兼容 Chroma。
    """
    from fastembed import TextEmbedding

    MODEL_CACHE_DIR.mkdir(parents=True, exist_ok=True)
    embedder = TextEmbedding(model_name=EMBEDDING_MODEL, cache_dir=str(MODEL_CACHE_DIR))
    texts = [c.text for c in chunks]
    vectors = list(embedder.embed(texts))
    return [v.tolist() for v in vectors]


# ---------------------------------------------------------------------------
# ④ 存储（先清空再灌，保证幂等）
# ---------------------------------------------------------------------------
def store(chunks: list[Chunk], vectors: list[list[float]]) -> None:
    """写入 Chroma 本地持久化库。显式传 embeddings，禁用 Chroma 默认模型。"""
    import chromadb

    CHROMA_DIR.mkdir(parents=True, exist_ok=True)
    client = chromadb.PersistentClient(path=str(CHROMA_DIR))
    collection = client.get_or_create_collection(COLLECTION_NAME)

    # 幂等：清空旧数据再灌，文档更新后重跑即为最新
    existing = collection.get()
    if existing and existing.get("ids"):
        collection.delete(ids=existing["ids"])

    collection.add(
        ids=[f"{c.source}#{c.index}" for c in chunks],
        embeddings=vectors,
        documents=[c.text for c in chunks],
        metadatas=[c.metadata for c in chunks],
    )


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
def self_check() -> None:
    import chromadb
    from fastembed import TextEmbedding

    client = chromadb.PersistentClient(path=str(CHROMA_DIR))
    collection = client.get_or_create_collection(COLLECTION_NAME)
    embedder = TextEmbedding(model_name=EMBEDDING_MODEL, cache_dir=str(MODEL_CACHE_DIR))

    print("=" * 50)
    print("检索自检（top-3，目标块进入前 3 即视为命中）")
    print("=" * 50)
    for query, expect_keyword in SELF_CHECK_QUERIES:
        q = list(embedder.embed([query]))[0].tolist()
        result = collection.query(query_embeddings=[q], n_results=3)
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
    path = KNOWLEDGE_DIR / file_name
    print(f"读取文档：{path}")
    text = read_file(path)
    print(f"原文 {len(text)} 字\n")

    chunks = split(text, source=file_name, strategy=strategy)
    print_overview(chunks)          # 查看方式 1

    export_preview(chunks)          # 查看方式 2

    print("计算向量并写入 Chroma ...")
    vectors = embed(chunks)
    store(chunks, vectors)
    print(f"已写入 {len(chunks)} 块 → {CHROMA_DIR}（collection: {COLLECTION_NAME}）\n")

    self_check()                    # 查看方式 3


def main() -> None:
    parser = argparse.ArgumentParser(description="知识库灌入脚本")
    parser.add_argument("--file", default=DEFAULT_FILE, help="知识库中的文件名")
    parser.add_argument("--strategy", default="markdown", help="切块策略（人工指定）")
    args = parser.parse_args()
    ingest(args.file, args.strategy)


if __name__ == "__main__":
    main()
