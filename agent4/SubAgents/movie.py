"""电影子 agent：搜索/推荐电影、查看详情、选片。"""
from __future__ import annotations

from typing import Any

from agent4.SubAgents.base import make_subagent
from agent4.skills import load_skill
from agent4.tools.AgentTools import (
    BOOKING_TOOLS,
    get_movie,
    recommend_movies,
    search_movies,
)


def build_movie_agent() -> Any:
    tools = [search_movies, get_movie, recommend_movies, *BOOKING_TOOLS]
    return make_subagent("movie", tools, load_skill("movie"))
