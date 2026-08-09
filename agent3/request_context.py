"""请求级上下文：前端 JWT 经 ContextVar 传到 SubAgent / Tools。"""
from __future__ import annotations

from contextlib import contextmanager
from contextvars import ContextVar
from typing import Iterator


_authorization: ContextVar[str | None] = ContextVar("agent_authorization", default=None)

# 用户当前位置（GCJ-02），由后端端点注入，Tools 读取（如 searchCinemas 未显式传经纬度时兜底）
_location: ContextVar[tuple[float, float] | None] = ContextVar("agent_location", default=None)


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


def get_location() -> tuple[float, float] | None:
    """返回当前用户位置 (latitude, longitude)；未注入时返回 None。"""
    return _location.get()


@contextmanager
def use_location(latitude: float | None, longitude: float | None) -> Iterator[tuple[float, float] | None]:
    """在调用栈内设置用户位置，供 Tools（如 searchCinemas）在未显式传经纬度时兜底。"""
    value: tuple[float, float] | None = None
    if latitude is not None and longitude is not None:
        value = (float(latitude), float(longitude))
    token = _location.set(value)
    try:
        yield value
    finally:
        _location.reset(token)
