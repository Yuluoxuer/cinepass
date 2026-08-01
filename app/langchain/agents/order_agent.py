"""OrderAgent — createOrder / getOrder；禁止 pay。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools import order_tools


class OrderAgent:
    name = "OrderAgent"

    def __init__(self, tools_ctx: Any) -> None:
        self._ctx = tools_ctx

    async def create_order(self, **kwargs: Any) -> Any:
        return await order_tools.create_order(self._ctx, **kwargs)

    async def get_order(self, order_id: str) -> Any:
        return await order_tools.get_order(self._ctx, order_id)
