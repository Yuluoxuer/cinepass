"""CinemaAgent — searchCinemas。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools import cinema_tools


class CinemaAgent:
    name = "CinemaAgent"

    def __init__(self, tools_ctx: Any) -> None:
        self._ctx = tools_ctx

    async def search_cinemas(self, **kwargs: Any) -> Any:
        return await cinema_tools.search_cinemas(self._ctx, **kwargs)
