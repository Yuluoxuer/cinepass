"""子 Agent 的业务工具（对 Java 中台的业务调用；底层 HTTP/JWT 在 Http2BackendTools）。

为避免同名冲突，本地草稿函数用 ``_local`` 后缀，中台草稿函数用 ``_middle`` 后缀。
"""
from agent4.tools.AgentTools.movie_tools import get_movie, recommend_movies, search_movies
from agent4.tools.AgentTools.cinema_tools import get_cinema, search_cinemas
from agent4.tools.AgentTools.show_tools import (
    get_show,
    list_shows,
    search_movies_by_time_range,
)
from agent4.tools.AgentTools.seat_tools import (
    get_seat_map,
    lock_seats,
    recommend_seats,
    unlock_seats,
)
from agent4.tools.AgentTools.order_tools import cancel_order, create_order, get_order
from agent4.tools.AgentTools.user_tools import get_current_user
from agent4.tools.AgentTools.draft_tools import (
    clear_booking_draft,
    get_booking_draft,
    load_draft as load_middle_draft,
    save_draft as save_middle_draft,
    update_booking_draft,
)
from agent4.tools.AgentTools.booking_draft import (
    get_session_id,
    load_draft as load_local_draft,
    save_draft as save_local_draft,
    use_session_id,
)

# 子 agent 共用的草稿读写工具
BOOKING_TOOLS = [get_booking_draft, update_booking_draft, clear_booking_draft]

__all__ = [
    "get_movie",
    "recommend_movies",
    "search_movies",
    "get_cinema",
    "search_cinemas",
    "get_show",
    "list_shows",
    "search_movies_by_time_range",
    "get_seat_map",
    "recommend_seats",
    "lock_seats",
    "unlock_seats",
    "create_order",
    "get_order",
    "cancel_order",
    "get_current_user",
    "get_booking_draft",
    "update_booking_draft",
    "clear_booking_draft",
    "load_middle_draft",
    "save_middle_draft",
    "load_local_draft",
    "save_local_draft",
    "use_session_id",
    "get_session_id",
    "BOOKING_TOOLS",
]
