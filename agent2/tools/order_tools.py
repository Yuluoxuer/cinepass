"""OrderAgent Tools — 调用票务中台订单创建/查询/取消接口，返回原始数据。"""
from __future__ import annotations

from typing import Any

import httpx
from langchain.tools import tool

from agent2.http import backend_url, get, post
from agent2.request_context import has_authorization


class OrderToolError(RuntimeError):
    """把中台或网络异常转换为可识别的错误消息。"""


def _unwrap(payload: Any) -> Any:
    if not isinstance(payload, dict):
        raise OrderToolError("订单服务返回的数据格式不正确，请稍后再试。")
    code = payload.get("code")
    message = str(payload.get("message") or "")
    if code not in (0, 200, None):
        raise OrderToolError(f"订单操作失败：{message or f'业务错误 code={code}'}")
    return payload.get("data")


def _http_error(exc: httpx.HTTPStatusError, *, detail: bool) -> OrderToolError:
    status = exc.response.status_code
    if status == 404 and detail:
        return OrderToolError("未找到该订单，请确认订单 ID 是否正确。")
    if status == 401:
        return OrderToolError("请先登录后再操作订单。")
    if status == 409:
        return OrderToolError("订单状态冲突，可能已被处理，请刷新后重试。")
    return OrderToolError(f"订单服务暂时不可用（HTTP {status}），请稍后再试。")


@tool("createOrder")
async def create_order(lock_id: str, session_id: str = "") -> str:
    """根据锁座凭证创建订单。lock_id=锁座凭证ID，session_id=可选会话ID(回写Draft)。返回原始 JSON 数据。"""
    if not has_authorization():
        return "请先登录后再下单。"

    body: dict[str, Any] = {"lockId": lock_id}
    if session_id:
        body["sessionId"] = session_id

    try:
        data = _unwrap(await post(backend_url("/orders"), json=body, timeout=1.0))
    except httpx.TimeoutException:
        return str(OrderToolError("创建订单超时，请稍后再试。"))
    except httpx.HTTPStatusError as exc:
        return str(_http_error(exc, detail=False))
    except httpx.HTTPError:
        return str(OrderToolError("订单服务连接失败，请稍后再试。"))
    return str(data)


@tool("getOrder")
async def get_order(order_id: str) -> str:
    """查询订单详情。order_id 为订单ID。返回原始 JSON 数据。"""
    if not has_authorization():
        return "请先登录后再查询订单。"

    try:
        data = _unwrap(await get(backend_url(f"/orders/{order_id}"), timeout=0.5))
    except httpx.TimeoutException:
        return str(OrderToolError("查询订单超时，请稍后再试。"))
    except httpx.HTTPStatusError as exc:
        return str(_http_error(exc, detail=True))
    except httpx.HTTPError:
        return str(OrderToolError("订单服务连接失败，请稍后再试。"))
    return str(data)


@tool("cancelOrder")
async def cancel_order(order_id: str, reason: str = "user_cancel") -> str:
    """取消订单。order_id=订单ID，reason=取消原因(默认user_cancel)。返回原始 JSON 数据。"""
    if not has_authorization():
        return "请先登录后再操作。"

    body = {"reason": reason}
    try:
        data = _unwrap(
            await post(
                backend_url(f"/orders/{order_id}/cancel"),
                json=body,
                timeout=0.8,
            )
        )
    except httpx.TimeoutException:
        return str(OrderToolError("取消订单超时，请稍后再试。"))
    except httpx.HTTPStatusError as exc:
        return str(_http_error(exc, detail=False))
    except httpx.HTTPError:
        return str(OrderToolError("订单服务连接失败，请稍后再试。"))
    return str(data)


ORDER_TOOLS = [create_order, get_order, cancel_order]

__all__ = [
    "ORDER_TOOLS",
    "OrderToolError",
    "create_order",
    "get_order",
    "cancel_order",
]
