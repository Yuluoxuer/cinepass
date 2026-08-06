"""请求 / 响应模型。"""
from __future__ import annotations

from pydantic import BaseModel, Field, model_validator


class ChatMessage(BaseModel):
    role: str = Field(..., description="user | assistant | system")
    content: str


class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1)
    history: list[ChatMessage] = Field(default_factory=list)
    session_id: str | None = None
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)

    @model_validator(mode="after")
    def coordinates_must_be_provided_together(self) -> "ChatRequest":
        """附近影院查询的位置必须是完整的一对经纬度。"""
        if (self.latitude is None) != (self.longitude is None):
            raise ValueError("latitude 和 longitude 必须同时提供")
        return self


class ChatResponse(BaseModel):
    route: str
    reply: str
    events: list[str] = Field(default_factory=list)
    session_id: str | None = None
