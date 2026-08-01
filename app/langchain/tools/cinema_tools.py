"""Cinema Tools → GET /cinemas。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools.context import ToolsContext


async def search_cinemas(
    ctx: ToolsContext,
    *,
    lat: float | None = None,
    lng: float | None = None,
    radius_meters: int | None = None,
    q: str | None = None,
    page: int = 1,
    size: int = 20,
) -> Any:
    params: dict[str, Any] = {"page": page, "size": size}
    if lat is not None:
        params["lat"] = lat
    if lng is not None:
        params["lng"] = lng
    if radius_meters is not None:
        params["radiusMeters"] = radius_meters
    if q:
        params["q"] = q
    return await ctx.api.get("/api/v1/cinemas", params=params)
