"""LangGraph 对话状态。"""
from __future__ import annotations

import operator
from typing import Annotated, Any, TypedDict


def _replace(left: list[str] | None, right: list[str] | None) -> list[str]:
    """每轮覆盖 events，避免 checkpoint 跨轮无限追加。"""
    if right is None:
        return left or []
    return right


def _replace_any(left: Any, right: Any) -> Any:
    """每轮覆盖（通用版）。"""
    return right if right is not None else left


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
    # ---------- 动态卡片（电影/影院/场次列表） ----------
    cards: Annotated[list[dict[str, Any]], _replace_any]
    # ---------- BookingDraft 流程扩展 ----------
    intent: str
    bookingdraft: dict[str, Any]
    draft_complete: bool
    missing_fields: list[str]
    sessionId: str
