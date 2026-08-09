"""agent3 内部 Tools 包：自包含复制的票务工具（JWT 经 agent3.http 自动透传）。

原文件来自 agent2/tools/，仅 re-export agent3 监督者图使用到的 15 个工具。
"""
from agent3.tools.backend_tools import get_current_user
from agent3.tools.cinema_tools import get_cinema, search_cinemas
from agent3.tools.movie_tools import get_movie, recommend_movies, search_movies
from agent3.tools.order_tools import cancel_order, create_order, get_order
from agent3.tools.seat_tools import get_seat_map, lock_seats, recommend_seats, unlock_seats
from agent3.tools.show_tools import get_show, list_shows

__all__ = [
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
