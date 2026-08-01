"""请求依赖：透传用户 JWT，供 Tools 回调中台。"""
from __future__ import annotations

from dataclasses import dataclass

from fastapi import Header, Request


@dataclass
class RequestContext:
    authorization: str | None
    request_id: str | None


async def get_request_context(
    request: Request,
    authorization: str | None = Header(default=None),
) -> RequestContext:
    return RequestContext(
        authorization=authorization,
        request_id=request.headers.get("X-Request-Id"),
    )
