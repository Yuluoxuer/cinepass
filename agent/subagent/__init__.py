"""SubAgent 注册表。"""
from agent.subagent.base import SubAgent
from agent.subagent.chat_agent import ChatSubAgent
from agent.subagent.helper_agent import HelperSubAgent

__all__ = ["ChatSubAgent", "HelperSubAgent", "SubAgent", "get_subagents"]


def get_subagents() -> dict[str, SubAgent]:
    return {
        "chat": ChatSubAgent(),
        "helper": HelperSubAgent(),
    }
