"""ShowAgent — listShows；timeWindow 在 Agent 侧按 startTime 本地过滤。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools import show_tools


class ShowAgent:
    name = "ShowAgent"

    def __init__(self, tools_ctx: Any) -> None:
        self._ctx = tools_ctx

    async def list_shows(self, **kwargs: Any) -> Any:
        return await show_tools.list_shows(self._ctx, **kwargs)
