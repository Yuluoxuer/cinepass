"""SubAgent 注册表。"""
from agent.subagent.base import SubAgent
from agent.subagent.chat_agent import ChatSubAgent
from agent.subagent.cinema_agent import CinemaSubAgent
from agent.subagent.helper_agent import HelperSubAgent
from agent.subagent.movie_agent import MovieSubAgent
from agent.subagent.show_agent import ShowSubAgent

__all__ = [
    "ChatSubAgent", "HelperSubAgent",
    "MovieSubAgent", "ShowSubAgent", "SubAgent", "get_subagents","CinemaSubAgent"
]

def get_subagents() -> dict[str, SubAgent]:
    return {
        "chat": ChatSubAgent(),
        "cinema": CinemaSubAgent(),
        "helper": HelperSubAgent(),
        "movie": MovieSubAgent(),
        "show": ShowSubAgent(),
    }
