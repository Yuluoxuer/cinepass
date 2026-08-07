"""SubAgent 注册表。"""
from agent.subagent.base import SubAgent
from agent.subagent.subagents.chat_agent import ChatSubAgent
from agent.subagent.subagents.cinema_agent import CinemaSubAgent
from agent.subagent.subagents.helper_agent import HelperSubAgent
from agent.subagent.subagents.movie_agent import MovieSubAgent
from agent.subagent.subagents.order_agent import OrderSubAgent
from agent.subagent.subagents.seat_agent import SeatSubAgent
from agent.subagent.subagents.show_agent import ShowSubAgent

__all__ = [
    "ChatSubAgent", "HelperSubAgent",
    "MovieSubAgent", "ShowSubAgent", "SubAgent", "get_subagents",
    "CinemaSubAgent", "SeatSubAgent", "OrderSubAgent",
]

def get_subagents() -> dict[str, SubAgent]:
    return {
        "chat": ChatSubAgent(),
        "cinema": CinemaSubAgent(),
        "helper": HelperSubAgent(),
        "movie": MovieSubAgent(),
        "show": ShowSubAgent(),
        "seat": SeatSubAgent(),
        "order": OrderSubAgent(),
    }
