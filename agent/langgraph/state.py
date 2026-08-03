"""LangGraph 对话状态。"""
from __future__ import annotations

import operator
from typing import Annotated, TypedDict


class GraphState(TypedDict, total=False):
    message: str
    history: list[dict[str, str]]
    authorization: str | None
    route: str
    reply: str
    events: Annotated[list[str], operator.add]
