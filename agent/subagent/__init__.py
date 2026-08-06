"""SubAgent 注册表。"""
from agent.subagent.base import SubAgent
from agent.subagent.chat_agent import ChatSubAgent
from agent.subagent.cinema_agent import CinemaSubAgent
from agent.subagent.helper_agent import HelperSubAgent

__all__ = ["ChatSubAgent", "CinemaSubAgent", "HelperSubAgent", "SubAgent", "get_subagents"]


def get_subagents() -> dict[str, SubAgent]:
    return {
        "chat": ChatSubAgent(),
        "cinema": CinemaSubAgent(),
        "helper": HelperSubAgent(),
    }
