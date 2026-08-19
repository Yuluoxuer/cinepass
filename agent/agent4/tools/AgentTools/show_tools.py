"""场次 Tools — 调用票务中台场次查询接口，返回原始数据。"""
from __future__ import annotations

import httpx
from langchain.tools import tool

from agent4.tools.Http2BackendTools.http import get
from agent4.tools.Http2BackendTools.safety import validate_id
from agent4.config import get_settings


def _base() -> str:
    return get_settings().backend_base_url


@tool
async def list_shows(cinema_id: str, movie_id: str, date: str, time_window: str = "") -> str:
    """查询某影院某影片在指定日期的场次。cinema_id=影院ID，movie_id=影片ID，date=日期YYYY-MM-DD，time_window=可选时段(morning/afternoon/evening)。返回原始 JSON 数据。"""
    params: dict = {"cinemaId": cinema_id, "movieId": movie_id, "date": date}
    if time_window:
        params["timeWindow"] = time_window
    data = await get(
        f"{_base()}/shows",
        params=params,
    )
    return str(data)


@tool
async def get_show(show_id: str) -> str:
    """查看场次详情，包含影片和影院的简要信息。show_id=场次ID（如 s900）。返回原始 JSON 数据。"""
    try:
        validate_id(show_id, "show_id")
    except ValueError as exc:
        return str(exc)
    data = await get(f"{_base()}/shows/{show_id}")
    return str(data)


@tool
async def search_movies_by_time_range(start_time: str, end_time: str, cinema_id: str = "") -> str:
    """按时间段搜索有排片的电影。start_time/end_time 为 ISO-8601 时间戳（如 2026-08-10T13:00:00+08:00），cinema_id 可选。返回原始 JSON 数据。"""
    params: dict = {"startTime": start_time, "endTime": end_time, "page": 1, "size": 20}
    if cinema_id:
        params["cinemaId"] = cinema_id
    try:
        data = await get(f"{_base()}/shows/movies", params=params)
        return str(data)
    except httpx.HTTPStatusError as exc:
        # 后端在无场次时返回 HTTP 404（"场次不存在"），视为空列表，避免中断流程
        if exc.response.status_code == 404:
            return '{"code": 0, "data": {"items": []}}'
        return f"查询该时段电影失败（HTTP {exc.response.status_code}）"
    except httpx.HTTPError as exc:
        return f"查询该时段电影失败：{exc}"
