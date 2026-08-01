"""Seat Tools → seat-map / reco/seats / locks；写操作带 Idempotency-Key。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools.context import ToolsContext


async def get_seat_map(ctx: ToolsContext, show_id: str) -> Any:
    return await ctx.api.get(f"/api/v1/shows/{show_id}/seat-map")


async def recommend_seats(ctx: ToolsContext, body: dict[str, Any]) -> Any:
    return await ctx.api.post("/api/v1/reco/seats", json=body)


async def lock_seats(
    ctx: ToolsContext,
    *,
    show_id: str,
    seat_ids: list[str],
    session_id: str | None = None,
) -> Any:
    payload: dict[str, Any] = {"showId": show_id, "seatIds": seat_ids}
    if session_id:
        payload["sessionId"] = session_id
    return await ctx.api.post(
        "/api/v1/locks",
        json=payload,
        idempotency_key=ctx.idem_key(),
    )


async def unlock_seats(ctx: ToolsContext, lock_id: str) -> Any:
    return await ctx.api.delete(f"/api/v1/locks/{lock_id}")
