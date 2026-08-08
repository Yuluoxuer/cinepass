"""对话 API：同步一轮 + SSE 流式（基于 agent2）。"""
from __future__ import annotations

import json
import uuid
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from agent2.agent import get_agent
from fapi.deps import get_authorization
from fapi.models.chat import ChatRequest, ChatResponse

router = APIRouter(prefix="/chat", tags=["chat"])


def _session_id(raw: str | None) -> str:
    """session_id → LangGraph thread_id；缺省则新建。"""
    return (raw or "").strip() or str(uuid.uuid4())


@router.post("", response_model=ChatResponse)
async def chat(
    body: ChatRequest,
    authorization: str | None = Depends(get_authorization),
) -> ChatResponse:
    """非流式：跑完一轮返回完整回复。JWT 经 Authorization 透传给 Tools。

    ``session_id`` 映射为 LangGraph ``thread_id``（PostgresSaver 短期记忆）。
    未传时服务端生成并回写；多轮请带回同一个 session_id。
    """
    session_id = _session_id(body.session_id)
    agent = await get_agent()
    reply = await agent.run(
        body.message,
        session_id=session_id,
        authorization=authorization,
        latitude=body.latitude,
        longitude=body.longitude,
    )
    return ChatResponse(
        route="agent2",
        reply=reply,
        events=[],
        session_id=session_id,
    )


@router.post("/stream")
async def chat_stream(
    body: ChatRequest,
    authorization: str | None = Depends(get_authorization),
) -> StreamingResponse:
    """SSE 流式对话：event = token | done | error（基于 agent2）。"""
    session_id = _session_id(body.session_id)

    async def event_source() -> AsyncIterator[str]:
        try:
            agent = await get_agent()
            async for event in agent.stream(
                body.message,
                session_id=session_id,
                authorization=authorization,
                latitude=body.latitude,
                longitude=body.longitude,
            ):
                etype = event.get("type", "message")
                payload = {k: v for k, v in event.items() if k != "type"}
                yield f"event: {etype}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"
        except Exception as exc:  # noqa: BLE001 — 流式通道需把错误推给客户端
            err = {"message": str(exc)}
            yield f"event: error\ndata: {json.dumps(err, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_source(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
