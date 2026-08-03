"""请求级上下文：前端 JWT 经 ContextVar 传到 SubAgent / Tools。"""
from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from typing import Iterator


_authorization: ContextVar[str | None] = ContextVar("agent_authorization", default=None)


def normalize_authorization(raw: str | None) -> str | None:
    """接受 ``Bearer <jwt>`` 或裸 JWT，统一成 Authorization 头值。"""
    if raw is None:
        return None
    value = raw.strip()
    if not value:
        return None
    if value.lower().startswith("bearer "):
        return f"Bearer {value[7:].strip()}"
    return f"Bearer {value}"


def get_authorization() -> str | None:
    return _authorization.get()


def has_authorization() -> bool:
    return bool(get_authorization())


@contextmanager
def use_authorization(raw: str | None) -> Iterator[str | None]:
    """在调用栈内设置 JWT，供 http 客户端与 Tools 读取。"""
    normalized = normalize_authorization(raw)
    token = _authorization.set(normalized)
    try:
        yield normalized
    finally:
        _authorization.reset(token)
