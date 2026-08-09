"""工具入参安全校验：URL 路径片段 ID 白名单。

中台 ID 形如 ``s900`` / ``m100`` / ``c1`` / ``o900`` / ``sm1:6:7``，
限制为字母数字与常见分隔符，杜绝 ``../``、``/`` 等路径遍历字符。
"""
from __future__ import annotations

import re

# 允许字母、数字、下划线、冒号、连字符（覆盖 showId/cinemaId/movieId/orderId/lockId/seatId）
_ID_PATTERN = re.compile(r"^[A-Za-z0-9_:\-]+$")


def validate_id(value: str, name: str = "id") -> str:
    """校验单个路径片段 ID；非法时抛出 ValueError。"""
    if not value or not _ID_PATTERN.match(value):
        raise ValueError(f"非法的{name}：{value!r}")
    return value


def validate_seat_ids(value: str) -> str:
    """校验逗号分隔的座位 ID 列表（形如 ``sm1:6:7,sm1:6:8``）。"""
    parts = [p.strip() for p in value.split(",")]
    if not parts or any(not p or not _ID_PATTERN.match(p) for p in parts):
        raise ValueError(f"非法的座位 ID：{value!r}")
    return value


__all__ = ["validate_id", "validate_seat_ids"]
