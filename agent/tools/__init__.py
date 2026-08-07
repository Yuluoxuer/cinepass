"""Agent 本地 Tools（LangChain StructuredTool）。"""
from agent.tools.movie_tools import get_movie, recommend_movies, search_movies
from agent.tools.backend_tools import get_current_user
from agent.tools.cinema_tools import CINEMA_TOOLS, get_cinema, search_cinemas
from agent.tools.order_tools import ORDER_TOOLS, cancel_order, create_order, get_order
from agent.tools.sample_tools import auth_status, authorized_get, echo_text, get_current_time
from agent.tools.seat_tools import SEAT_TOOLS, get_seat_map, lock_seats, recommend_seats, unlock_seats
from agent.tools.show_tools import get_show, list_shows

HELPER_TOOLS = [get_current_time, echo_text, auth_status, get_current_user]
MOVIE_TOOLS = [search_movies, get_movie, recommend_movies]
SHOW_TOOLS = [list_shows, get_show]
CINEMA_TOOLS = CINEMA_TOOLS
SEAT_TOOLS = SEAT_TOOLS
ORDER_TOOLS = ORDER_TOOLS


__all__ = [
    "HELPER_TOOLS",
    "MOVIE_TOOLS",
    "SHOW_TOOLS",
    "CINEMA_TOOLS",
    "SEAT_TOOLS",
    "ORDER_TOOLS",
    "auth_status",
    "authorized_get",
    "echo_text",
    "get_current_time",
    "get_movie",
    "get_show",
    "list_shows",
    "recommend_movies",
    "search_movies",
    "get_current_user",
    "get_cinema",
    "search_cinemas",
    "get_seat_map",
    "recommend_seats",
    "lock_seats",
    "unlock_seats",
    "create_order",
    "get_order",
    "cancel_order",
]
