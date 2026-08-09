"""RAG 共享配置：ingest 与 retriever 必须使用同一套常量。

集中定义，保证「灌入用的 collection / 模型」与「检索用的」永远一致，
否则向量对不上、检索不到数据。
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]          # 仓库根目录
KNOWLEDGE_DIR = ROOT_DIR / "knowledge"                  # 知识文档目录（与 agent/ 共享）
RAG_DIR = ROOT_DIR / "agent4" / "rag"                   # 本模块目录
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

# 知识库作用域（scope）：system=系统知识（admin 管理）；cinema=某影院知识（staff 管理）
SCOPE_SYSTEM = "system"
SCOPE_CINEMA = "cinema"
SCOPES = (SCOPE_SYSTEM, SCOPE_CINEMA)

# 单个知识文档上传大小上限（字节）
MAX_KB_FILE_SIZE = 1_000_000

# 合法文件名：不允许路径分隔符（/、\\）与控制字符，允许中文等任意非空 basename，
# 以 .md / .markdown 结尾，≤128 字符。
_FILENAME_RE = re.compile(r"^[^/\\\x00-\x1f]{1,128}\.(?:md|markdown)$")


def collection_name(scope: str, cinema_id: str | None = None) -> str:
    """作用域 → Chroma collection 名。ingest 与 retriever 必须一致。

    - ``system`` → 基础库 ``cinema_knowledge``（沿用现有数据）
    - ``cinema`` → ``cinema_knowledge_cinema_{cinemaId}``，按影院拆分，互不串扰
    """
    scope = (scope or SCOPE_SYSTEM).strip().lower()
    if scope == SCOPE_SYSTEM:
        return COLLECTION_NAME
    if scope == SCOPE_CINEMA:
        if not cinema_id or not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", str(cinema_id)):
            raise ValueError(f"非法影院ID，无法定位影院知识库：{cinema_id!r}")
        return f"cinema_knowledge_cinema_{cinema_id}"
    raise ValueError(f"未知知识库作用域：{scope!r}，当前支持 {list(SCOPES)}")


def knowledge_dir(scope: str, cinema_id: str | None = None) -> Path:
    """作用域 → 磁盘原文目录。

    - ``system`` → ``knowledge/system/``
    - ``cinema`` → ``knowledge/cinema_{cinemaId}/``
    """
    scope = (scope or SCOPE_SYSTEM).strip().lower()
    if scope == SCOPE_SYSTEM:
        return KNOWLEDGE_DIR / "system"
    if scope == SCOPE_CINEMA:
        if not cinema_id or not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", str(cinema_id)):
            raise ValueError(f"非法影院ID，无法定位影院知识库：{cinema_id!r}")
        return KNOWLEDGE_DIR / f"cinema_{cinema_id}"
    raise ValueError(f"未知知识库作用域：{scope!r}，当前支持 {list(SCOPES)}")


def is_valid_knowledge_filename(name: str | None) -> bool:
    """校验知识文档文件名：仅接受纯文件名（无路径分量），限制后缀与长度。

    上传与 DELETE 都要走此校验——DELETE 的 filename 来自 URL 路径，不可信，
    否则 ``knowledge_dir / filename`` 可能路径穿越。
    """
    if not name or not isinstance(name, str):
        return False
    # 拒绝含路径分量的输入（..、/、\\、绝对路径）
    if Path(name).name != name or name in (".", ".."):
        return False
    return bool(_FILENAME_RE.match(name))


def ensure_offline_if_cached() -> None:
    """模型已本地缓存时离线加载，避免每次联网抖动导致 SSL 错误。

    fastembed 每次初始化都会尝试访问 HuggingFace 校验/下载模型；
    缓存存在时置 HF_HUB_OFFLINE=1 可跳过联网。首次无缓存时不设置，保持在线下载。
    """
    import os

    if MODEL_CACHE_DIR.exists() and any(MODEL_CACHE_DIR.iterdir()):
        os.environ.setdefault("HF_HUB_OFFLINE", "1")
