"""agent4 图入口：从 ``flow.py`` 取流程定义，负责编译缓存与 checkpointer 挂载。

购票流程（意图识别 → 提取草稿 → 追问补齐 → 确认 → 锁座支付）见
``agent4.graph.flow.build_booking_graph``。
"""
from __future__ import annotations

from typing import Any

from agent4.graph.checkpoint import get_checkpointer
from agent4.graph.flow import build_booking_graph

_compiled = None


def build_graph() -> Any:
    """构建并缓存购票流程图；挂上 PostgresSaver 短期记忆（checkpointer，thread_id=sessionId）。"""
    global _compiled
    if _compiled is not None:
        return _compiled

    g = build_booking_graph()

    _compiled = g.compile(checkpointer=get_checkpointer())
    return _compiled


def reset_graph_cache() -> None:
    """lifespan 启停时清空编译缓存，避免挂上已关闭的 checkpointer。"""
    global _compiled
    _compiled = None


def get_agent4() -> Any:
    """获取编译后的监督者图。"""
    return build_graph()


__all__ = ["build_graph", "get_agent4", "reset_graph_cache"]
