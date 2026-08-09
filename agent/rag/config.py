"""RAG 共享配置：ingest 与 retriever 必须使用同一套常量。

集中定义，保证「灌入用的 collection / 模型」与「检索用的」永远一致，
否则向量对不上、检索不到数据。
"""
from __future__ import annotations

from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]          # 仓库根目录
KNOWLEDGE_DIR = ROOT_DIR / "knowledge"                  # 知识文档目录
RAG_DIR = ROOT_DIR / "agent" / "rag"                    # 本模块目录
CHROMA_DIR = RAG_DIR / "chroma_db"                      # 向量库持久化目录
PREVIEW_FILE = RAG_DIR / "chunks_preview.md"            # 分块预览导出文件

COLLECTION_NAME = "cinema_knowledge"
EMBEDDING_MODEL = "BAAI/bge-small-zh-v1.5"

# 本地 embedding 模型缓存目录（项目内，便于离线/内网部署；勿提交 git）
MODEL_CACHE_DIR = RAG_DIR / "models"

# 切块参数（ingest 用）
CHUNK_SIZE = 500
CHUNK_OVERLAP = 50

# 检索返回的块数量（retriever 用）
TOP_K = 3
