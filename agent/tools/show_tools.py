"""ShowAgent Tools — 调用票务中台 /shows 相关接口。"""
from __future__ import annotations

from langchain.tools import tool

from agent.http import backend_url, get
from agent.tools._common import safe_api_call


@tool("listShows")
async def list_shows(cinema_id: str, movie_id: str, date: str) -> str:
    """查询某影院某影片在指定日期的场次。cinema_id=影院ID，movie_id=影片ID，date=日期YYYY-MM-DD。"""
    data = await safe_api_call(
        get(backend_url("/shows"), params={"cinemaId": cinema_id, "movieId": movie_id, "date": date}, timeout=5.0),
        domain="场次",
    )
    if isinstance(data, str):
        return data
    return str(data)


@tool("getShow")
async def get_show(show_id: str) -> str:
    """查看场次详情，包含影片和影院的简要信息。show_id 为场次ID（如 s900）。"""
    data = await safe_api_call(
        get(backend_url(f"/shows/{show_id}"), timeout=5.0),
        domain="场次",
        detail=True,
    )
    if isinstance(data, str):
        return data
    return str(data)
