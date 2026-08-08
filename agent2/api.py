"""FastAPI 接口：暴露购票辅助 Agent 对话能力，供 Postman / 前端调用。

启动方式（从项目根目录）：
    cd /home/rei/Code/FastAPIAgent
    conda run -n langchain python -m uvicorn agent.api:app --host 0.0.0.0 --port 8001 --reload

接口：
    GET  /health                     健康检查
    POST /chat                       单轮对话（Authorization 头可选携带 JWT）
    GET  /booking-draft/{session_id} 查看某会话的购票草稿（调试用）
"""
from __future__ import annotations

import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header
from pydantic import BaseModel, Field

from .agent import get_agent
from .tools.booking_draft import ensure_table, load_draft


class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1, description="用户输入")
    session_id: str | None = Field(default=None, description="会话ID，多轮对话请带回")
    latitude: float | None = Field(default=None, ge=-90, le=90, description="纬度")
    longitude: float | None = Field(default=None, ge=-180, le=180, description="经度")


class ChatResponse(BaseModel):
    reply: str = Field(..., description="Agent 回复")
    session_id: str | None = Field(default=None, description="会话ID")
    draft: dict | None = Field(default=None, description="当前购票草稿（调试用）")


class DraftResponse(BaseModel):
    session_id: str
    draft: dict


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # 初始化全局 agent（连接池、PostgresSaver、booking_drafts 表）
    await get_agent()
    yield


app = FastAPI(title="购票辅助 Agent 服务", version="1.0.0", lifespan=lifespan)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "service": "agent-service"}


@app.post("/chat", response_model=ChatResponse)
async def chat(
    body: ChatRequest,
    authorization: str | None = Header(default=None, description="Bearer <JWT>"),
) -> ChatResponse:
    """单轮对话。

    - 不传 session_id 时服务端生成并回写；多轮请带回同一个 session_id。
    - Authorization 头可选：携带 JWT 可测试锁座/下单等需登录操作。
    """
    session_id = (body.session_id or "").strip() or str(uuid.uuid4())

    agent = await get_agent()
    reply = await agent.run(
        body.message,
        session_id=session_id,
        authorization=authorization,
        latitude=body.latitude,
        longitude=body.longitude,
    )

    # 附带当前购票草稿，便于调试记忆是否生效
    draft = await load_draft(session_id)
    return ChatResponse(reply=reply, session_id=session_id, draft=draft or {})


@app.get("/booking-draft/{session_id}", response_model=DraftResponse)
async def get_booking_draft(session_id: str) -> DraftResponse:
    """查看某会话的购票草稿（调试用）。"""
    await ensure_table()
    draft = await load_draft(session_id)
    return DraftResponse(session_id=session_id, draft=draft)
