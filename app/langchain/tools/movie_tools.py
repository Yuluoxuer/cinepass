"""Movie Tools → GET /movies · GET /reco/personal · GET /movies/{id}。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools.context import ToolsContext


async def search_movies(
    ctx: ToolsContext,
    *,
    status: str | None = None,
    q: str | None = None,
    genre: str | None = None,
    page: int = 1,
    size: int = 20,
) -> Any:
    params: dict[str, Any] = {"page": page, "size": size}
    if status:
        params["status"] = status
    if q:
        params["q"] = q
    if genre:
        params["genre"] = genre
    return await ctx.api.get("/api/v1/movies", params=params)


async def recommend_movies(
    ctx: ToolsContext,
    *,
    limit: int = 10,
    exclude_movie_ids: list[str] | None = None,
) -> Any:
    params: dict[str, Any] = {"limit": limit}
    if exclude_movie_ids:
        params["excludeMovieIds"] = ",".join(exclude_movie_ids)
    return await ctx.api.get("/api/v1/reco/personal", params=params)


async def get_movie(ctx: ToolsContext, movie_id: str) -> Any:
    return await ctx.api.get(f"/api/v1/movies/{movie_id}")
