"""访问 Java 后端的底层工具：JWT 获取/注入 + 出站 HTTP（自动携带 JWT）。

- ``auth``：请求级 JWT / 位置 ContextVar（``use_authorization`` / ``use_location``）
- ``http``：``backend_url`` 拼路径，``get/post/delete`` 发请求并自动带 Authorization
"""
from agent4.tools.Http2BackendTools.auth import (
    get_authorization,
    get_location,
    has_authorization,
    normalize_authorization,
    use_authorization,
    use_location,
)
from agent4.tools.Http2BackendTools.http import backend_url, delete, get, post, request

__all__ = [
    "normalize_authorization",
    "get_authorization",
    "has_authorization",
    "use_authorization",
    "get_location",
    "use_location",
    "backend_url",
    "request",
    "get",
    "post",
    "delete",
]
