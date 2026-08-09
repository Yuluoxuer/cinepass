"""场次子 agent：查某影院某影片的场次。"""
from __future__ import annotations

from typing import Any

from agent4.SubAgents.base import make_subagent
from agent4.skills import load_skill
from agent4.tools.AgentTools import BOOKING_TOOLS, get_show, list_shows


def build_show_agent() -> Any:
    tools = [list_shows, get_show, *BOOKING_TOOLS]
    return make_subagent("show", tools, load_skill("show"))
