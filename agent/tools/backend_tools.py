"""调用票务中台的示例 Tool；JWT 经 ``agent.http`` 自动透传。"""
from __future__ import annotations

import json

import httpx
from langchain.tools import tool

from agent.http import backend_url, get
from agent.request_context import has_authorization


def _format_result(payload: object) -> str:
    """把中台 ``Result<T>`` JSON 压成可读摘要。"""
    if not isinstance(payload, dict):
        return str(payload)
    code = payload.get("code")
    message = payload.get("message") or ""
    data = payload.get("data")
    if code not in (0, 200, None) and data is None:
        return f"中台业务失败 code={code}: {message or payload}"
    if isinstance(data, dict):
        user_id = data.get("userId") or data.get("user_id") or "?"
        nickname = data.get("nickname") or "?"
        role = data.get("role") or "?"
        phone = data.get("phone") or "-"
        return f"当前用户：{nickname}（id={user_id}，role={role}，phone={phone}）"
    return json.dumps(payload, ensure_ascii=False)


@tool
async def get_current_user() -> str:
    """查询当前登录用户（中台 GET /api/v1/auth/me）。需前端请求头携带 Authorization JWT。"""
    if not has_authorization():
        return "未携带 JWT，无法调用中台。请在请求头加上 Authorization: Bearer <token>。"

    try:
        payload = await get(backend_url("/api/v1/auth/me"))
    except httpx.HTTPStatusError as exc:
        body = (exc.response.text or "")[:300]
        return f"中台 HTTP {exc.response.status_code}: {body or exc}"
    except httpx.HTTPError as exc:
        return f"调用中台失败: {exc}"

    return _format_result(payload)
