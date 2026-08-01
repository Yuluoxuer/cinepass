"""LangGraph 共享状态。"""
from __future__ import annotations

from typing import Any, TypedDict


class BookingGraphState(TypedDict, total=False):
    session_id: str
    message: str | None
    card_action: dict[str, Any] | None
    client_draft_version: int | None
    authorization: str | None
    debug: bool

    draft: dict[str, Any]
    messages: list[dict[str, Any]]
    intent: str
    slot_patch: dict[str, Any]
    rag_hits: list[dict[str, Any]]
    plan: dict[str, Any]
    tool_results: list[dict[str, Any]]
    tool_traces: list[dict[str, Any]]
    reply_text: str
    cards: list[dict[str, Any]]
    progress: dict[str, Any]
    need_login: bool
    events: list[str]
    error: str | None
