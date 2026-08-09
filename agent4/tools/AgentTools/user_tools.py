"""用户 Tools — 调用票务中台用户信息接口；JWT 经 Http2BackendTools.http 自动透传。"""
from __future__ import annotations

import httpx
from langchain.tools import tool

from agent4.tools.Http2BackendTools.http import backend_url, get
from agent4.tools.Http2BackendTools.auth import has_authorization


@tool
async def get_current_user() -> str:
    """查询当前登录用户（中台 GET /api/v1/auth/me）。需前端请求头携带 Authorization JWT。返回原始 JSON 数据。"""
    if not has_authorization():
        return "未携带 JWT，无法调用中台。请在请求头加上 Authorization: Bearer <token>。"
    try:
        payload = await get(backend_url("/api/v1/auth/me"))
    except httpx.HTTPStatusError as exc:
        body = (exc.response.text or "")[:300]
        return f"中台 HTTP {exc.response.status_code}: {body or exc}"
    except httpx.HTTPError as exc:
        return f"调用中台失败: {exc}"
    return str(payload)
