"""助手子 agent：查登录用户、查看/管理购票草稿。"""
from __future__ import annotations

from typing import Any

from agent4.SubAgents.base import make_subagent
from agent4.skills import load_skill
from agent4.tools.AgentTools import BOOKING_TOOLS, get_current_user


def build_helper_agent() -> Any:
    tools = [get_current_user, *BOOKING_TOOLS]
    return make_subagent("helper", tools, load_skill("helper"))
