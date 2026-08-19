"""SubAgents：7 个子 agent，每个独立文件、职责单一。

- ``chat``：闲聊/问答
- ``movie``：搜索/推荐/选片
- ``cinema``：查/选影院
- ``show``：查/选场次
- ``seat``：座位图/推荐/锁座
- ``order``：创建/查/取消订单
- ``helper``：登录用户/草稿管理

每个子 agent 的角色与指令定义在 ``agent4.skills/<name>.md``，此处只负责组装工具子集。
"""
from __future__ import annotations

from typing import Any

from agent4.SubAgents.chat import build_chat_agent
from agent4.SubAgents.cinema import build_cinema_agent
from agent4.SubAgents.helper import build_helper_agent
from agent4.SubAgents.movie import build_movie_agent
from agent4.SubAgents.order import build_order_agent
from agent4.SubAgents.seat import build_seat_agent
from agent4.SubAgents.show import build_show_agent

SUBAGENT_KEYS: tuple[str, ...] = ("chat", "movie", "cinema", "show", "seat", "order", "helper")

BUILDERS: dict[str, Any] = {
    "chat": build_chat_agent,
    "movie": build_movie_agent,
    "cinema": build_cinema_agent,
    "show": build_show_agent,
    "seat": build_seat_agent,
    "order": build_order_agent,
    "helper": build_helper_agent,
}


def build_all_subagents() -> dict[str, Any]:
    """构建所有子 agent（每个为 create_react_agent 实例）。"""
    return {k: BUILDERS[k]() for k in SUBAGENT_KEYS}


__all__ = [
    "SUBAGENT_KEYS",
    "BUILDERS",
    "build_all_subagents",
    "build_chat_agent",
    "build_movie_agent",
    "build_cinema_agent",
    "build_show_agent",
    "build_seat_agent",
    "build_order_agent",
    "build_helper_agent",
]
