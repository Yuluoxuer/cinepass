"""本地示例 Tool（LangChain ``@tool``）；出站请求经 ``agent.http`` 自动带 JWT。"""
from __future__ import annotations

from datetime import datetime, timezone

from langchain.tools import tool

from agent.request_context import get_authorization


@tool
def get_current_time(tz_name: str = "UTC") -> str:
    """返回当前时间字符串。tz_name 支持 UTC 或 local。"""
    if tz_name.upper() == "UTC":
        now = datetime.now(timezone.utc)
        return now.strftime("%Y-%m-%d %H:%M:%S UTC")
    now = datetime.now().astimezone()
    return now.strftime("%Y-%m-%d %H:%M:%S %Z")


@tool
def echo_text(text: str) -> str:
    """原样回显文本，用于验证 Tool 调用链路。"""
    return text


@tool
def auth_status() -> str:
    """查看当前请求是否带有前端 JWT（脱敏显示）。"""
    auth = get_authorization()
    if not auth:
        return "未携带 JWT（请求头无 Authorization）"
    token = auth[7:].strip() if auth.lower().startswith("bearer ") else auth
    if len(token) <= 12:
        masked = "***"
    else:
        masked = f"{token[:6]}...{token[-4:]}"
    return f"已携带 Authorization: Bearer {masked}"


@tool
async def authorized_get(url: str) -> str:
    """GET 外部 URL，自动附带当前 JWT；返回响应文本摘要。"""
    from agent.http import get

    data = await get(url)
    return str(data)
