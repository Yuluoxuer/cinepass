"""调用票务中台的 Helper Tool；JWT 经 ``agent.http`` 自动透传。"""
from __future__ import annotations

from langchain.tools import tool

from agent.http import backend_url, get
from agent.tools._common import require_auth, safe_api_call


def _format_user(data: object) -> str:
    """把中台用户信息格式化为可读摘要。"""
    if not isinstance(data, dict):
        return str(data)
    user_id = data.get("userId") or data.get("user_id") or "?"
    nickname = data.get("nickname") or "?"
    role = data.get("role") or "?"
    phone = data.get("phone") or "-"
    return f"当前用户：{nickname}（id={user_id}，role={role}，phone={phone}）"


@tool("getCurrentUser")
async def get_current_user() -> str:
    """查询当前登录用户（中台 GET /auth/me）。需前端请求头携带 Authorization JWT。"""
    auth_err = require_auth("未携带 JWT，无法调用中台。请在请求头加上 Authorization: Bearer <token>。")
    if auth_err:
        return auth_err

    data = await safe_api_call(
        get(backend_url("/auth/me"), timeout=5.0),
        domain="用户",
    )
    if isinstance(data, str):
        return data
    return _format_user(data)
