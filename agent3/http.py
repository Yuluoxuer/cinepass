"""出站 HTTP：自动附带当前请求的 Authorization（JWT）。"""
from __future__ import annotations

from typing import Any

import httpx

from agent3.request_context import get_authorization
from agent3.settings import get_agent_settings


def backend_url(path: str) -> str:
    """把相对路径拼到 ``BACKEND_BASE_URL``（如 ``/api/v1/auth/me``）。"""
    base = get_agent_settings().backend_base_url.rstrip("/")
    if not path.startswith("/"):
        path = f"/{path}"
    return f"{base}{path}"


def _merge_auth_headers(headers: dict[str, str] | None) -> dict[str, str]:
    merged = dict(headers or {})
    # 调用方显式传入时不覆盖
    if any(k.lower() == "authorization" for k in merged):
        return merged
    auth = get_authorization()
    if auth:
        merged["Authorization"] = auth
    return merged


async def request(
    method: str,
    url: str,
    *,
    params: dict[str, Any] | None = None,
    json: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float | None = None,
) -> Any:
    """发起 HTTP 请求；若上下文有 JWT 则写入 Authorization。"""
    settings = get_agent_settings()
    async with httpx.AsyncClient(timeout=timeout or settings.http_timeout_s) as client:
        resp = await client.request(
            method.upper(),
            url,
            params=params,
            json=json,
            headers=_merge_auth_headers(headers),
        )
        resp.raise_for_status()
        if not resp.content:
            return None
        ctype = resp.headers.get("content-type", "")
        if "application/json" in ctype:
            return resp.json()
        return resp.text


async def get(url: str, **kwargs: Any) -> Any:
    return await request("GET", url, **kwargs)


async def post(url: str, **kwargs: Any) -> Any:
    return await request("POST", url, **kwargs)


async def delete(url: str, **kwargs: Any) -> Any:
    return await request("DELETE", url, **kwargs)
