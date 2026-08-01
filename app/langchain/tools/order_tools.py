"""Order Tools → POST /orders · GET /orders/{id}。禁止 pay。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools.context import ToolsContext

# 硬约束：不得在此模块注册 / 调用 pay 相关路径。


async def create_order(ctx: ToolsContext, body: dict[str, Any]) -> Any:
    return await ctx.api.post(
        "/api/v1/orders",
        json=body,
        idempotency_key=ctx.idem_key(),
    )


async def get_order(ctx: ToolsContext, order_id: str) -> Any:
    return await ctx.api.get(f"/api/v1/orders/{order_id}")
