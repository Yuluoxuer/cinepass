"""请求 / 响应模型。"""
from __future__ import annotations

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    role: str = Field(..., description="user | assistant | system")
    content: str


class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1)
    history: list[ChatMessage] = Field(default_factory=list)
    session_id: str | None = None


class ChatResponse(BaseModel):
    route: str
    reply: str
    events: list[str] = Field(default_factory=list)
    session_id: str | None = None
