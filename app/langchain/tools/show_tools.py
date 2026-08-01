"""Show Tools → GET /shows。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools.context import ToolsContext


async def list_shows(
    ctx: ToolsContext,
    *,
    cinema_id: str | None = None,
    movie_id: str | None = None,
    date: str | None = None,
) -> Any:
    params: dict[str, Any] = {}
    if cinema_id:
        params["cinemaId"] = cinema_id
    if movie_id:
        params["movieId"] = movie_id
    if date:
        params["date"] = date
    return await ctx.api.get("/api/v1/shows", params=params)
