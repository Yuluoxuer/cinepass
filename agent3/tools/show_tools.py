"""场次 Tools — 调用票务中台场次查询接口，返回原始数据。"""
from __future__ import annotations

from langchain.tools import tool

from agent3.http import get
from agent3.settings import get_agent_settings


def _base() -> str:
    return get_agent_settings().backend_base_url


@tool
async def list_shows(cinema_id: str, movie_id: str, date: str) -> str:
    """查询某影院某影片在指定日期的场次。cinema_id=影院ID，movie_id=影片ID，date=日期YYYY-MM-DD。返回原始 JSON 数据。"""
    data = await get(
        f"{_base()}/shows",
        params={"cinemaId": cinema_id, "movieId": movie_id, "date": date},
    )
    return str(data)


@tool
async def get_show(show_id: str) -> str:
    """查看场次详情，包含影片和影院的简要信息。show_id=场次ID（如 s900）。返回原始 JSON 数据。"""
    data = await get(f"{_base()}/shows/{show_id}")
    return str(data)
