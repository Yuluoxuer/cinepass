"""对话 API：同步一轮 + SSE 流式。"""
from __future__ import annotations

import json
from collections.abc import AsyncIterator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from agent import run_chat, stream_chat
from fapi.deps import get_authorization
from fapi.models.chat import ChatRequest, ChatResponse

router = APIRouter(prefix="/chat", tags=["chat"])


def _history_dicts(body: ChatRequest) -> list[dict[str, str]]:
    return [{"role": m.role, "content": m.content} for m in body.history]


@router.post("", response_model=ChatResponse)
async def chat(
    body: ChatRequest,
    authorization: str | None = Depends(get_authorization),
) -> ChatResponse:
    """非流式：跑完一轮返回完整回复。JWT 经 Authorization 透传给 SubAgent。

    ``session_id`` 映射为 LangGraph ``thread_id``（Postgres Checkpointer 短期记忆）。
    未传时服务端生成并回写；多轮请带回同一个 session_id。
    """
    result = await run_chat(
        body.message,
        history=_history_dicts(body),
        authorization=authorization,
        session_id=body.session_id,
        latitude=body.latitude,
        longitude=body.longitude,
    )
    return ChatResponse(
        route=result["route"],
        reply=result["reply"],
        events=result.get("events") or [],
        session_id=result.get("session_id") or body.session_id,
    )


@router.post("/stream")
async def chat_stream(
    body: ChatRequest,
    authorization: str | None = Depends(get_authorization),
) -> StreamingResponse:
    """SSE 流式对话：event = route | token | done | error。"""

    async def event_source() -> AsyncIterator[str]:
        try:
            async for event in stream_chat(
                body.message,
                history=_history_dicts(body),
                authorization=authorization,
                session_id=body.session_id,
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
