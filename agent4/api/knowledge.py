"""知识库管理 API：上传 / 删除 / 列表（作用域 + 角色权限）。

权限模型（staff / admin）：
- ``admin``：读取 / 删除覆盖全部知识库（默认系统库，``?cinemaId=`` 指定任意影院库）；
  上传**只能**写入系统知识库（影院级知识由对应影院 staff 上传）
- ``staff``：只管理自己影院的知识库（scope=cinema，cinemaId 取自 JWT，
  指定其他影院一律 403）
- 其它角色 / 匿名 / 验签失败：一律 403 / 401

错误统一返回信封 ``{code, message, data}``：前端 client.ts 只认信封，
裸 HTTPException 的 ``{"detail": ...}`` 无法被解析为可读错误。
"""
from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Depends, File, Query, UploadFile
from fastapi.responses import JSONResponse

from agent4.api.contract import (
    KnowledgeChunkListEnvelope,
    KnowledgeChunkVO,
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
from agent4.rag.ingest import get_chunks_for_file, ingest_content, list_sources_data

router = APIRouter(prefix="/agent4/knowledge", tags=["agent4-knowledge"])

# Chroma 写操作串行化（同库并发写有锁竞争），上传 / 删除共用一把进程级锁
_WRITE_LOCK = asyncio.Lock()


def _envelope(status: int, message: str, data: Any = None) -> JSONResponse:
    """返回与前端一致的信封形错误 / 响应。"""
    return JSONResponse(
        status_code=status,
        content={"code": status, "message": message, "data": data},
    )


def _resolve_scope(
    claims: dict, cinema_id_param: str | None = None
) -> tuple[str, str | None] | None:
    """按角色解析 ``(scope, cinema_id)``；无权时返回 None（调用方转 403）。

    - admin → 默认系统知识库；指定 ``cinema_id_param`` 时管理该影院知识库
    - staff → 只能自己影院知识库（JWT 必须带 cinemaId，指定其他影院视为无权）
    - 其它 → None
    """
    role = claims.get("role") or (claims.get("roles") or [None])[0]
    if role == "admin":
        if cinema_id_param:
            return SCOPE_CINEMA, cinema_id_param
        return SCOPE_SYSTEM, None
    if role == "staff":
        cid = claims.get("cinemaId")
        if not cid:
            return None  # staff 未绑定影院，无法定位知识库
        if cinema_id_param and cinema_id_param != str(cid):
            return None  # staff 不得跨影院操作
        return SCOPE_CINEMA, str(cid)
    return None


def _collection_or_400(scope: str, cinema_id: str | None):
    """解析 collection 名；cinemaId 非法时返回 400 信封而非 500。"""
    try:
        return collection_name(scope, cinema_id), None
    except ValueError as exc:
        return None, _envelope(400, str(exc))


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
    cinemaId: str | None = Query(None),
    authorization: str | None = Depends(get_authorization),
) -> KnowledgeUploadEnvelope | JSONResponse:
    """上传一个 Markdown 文档到当前账号所属作用域的知识库。

    admin 只能上传系统级知识（不接受 ``cinemaId``）；影院级知识由对应影院 staff 上传。
    """
    claims = decode_jwt_claims(authorization)
    if not claims:
        return _envelope(401, "登录已失效，请重新登录。")
    role = claims.get("role") or (claims.get("roles") or [None])[0]
    if role == "admin" and cinemaId:
        return _envelope(403, "管理员只能上传系统级知识；影院级知识请由对应影院 staff 上传。")
    scope_res = _resolve_scope(claims, cinemaId)
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

    coll, err = _collection_or_400(scope, cinema_id)
    if err:
        return err
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
    cinemaId: str | None = Query(None),
    authorization: str | None = Depends(get_authorization),
) -> KnowledgeFileListEnvelope | JSONResponse:
    """列出当前账号作用域知识库中的文档（source）与分块数。

    admin 可通过 ``?cinemaId=`` 查看任意影院的知识库（默认系统库）。
    """
    claims = decode_jwt_claims(authorization)
    if not claims:
        return _envelope(401, "登录已失效，请重新登录。")
    scope_res = _resolve_scope(claims, cinemaId)
    if scope_res is None:
        return _envelope(403, "当前账号无知识库管理权限。")
    scope, cinema_id = scope_res

    coll, err = _collection_or_400(scope, cinema_id)
    if err:
        return err
    items = await asyncio.to_thread(list_sources_data, coll)
    return KnowledgeFileListEnvelope(
        data=[_to_vo(scope, cinema_id, it) for it in items]
    )


@router.delete("/files/{filename}", response_model=KnowledgeDeleteEnvelope)
async def delete_knowledge_file(
    filename: str,
    cinemaId: str | None = Query(None),
    authorization: str | None = Depends(get_authorization),
) -> KnowledgeDeleteEnvelope | JSONResponse:
    """删除当前账号作用域知识库中的指定文档（向量块 + 磁盘原文）。

    admin 可通过 ``?cinemaId=`` 删除任意影院知识库中的文档（默认系统库）。
    """
    claims = decode_jwt_claims(authorization)
    if not claims:
        return _envelope(401, "登录已失效，请重新登录。")
    scope_res = _resolve_scope(claims, cinemaId)
    if scope_res is None:
        return _envelope(403, "当前账号无知识库管理权限。")
    scope, cinema_id = scope_res

    # DELETE 的 filename 来自 URL 路径，不可信，必须严格校验（不做 basename 清洗）
    if not is_valid_knowledge_filename(filename):
        return _envelope(400, "文件名不合法。")

    coll, err = _collection_or_400(scope, cinema_id)
    if err:
        return err
    target_path = knowledge_dir(scope, cinema_id) / filename
    file_existed = target_path.exists()
    try:
        async with _WRITE_LOCK:
            deleted = await asyncio.to_thread(_delete_chroma_file, filename, coll)
        target_path.unlink(missing_ok=True)
    except Exception as exc:
        return _envelope(500, f"删除失败：{exc}")

    # 向量块与磁盘原文都不存在 = 空删，明确告知，杜绝「假成功」
    if deleted == 0 and not file_existed:
        return _envelope(404, f"当前知识库中没有「{filename}」。")
    return KnowledgeDeleteEnvelope(
        data={"filename": filename, "deletedChunks": deleted},
    )


@router.get("/files/{filename}/chunks", response_model=KnowledgeChunkListEnvelope)
async def list_file_chunks(
    filename: str,
    cinemaId: str | None = Query(None),
    authorization: str | None = Depends(get_authorization),
) -> KnowledgeChunkListEnvelope | JSONResponse:
    """查看指定文档的全部切块明细（章节、内容、字数），供管理后台预览切分效果。

    admin 可通过 ``?cinemaId=`` 查看任意影院知识库中的文档切块（默认系统库）。
    """
    claims = decode_jwt_claims(authorization)
    if not claims:
        return _envelope(401, "登录已失效，请重新登录。")
    scope_res = _resolve_scope(claims, cinemaId)
    if scope_res is None:
        return _envelope(403, "当前账号无知识库管理权限。")
    scope, cinema_id = scope_res

    if not is_valid_knowledge_filename(filename):
        return _envelope(400, "文件名不合法。")

    coll, err = _collection_or_400(scope, cinema_id)
    if err:
        return err

    try:
        raw = await asyncio.to_thread(get_chunks_for_file, filename, coll)
    except Exception as exc:
        return _envelope(500, f"查询切块失败：{exc}")

    if not raw:
        return _envelope(404, f"当前知识库中没有「{filename}」的切块。")

    return KnowledgeChunkListEnvelope(
        data=[
            KnowledgeChunkVO(
                id=str(c.get("id", "")),
                chunkIndex=int(c.get("chunkIndex", 0)),
                section=str(c.get("section", "")),
                text=str(c.get("text", "")),
                charCount=int(c.get("charCount", 0)),
            )
            for c in raw
        ]
    )


def _rollback_file(path: Path) -> None:
    """向量化失败时清理磁盘半成品。"""
    try:
        path.unlink(missing_ok=True)
    except OSError:
        pass


__all__ = ["router"]
