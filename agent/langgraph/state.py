"""LangGraph 对话状态。"""
from __future__ import annotations

import operator
from typing import Annotated, TypedDict


def _replace(left: list[str] | None, right: list[str] | None) -> list[str]:
    """每轮覆盖 events，避免 checkpoint 跨轮无限追加。"""
    if right is None:
        return left or []
    return right


class GraphState(TypedDict, total=False):
    message: str
    # 经 Checkpointer 按 thread_id 持久化；每轮节点用 operator.add 追加本轮 user/assistant
    history: Annotated[list[dict[str, str]], operator.add]
    authorization: str | None
    latitude: float | None
    longitude: float | None
    route: str
    reply: str
    events: Annotated[list[str], _replace]
