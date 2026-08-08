"""OrderAgent Tools — 调用票务中台订单创建/查询/取消接口。"""
from __future__ import annotations

from typing import Any

from langchain.tools import tool

from agent.http import backend_url, get, post
from agent.tools._common import ToolError, require_auth, safe_api_call

# 向后兼容别名
OrderToolError = ToolError


def format_order(data: Any) -> str:
    """把 OrderVO 格式化为用户可读文本。"""
    if not isinstance(data, dict):
        return "订单数据格式不正确，请稍后再试。"
    status_map = {
        "pending_pay": "待支付",
        "issued": "已出票",
        "cancelled": "已取消",
    }
    status = status_map.get(data.get("status"), data.get("status", "-"))
    lines = [
        "订单详情：",
        f"- 订单号：{data.get('orderId', '-')}",
        f"- 影片：{data.get('movieTitle', '-')}",
        f"- 影院：{data.get('cinemaName', '-')}",
        f"- 影厅：{data.get('hallName', '-')}",
        f"- 开场时间：{data.get('startTime', '-')}",
        f"- 金额：¥{data.get('amount', '?')}（单价 ¥{data.get('unitPrice', '?')} × {len(data.get('seatIds', []))} 张）",
        f"- 状态：{status}",
    ]
    ticket_code = data.get("ticketCode")
    if ticket_code:
        lines.append(f"- 取票码：{ticket_code}")
    expire_at = data.get("expireAt")
    if expire_at and data.get("status") == "pending_pay":
        lines.append(f"- 支付截止：{expire_at}")
    return "\n".join(lines)


# ---------- createOrder ----------

@tool("createOrder")
async def create_order(lock_id: str, session_id: str = "") -> str:
    """根据锁座凭证创建订单。lock_id=锁座凭证ID，session_id=可选会话ID(回写Draft)。注意：创建订单后需用户在支付页面手动支付。"""
    auth_err = require_auth("请先登录后再下单。")
    if auth_err:
        return auth_err

    body: dict[str, Any] = {"lockId": lock_id}
    if session_id:
        body["sessionId"] = session_id

    data = await safe_api_call(
        post(backend_url("/orders"), json=body, timeout=1.0),
        domain="订单",
    )
    if isinstance(data, str):
        return data
    return format_order(data)


# ---------- getOrder ----------

@tool("getOrder")
async def get_order(order_id: str) -> str:
    """查询订单详情。order_id 为订单ID。返回影片、影院、座位、金额和状态等信息。"""
    auth_err = require_auth("请先登录后再查询订单。")
    if auth_err:
        return auth_err

    data = await safe_api_call(
        get(backend_url(f"/orders/{order_id}"), timeout=0.5),
        domain="订单",
        detail=True,
    )
    if isinstance(data, str):
        return data
    return format_order(data)


# ---------- cancelOrder ----------

@tool("cancelOrder")
async def cancel_order(order_id: str, reason: str = "user_cancel") -> str:
    """取消订单。order_id=订单ID，reason=取消原因(默认user_cancel)。取消后会释放关联的锁座。"""
    auth_err = require_auth("请先登录后再操作。")
    if auth_err:
        return auth_err

    data = await safe_api_call(
        post(backend_url(f"/orders/{order_id}/cancel"), json={"reason": reason}, timeout=0.8),
        domain="订单",
    )
    if isinstance(data, str):
        return data
    return format_order(data)


ORDER_TOOLS = [create_order, get_order, cancel_order]

__all__ = [
    "ORDER_TOOLS",
    "OrderToolError",
    "create_order",
    "get_order",
    "cancel_order",
    "format_order",
]
