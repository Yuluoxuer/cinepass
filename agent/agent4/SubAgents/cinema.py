"""影院子 agent：按位置/影片查影院、选影院。"""
from __future__ import annotations

from typing import Any

from agent4.SubAgents.base import make_subagent
from agent4.skills import load_skill
from agent4.tools.AgentTools import BOOKING_TOOLS, get_cinema, search_cinemas


def build_cinema_agent() -> Any:
    tools = [search_cinemas, get_cinema, *BOOKING_TOOLS]
    return make_subagent("cinema", tools, load_skill("cinema"))
