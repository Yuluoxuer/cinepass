"""订单子 agent：创建/查询/取消订单。"""
from __future__ import annotations

from typing import Any

from agent4.SubAgents.base import make_subagent
from agent4.skills import load_skill
from agent4.tools.AgentTools import BOOKING_TOOLS, cancel_order, create_order, get_order


def build_order_agent() -> Any:
    tools = [create_order, get_order, cancel_order, *BOOKING_TOOLS]
    return make_subagent("order", tools, load_skill("order"))
