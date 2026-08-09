"""agent3 监督者图的状态。"""
from __future__ import annotations

from typing import Annotated, Any, TypedDict


def _replace(left, right):
    """每轮/每节点覆盖，避免跨轮无限追加。"""
    return right if right is not None else left


class Agent3State(TypedDict, total=False):
    message: str
    # 跨轮历史（user/assistant），由端点存取
    history: Annotated[list[dict[str, str]], _replace]
    # 本轮各子 agent 回复片段累积
    reply_parts: Annotated[list[str], _replace]
    # 本轮累积的动态卡片
    cards: Annotated[list[dict[str, Any]], _replace]
    # 购票草稿（跨轮持久化）
    bookingdraft: dict[str, Any]
    sessionId: str
    authorization: str | None
    latitude: float | None
    longitude: float | None
    # 监督者路由决策
    next: str
    # 循环步数（防死循环）
    steps: Annotated[int, _replace]
    # 上一个子 agent 的输出（供监督者判断）
    last_reply: Annotated[str, _replace]
