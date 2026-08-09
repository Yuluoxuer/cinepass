"""知识库管理 API：上传 / 删除 / 列表（作用域 + 角色权限）。

权限模型（staff / admin）：
- ``admin``：只管理系统知识库（scope=system，如购票流程、平台规则）
- ``staff``：只管理自己影院的知识库（scope=cinema，cinemaId 取自 JWT）
- 其它角色 / 匿名 / 验签失败：一律 403 / 401

错误统一返回信封 ``{code, message, data}``：前端 client.ts 只认信封，
裸 HTTPException 的 ``{"detail": ...}`` 无法被解析为可读错误。
"""
from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Depends, File, UploadFile
from fastapi.responses import JSONResponse

from agent4.api.contract import (
    KnowledgeDeleteEnvelope,
    KnowledgeFileListEnvelope,
    KnowledgeFileVO,
    KnowledgeUploadEnvelope,
    decode_jwt_claims,
)
from agent4.api.deps import get_authorization
from agent4.rag.config import (
    MAX_KB_FILE_SIZE,
    SCOPE_CINEMA,
    SCOPE_SYSTEM,
    collection_name,
    is_valid_knowledge_filename,
    knowledge_dir,
)
from agent4.rag.ingest import delete_file as _delete_chroma_file
from agent4.rag.ingest import ingest_content, list_sources_data

router = APIRouter(prefix="/agent4/knowledge", tags=["agent4-knowledge"])

# Chroma 写操作串行化（同库并发写有锁竞争），上传 / 删除共用一把进程级锁
_WRITE_LOCK = asyncio.Lock()


def _envelope(status: int, message: str, data: Any = None) -> JSONResponse:
    """返回与前端一致的信封形错误 / 响应。"""
    return JSONResponse(
        status_code=status,
        content={"code": status, "message": message, "data": data},
    )


def _resolve_scope(claims: dict) -> tuple[str, str | None] | None:
    """按角色解析 ``(scope, cinema_id)``；无权时返回 None（调用方转 403）。

    - admin → 系统知识库
    - staff → 自己影院知识库（JWT 必须带 cinemaId）
    - 其它 → None
    """
    role = claims.get("role") or (claims.get("roles") or [None])[0]
    if role == "admin":
        return SCOPE_SYSTEM, None
    if role == "staff":
        cid = claims.get("cinemaId")
        if not cid:
            return None  # staff 未绑定影院，无法定位知识库
        return SCOPE_CINEMA, str(cid)
    return None


def _mtime_iso(path: Path) -> str | None:
    """磁盘文件的修改时间（ISO8601 UTC）；文件缺失 / 读取失败返回 None。"""
    try:
        return datetime.fromtimestamp(path.stat().st_mtime, tz=timezone.utc).isoformat()
    except Exception:
        return None


def _to_vo(scope: str, cinema_id: str | None, src: dict) -> KnowledgeFileVO:
    """把 Chroma 里的 source 记录映射为前端 VO，补充磁盘 mtime。"""
    filename = str(src.get("source") or "")
    return KnowledgeFileVO(
        filename=filename,
        chunkCount=int(src.get("chunkCount") or 0),
        scope=scope,
        cinemaId=cinema_id,
        updatedAt=_mtime_iso(knowledge_dir(scope, cinema_id) / filename),
    )


@router.post("/files", response_model=KnowledgeUploadEnvelope)
async def upload_knowledge_file(
    file: UploadFile = File(...),
    authorization: str | None = Depends(get_authorization),
) -> KnowledgeUploadEnvelope | JSONResponse:
    """上传一个 Markdown 文档到当前账号所属作用域的知识库。"""
    claims = decode_jwt_claims(authorization)
    if not claims:
        return _envelope(401, "登录已失效，请重新登录。")
    scope_res = _resolve_scope(claims)
    if scope_res is None:
        return _envelope(403, "当前账号无知识库管理权限。")
    scope, cinema_id = scope_res

    # 取 basename（浏览器可能带 C:\\fakepath\\ 前缀），再走白名单校验
    filename = Path(file.filename or "").name
    if not is_valid_knowledge_filename(filename):
        return _envelope(
            400, "文件名不合法：仅支持 .md/.markdown 文档，文件名不能包含路径分隔符或控制字符。"
        )

    raw = await file.read(MAX_KB_FILE_SIZE + 1)
    if len(raw) > MAX_KB_FILE_SIZE:
        return _envelope(400, "文件过大，单文件上限 1MB。")
    try:
        content = raw.decode("utf-8")
    except UnicodeDecodeError:
        return _envelope(400, "文件编码不是 UTF-8，请使用 UTF-8 编码的 Markdown 文档。")
    if not content.strip():
        return _envelope(400, "文档内容为空，无法写入知识库。")

    coll = collection_name(scope, cinema_id)
    target_dir = knowledge_dir(scope, cinema_id)
    target_path = target_dir / filename

    # 先写盘（供溯源/重新灌入），再向量化；向量化失败回滚删除磁盘文件，避免半成品
    try:
        target_dir.mkdir(parents=True, exist_ok=True)
        target_path.write_text(content, encoding="utf-8")
    except OSError as exc:
        return _envelope(500, f"保存原文失败：{exc}")

    try:
        async with _WRITE_LOCK:
            chunk_count = await asyncio.to_thread(
                ingest_content, content, filename, "markdown", coll
            )
    except ValueError as exc:
        _rollback_file(target_path)
        return _envelope(400, str(exc))
    except Exception as exc:
        _rollback_file(target_path)
        return _envelope(500, f"知识库写入失败：{exc}")

    return KnowledgeUploadEnvelope(
        data=KnowledgeFileVO(
            filename=filename,
            chunkCount=chunk_count,
            scope=scope,
            cinemaId=cinema_id,
            updatedAt=_mtime_iso(target_path),
        )
    )


@router.get("/files", response_model=KnowledgeFileListEnvelope)
async def list_knowledge_files(
    authorization: str | None = Depends(get_authorization),
) -> KnowledgeFileListEnvelope | JSONResponse:
    """列出当前账号作用域知识库中的文档（source）与分块数。"""
    claims = decode_jwt_claims(authorization)
    if not claims:
        return _envelope(401, "登录已失效，请重新登录。")
    scope_res = _resolve_scope(claims)
    if scope_res is None:
        return _envelope(403, "当前账号无知识库管理权限。")
    scope, cinema_id = scope_res

    coll = collection_name(scope, cinema_id)
    items = await asyncio.to_thread(list_sources_data, coll)
    return KnowledgeFileListEnvelope(
        data=[_to_vo(scope, cinema_id, it) for it in items]
    )


@router.delete("/files/{filename}", response_model=KnowledgeDeleteEnvelope)
async def delete_knowledge_file(
    filename: str,
    authorization: str | None = Depends(get_authorization),
) -> KnowledgeDeleteEnvelope | JSONResponse:
    """删除当前账号作用域知识库中的指定文档（向量块 + 磁盘原文）。"""
    claims = decode_jwt_claims(authorization)
    if not claims:
        return _envelope(401, "登录已失效，请重新登录。")
    scope_res = _resolve_scope(claims)
    if scope_res is None:
        return _envelope(403, "当前账号无知识库管理权限。")
    scope, cinema_id = scope_res

    # DELETE 的 filename 来自 URL 路径，不可信，必须严格校验（不做 basename 清洗）
    if not is_valid_knowledge_filename(filename):
        return _envelope(400, "文件名不合法。")

    coll = collection_name(scope, cinema_id)
    target_path = knowledge_dir(scope, cinema_id) / filename
    try:
        async with _WRITE_LOCK:
            await asyncio.to_thread(_delete_chroma_file, filename, coll)
        target_path.unlink(missing_ok=True)
    except Exception as exc:
        return _envelope(500, f"删除失败：{exc}")

    return KnowledgeDeleteEnvelope(data={"filename": filename})


def _rollback_file(path: Path) -> None:
    """向量化失败时清理磁盘半成品。"""
    try:
        path.unlink(missing_ok=True)
    except OSError:
        pass


__all__ = ["router"]
