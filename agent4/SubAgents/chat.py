"""对话子 agent：闲聊、普通问答、信息性追问。"""
from __future__ import annotations

from typing import Any

from agent4.SubAgents.base import make_subagent
from agent4.skills import load_skill
from agent4.tools.AgentTools import (
    BOOKING_TOOLS,
    get_current_user,
    list_shows,
    search_cinemas,
    search_knowledge_base,
    search_movies,
)


def build_chat_agent() -> Any:
    tools = [
        get_current_user,
        search_movies,
        search_cinemas,
        list_shows,
        search_knowledge_base,
        *BOOKING_TOOLS,
    ]
    return make_subagent("chat", tools, load_skill("chat"))
