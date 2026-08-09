"""鉴权依赖：从请求头取出前端 JWT。"""
from __future__ import annotations

from fastapi import Header


async def get_authorization(
    authorization: str | None = Header(
        default=None,
        alias="Authorization",
        description="Bearer <jwt>，将透传给 SubAgent 出站请求",
    ),
) -> str | None:
    return authorization
