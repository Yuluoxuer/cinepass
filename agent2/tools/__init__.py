"""Agent 本地 Tools（LangChain StructuredTool）：仅保留向后端发起请求的 Tool。"""
from .backend_tools import get_current_user
from .booking_draft import (
    BOOKING_DRAFT_TOOLS,
    clear_booking_draft,
    ensure_table,
    get_booking_draft,
    update_booking_draft,
)
from .cinema_tools import CINEMA_TOOLS, get_cinema, search_cinemas
from .movie_tools import get_movie, recommend_movies, search_movies
from .order_tools import ORDER_TOOLS, cancel_order, create_order, get_order
from .seat_tools import SEAT_TOOLS, get_seat_map, lock_seats, recommend_seats, unlock_seats
from .show_tools import get_show, list_shows

HELPER_TOOLS = [get_current_user]
MOVIE_TOOLS = [search_movies, get_movie, recommend_movies]
SHOW_TOOLS = [list_shows, get_show]


def get_all_tools():
    """获取所有可用工具（仅向后端发起请求的 Tool，返回原始数据由 Agent 整理）。"""
    return (
        HELPER_TOOLS
        + MOVIE_TOOLS
        + SHOW_TOOLS
        + CINEMA_TOOLS
        + SEAT_TOOLS
        + ORDER_TOOLS
        + BOOKING_DRAFT_TOOLS
    )


__all__ = [
    "get_all_tools",
    "HELPER_TOOLS",
    "MOVIE_TOOLS",
    "SHOW_TOOLS",
    "BOOKING_DRAFT_TOOLS",
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
    "get_booking_draft",
    "update_booking_draft",
    "clear_booking_draft",
    "ensure_table",
]
