"""SeatAgent — getSeatMap / recommendSeats / lockSeats / unlockSeats。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools import seat_tools


class SeatAgent:
    name = "SeatAgent"

    def __init__(self, tools_ctx: Any) -> None:
        self._ctx = tools_ctx

    async def get_seat_map(self, show_id: str) -> Any:
        return await seat_tools.get_seat_map(self._ctx, show_id)

    async def recommend_seats(self, **kwargs: Any) -> Any:
        return await seat_tools.recommend_seats(self._ctx, **kwargs)

    async def lock_seats(self, **kwargs: Any) -> Any:
        return await seat_tools.lock_seats(self._ctx, **kwargs)

    async def unlock_seats(self, lock_id: str) -> Any:
        return await seat_tools.unlock_seats(self._ctx, lock_id)
