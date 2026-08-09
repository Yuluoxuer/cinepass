"""购票 Agent 请求 / 响应模型。"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field, model_validator


class CardActionBody(BaseModel):
    """前端卡片点击操作（对齐 agent2 的 CardActionBody）。"""

    cardId: str
    actionId: str
    itemId: str | None = None
    draftPatch: dict[str, Any] | None = None


class BookingChatRequest(BaseModel):
    model_config = {"populate_by_name": True}

    message: str | None = Field(default=None, description="用户消息，如'明天下午湘潭万达看哪吒2两张票'")
    session_id: str | None = Field(default=None, alias="sessionId", description="会话ID，多轮对话需带回同一个")
    latitude: float | None = Field(default=None, ge=-90, le=90, description="用户纬度（搜附近影院用）")
    longitude: float | None = Field(default=None, ge=-180, le=180, description="用户经度")
    cardAction: CardActionBody | None = Field(default=None, description="卡片点击操作（点「选这部/选这场/确认选座」等）")
    clientDraft: dict[str, Any] | None = Field(default=None, description="前端手动页面/中台草稿快照，同步给 Agent 避免不知道手动选片")
    clientDraftVersion: int | None = Field(default=None, description="前端草稿乐观锁版本号（draft.version），用于冲突检测")

    @model_validator(mode="after")
    def message_or_card_action(self) -> "BookingChatRequest":
        if not self.message and not self.cardAction:
            raise ValueError("message 和 cardAction 至少提供一个")
        if (self.latitude is None) != (self.longitude is None):
            raise ValueError("latitude 和 longitude 必须同时提供")
        return self


class BookingDraftVO(BaseModel):
    """BookingDraft 快照（系分 §4.3 字段子集）。"""
    sessionId: str = ""
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
    version: int = 0


class BookingChatResponse(BaseModel):
    intent: str = Field(..., description="识别的意图: buy_ticket/browse/modify/confirm/pay/chitchat")
    reply: str = Field(..., description="助手回复文本")
    replyText: str = Field("", description="与 reply 同值，对齐前端 AgentTurnResponse")
    draft: BookingDraftVO = Field(default_factory=BookingDraftVO, description="当前购票草稿")
    draft_complete: bool = Field(..., description="草稿是否完备（可下单）")
    missing_fields: list[str] = Field(default_factory=list, description="缺失的必填字段")
    cards: list[dict[str, Any]] = Field(default_factory=list, description="动态卡片（影片/影院/场次/座位/支付等）")
    needLogin: bool = Field(False, description="下一步需登录时 true")
    events: list[str] = Field(default_factory=list)
    session_id: str | None = None


class BookingChatEnvelope(BaseModel):
    """对齐前端 client.ts 的 {code, message, data} 信封格式。"""

    code: int = 200
    message: str = "ok"
    data: BookingChatResponse
