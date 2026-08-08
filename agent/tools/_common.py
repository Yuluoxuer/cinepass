"""Tool 共享基础设施：响应解包、HTTP 错误映射、鉴权守卫、安全调用包装。

所有需要调用票务中台的 Tool 文件统一使用此模块，避免重复代码。
"""
from __future__ import annotations

from typing import Any

import httpx

from agent.request_context import has_authorization


# ---------------------------------------------------------------------------
# 统一异常类
# ---------------------------------------------------------------------------

class ToolError(RuntimeError):
    """统一的 Tool 异常，domain 参数用于生成可读错误消息。"""

    def __init__(self, message: str, domain: str = "") -> None:
        self.domain = domain
        super().__init__(message)


# ---------------------------------------------------------------------------
# 响应解包
# ---------------------------------------------------------------------------

def unwrap(payload: Any, domain: str = "服务") -> Any:
    """统一解包中台 ``{code, message, data}`` 响应。

    Args:
        payload: HTTP 响应体（期望为 dict）
        domain: 服务名称，用于错误消息（如 "订单"、"座位"）

    Returns:
        payload["data"]

    Raises:
        ToolError: 响应格式不正确或业务 code 非成功
    """
    if not isinstance(payload, dict):
        raise ToolError(f"{domain}返回的数据格式不正确，请稍后再试。", domain)
    code = payload.get("code")
    message = str(payload.get("message") or "")
    if code not in (0, 200, None):
        raise ToolError(f"{domain}操作失败：{message or f'业务错误 code={code}'}", domain)
    return payload.get("data")


# ---------------------------------------------------------------------------
# HTTP 错误映射
# ---------------------------------------------------------------------------

def http_error(exc: httpx.HTTPStatusError, domain: str = "服务", *, detail: bool = False) -> ToolError:
    """统一映射 HTTP 状态码为 ToolError。

    Args:
        exc: httpx HTTP 状态异常
        domain: 服务名称，用于错误消息
        detail: 是否生成带 ID 提示的 404 消息（用于按 ID 查询场景）
    """
    status = exc.response.status_code
    if status == 404 and detail:
        return ToolError(f"未找到该{domain}信息，请确认 ID 是否正确。", domain)
    if status == 401:
        return ToolError("请先登录后再进行操作。", domain)
    if status == 409:
        return ToolError(f"{domain}状态冲突，可能已被处理，请刷新后重试。", domain)
    return ToolError(f"{domain}暂时不可用（HTTP {status}），请稍后再试。", domain)


# ---------------------------------------------------------------------------
# 鉴权守卫
# ---------------------------------------------------------------------------

def require_auth(hint: str = "请先登录后再操作。") -> str | None:
    """检查当前请求是否携带 JWT，未登录时返回提示消息。

    Returns:
        错误消息字符串（未登录时），已登录则返回 None
    """
    if not has_authorization():
        return hint
    return None


# ---------------------------------------------------------------------------
# 安全 HTTP 调用
# ---------------------------------------------------------------------------

async def safe_api_call(
    coro,
    *,
    domain: str = "服务",
    detail: bool = False,
) -> Any:
    """执行 HTTP 调用并统一处理异常。

    成功时返回 ``unwrap`` 后的 data；
    失败时返回 ``str(ToolError(...))`` 错误消息。

    Args:
        coro: httpx 请求协程（如 ``get(url)`` / ``post(url, json=body)``）
        domain: 服务名称，用于错误消息
        detail: 传递给 ``http_error`` 的 detail 参数
    """
    try:
        return unwrap(await coro, domain)
    except httpx.TimeoutException:
        return str(ToolError(f"请求{domain}超时，请稍后再试。", domain))
    except httpx.HTTPStatusError as exc:
        return str(http_error(exc, domain, detail=detail))
    except httpx.HTTPError:
        return str(ToolError(f"{domain}连接失败，请稍后再试。", domain))