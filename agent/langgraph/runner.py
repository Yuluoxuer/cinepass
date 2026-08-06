"""工作流运行器：一次性 / SSE 友好的异步流。"""
from __future__ import annotations

import uuid
from typing import Any, AsyncIterator

from agent.langgraph.checkpoint import get_checkpointer
from agent.langgraph.graph import _route_message, build_graph
from agent.request_context import use_authorization
from agent.subagent import get_subagents

_compiled = None
_compiled_with_cp = None


def reset_graph_cache() -> None:
    """lifespan 启停时清空编译缓存，避免挂上已关闭的 checkpointer。"""
    global _compiled, _compiled_with_cp
    _compiled = None
    _compiled_with_cp = None


def _graph():
    """按当前 checkpointer 缓存编译图（有/无各一份）。"""
    global _compiled, _compiled_with_cp
    cp = get_checkpointer()
    if cp is None:
        if _compiled is None:
            _compiled = build_graph(checkpointer=None)
        return _compiled
    if _compiled_with_cp is None:
        _compiled_with_cp = build_graph(checkpointer=cp)
    return _compiled_with_cp


def _thread_config(session_id: str | None) -> tuple[dict[str, Any], str]:
    """session_id → LangGraph thread_id；缺省则新建。"""
    sid = (session_id or "").strip() or str(uuid.uuid4())
    return {"configurable": {"thread_id": sid}}, sid


async def _seed_history_if_empty(
    graph: Any,
    config: dict[str, Any],
    history: list[dict[str, str]] | None,
) -> list[dict[str, str]]:
    """有 checkpoint 时优先用库里的 history；仅新线程才接受客户端 seed。"""
    if get_checkpointer() is None:
        return history or []
    snap = await graph.aget_state(config)
    existing = (snap.values or {}).get("history") if snap else None
    if existing:
        return []
    return history or []


async def run_chat(
    message: str,
    *,
    history: list[dict[str, str]] | None = None,
    authorization: str | None = None,
    session_id: str | None = None,
    latitude: float | None = None,
    longitude: float | None = None,
) -> dict[str, Any]:
    """跑完一轮，返回 route / reply / events / session_id。"""
    graph = _graph()
    config, sid = _thread_config(session_id)
    seed = await _seed_history_if_empty(graph, config, history)

    with use_authorization(authorization) as auth:
        payload: dict[str, Any] = {
            "message": message,
            "authorization": auth,
            "latitude": latitude,
            "longitude": longitude,
            "events": [],
        }
        if seed:
            payload["history"] = seed
        result = await graph.ainvoke(payload, config)

    return {
        "route": result.get("route", "chat"),
        "reply": result.get("reply", ""),
        "events": result.get("events") or [],
        "session_id": sid,
    }


async def stream_chat(
    message: str,
    *,
    history: list[dict[str, str]] | None = None,
    authorization: str | None = None,
    session_id: str | None = None,
    latitude: float | None = None,
    longitude: float | None = None,
) -> AsyncIterator[dict[str, Any]]:
    """流式事件：route → token* → done。

    FastAPI SSE 层直接消费这些 dict。有 Checkpointer 时：先读库内 history，
    流式生成回复后再 ``aupdate_state`` 写入本轮对话（避免再跑一遍 LLM）。
    """
    graph = _graph()
    config, sid = _thread_config(session_id)
    cp = get_checkpointer()

    with use_authorization(authorization):
        route = _route_message(message)
        yield {"type": "route", "route": route, "session_id": sid}

        seeded_from_client = False
        prev_history: list[dict[str, str]] = []
        if cp is not None:
            snap = await graph.aget_state(config)
            prev_history = list((snap.values or {}).get("history") or [])
            if not prev_history and history:
                prev_history = list(history)
                seeded_from_client = True
        else:
            prev_history = list(history or [])

        agent = get_subagents()[route]
        parts: list[str] = []
        async for token in agent.astream(
            message,
            history=prev_history,
            latitude=latitude,
            longitude=longitude,
        ):
            parts.append(token)
            yield {"type": "token", "content": token}

        reply = "".join(parts)
        turn_history = [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ]
        # 新线程且客户端 seed 了 history：第一次写入要带上 seed，否则会丢
        history_delta = (
            list(prev_history) + turn_history if seeded_from_client else turn_history
        )

        if cp is not None:
            # 写入 checkpoint：与 chat_node / helper_node 产出对齐
            await graph.aupdate_state(
                config,
                {
                    "message": message,
                    "route": route,
                    "reply": reply,
                    "history": history_delta,
                    "events": [f"routed:{route}", f"{route}_done"],
                },
                as_node=route,
            )

        yield {
            "type": "done",
            "route": route,
            "reply": reply,
            "events": [f"routed:{route}", f"{route}_done"],
            "session_id": sid,
        }
