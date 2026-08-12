"""座位子 agent：查座位图、推荐座位、锁座。"""
from __future__ import annotations

from typing import Any

from agent4.SubAgents.base import make_subagent
from agent4.skills import load_skill
from agent4.tools.AgentTools import (
    BOOKING_TOOLS,
    get_seat_map,
    lock_seats,
    recommend_seats,
    unlock_seats,
)


def build_seat_agent() -> Any:
    tools = [get_seat_map, recommend_seats, lock_seats, unlock_seats, *BOOKING_TOOLS]
    return make_subagent("seat", tools, load_skill("seat"))
