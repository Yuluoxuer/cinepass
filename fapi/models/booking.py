"""购票 Agent 请求 / 响应模型。"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field, model_validator


class BookingChatRequest(BaseModel):
    message: str = Field(..., min_length=1, description="用户消息，如'明天下午湘潭万达看哪吒2两张票'")
    session_id: str | None = Field(default=None, description="会话ID，多轮对话需带回同一个")
    latitude: float | None = Field(default=None, ge=-90, le=90, description="用户纬度（搜附近影院用）")
    longitude: float | None = Field(default=None, ge=-180, le=180, description="用户经度")

    @model_validator(mode="after")
    def coordinates_must_be_provided_together(self) -> "BookingChatRequest":
        if (self.latitude is None) != (self.longitude is None):
            raise ValueError("latitude 和 longitude 必须同时提供")
        return self


class BookingDraftVO(BaseModel):
    """BookingDraft 快照（系分 §4.3 字段子集）。"""
    movieId: str | None = None
    filmTitle: str | None = None
    cinemaId: str | None = None
    cinemaName: str | None = None
    showId: str | None = None
    date: str | None = None
    timeWindow: str | None = None
    count: int | None = None
    seatIds: list[str] | None = None
    preferRow: str | None = None
    preferSide: str | None = None
    together: bool | None = None
    lockId: str | None = None
    orderId: str | None = None
    expireAt: str | None = None


class BookingChatResponse(BaseModel):
    intent: str = Field(..., description="识别的意图: buy_ticket/browse/modify/confirm/pay/chitchat")
    reply: str = Field(..., description="助手回复文本")
    draft: BookingDraftVO = Field(default_factory=BookingDraftVO, description="当前购票草稿")
    draft_complete: bool = Field(..., description="草稿是否完备（可下单）")
    missing_fields: list[str] = Field(default_factory=list, description="缺失的必填字段")
    events: list[str] = Field(default_factory=list)
    session_id: str | None = None
