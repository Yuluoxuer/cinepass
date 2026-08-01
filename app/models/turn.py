"""Agent Turn 契约 — 对齐前端系分 §7.5 / 后端系分 §8.1。"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field, model_validator


class CardAction(BaseModel):
    card_id: str = Field(alias="cardId")
    action_id: str = Field(alias="actionId")
    item_id: str | None = Field(default=None, alias="itemId")
    draft_patch: dict[str, Any] | None = Field(default=None, alias="draftPatch")

    model_config = {"populate_by_name": True}


class AgentTurnRequest(BaseModel):
    session_id: str | None = Field(default=None, alias="sessionId")
    message: str | None = None
    card_action: CardAction | None = Field(default=None, alias="cardAction")
    client_draft_version: int | None = Field(default=None, alias="clientDraftVersion")
    debug: bool = False

    model_config = {"populate_by_name": True}

    @model_validator(mode="after")
    def require_message_or_card(self) -> AgentTurnRequest:
        if not self.message and self.card_action is None:
            raise ValueError("message 与 cardAction 至少填一个")
        return self


class ProgressVO(BaseModel):
    steps: list[str] = Field(
        default_factory=lambda: ["选片", "影院", "场次", "选座", "支付"]
    )
    current_index: int = Field(default=0, alias="currentIndex")
    state: str = "Idle"

    model_config = {"populate_by_name": True}


class AgentCardVO(BaseModel):
    card_id: str = Field(alias="cardId")
    type: str
    title: str | None = None
    payload: dict[str, Any] = Field(default_factory=dict)
    actions: list[dict[str, Any]] = Field(default_factory=list)

    model_config = {"populate_by_name": True}


class ToolTraceVO(BaseModel):
    tool: str
    input: dict[str, Any] = Field(default_factory=dict)
    output_summary: dict[str, Any] = Field(default_factory=dict, alias="outputSummary")
    latency_ms: int = Field(default=0, alias="latencyMs")
    success: bool = True

    model_config = {"populate_by_name": True}


class AgentTurnResponse(BaseModel):
    session_id: str = Field(alias="sessionId")
    reply_text: str = Field(alias="replyText")
    draft: dict[str, Any]
    cards: list[AgentCardVO] = Field(default_factory=list)
    progress: ProgressVO
    need_login: bool = Field(default=False, alias="needLogin")
    events: list[str] = Field(default_factory=list)
    tool_traces: list[ToolTraceVO] | None = Field(default=None, alias="toolTraces")

    model_config = {"populate_by_name": True}
