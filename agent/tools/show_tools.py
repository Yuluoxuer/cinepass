"""ShowAgent Tools — 调用票务中台 /api/v1/shows 相关接口。"""
from __future__ import annotations

from langchain.tools import tool

from agent.http import get
from agent.settings import get_agent_settings


def _base() -> str:
    return get_agent_settings().backend_base_url


@tool
async def list_shows(cinema_id: str, movie_id: str, date: str) -> str:
    """查询某影院某影片在指定日期的场次。cinema_id=影院ID，movie_id=影片ID，date=日期YYYY-MM-DD。"""
    data = await get(
        f"{_base()}/shows",
        params={"cinemaId": cinema_id, "movieId": movie_id, "date": date},
    )
    return str(data)


@tool
async def get_show(show_id: str) -> str:
    """查看场次详情，包含影片和影院的简要信息。show_id 为场次ID（如 s900）。"""
    data = await get(f"{_base()}/shows/{show_id}")
    return str(data)
