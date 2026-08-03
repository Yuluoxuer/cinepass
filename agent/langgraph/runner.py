"""工作流运行器：一次性 / SSE 友好的异步流。"""
from __future__ import annotations

from typing import Any, AsyncIterator

from agent.langgraph.graph import _route_message, build_graph
from agent.request_context import use_authorization
from agent.subagent import get_subagents

_compiled = None


def _graph():
    global _compiled
    if _compiled is None:
        _compiled = build_graph()
    return _compiled


async def run_chat(
    message: str,
    *,
    history: list[dict[str, str]] | None = None,
    authorization: str | None = None,
) -> dict[str, Any]:
    """跑完一轮，返回 route / reply / events。"""
    with use_authorization(authorization) as auth:
        result = await _graph().ainvoke(
            {
                "message": message,
                "history": history or [],
                "authorization": auth,
                "events": [],
            }
        )
    return {
        "route": result.get("route", "chat"),
        "reply": result.get("reply", ""),
        "events": result.get("events") or [],
    }


async def stream_chat(
    message: str,
    *,
    history: list[dict[str, str]] | None = None,
    authorization: str | None = None,
) -> AsyncIterator[dict[str, Any]]:
    """流式事件：route → token* → done。

    FastAPI SSE 层直接消费这些 dict；JWT 经 ContextVar 对 SubAgent/Tools 可见。
    """
    with use_authorization(authorization):
        route = _route_message(message)
        yield {"type": "route", "route": route}

        agent = get_subagents()[route]
        parts: list[str] = []
        async for token in agent.astream(message, history=history):
            parts.append(token)
            yield {"type": "token", "content": token}

        reply = "".join(parts)
        yield {
            "type": "done",
            "route": route,
            "reply": reply,
            "events": [f"routed:{route}", f"{route}_done"],
        }
