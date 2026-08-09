"""影片 Tools — 调用票务中台影片/推荐接口，返回原始数据。"""
from __future__ import annotations

from typing import Any

from langchain.tools import tool

from agent3.http import get
from agent3.settings import get_agent_settings


def _base() -> str:
    return get_agent_settings().backend_base_url


def _filter_available_movies(payload: Any) -> Any:
    """仅保留有影院排片（nextShowDate 非空）的影片，避免展示无法购票的影片。

    中台 /movies 列表为每部影片聚合了 nextShowDate（最近一场排片日期），
    无排片时为 null；据此过滤后，Agent 只会看到有场次可买的电影。
    """
    if not isinstance(payload, dict):
        return payload
    inner = payload.get("data") if isinstance(payload.get("data"), dict) else None
    if not inner or not isinstance(inner.get("items"), list):
        return payload
    items = inner["items"]
    filtered = [m for m in items if isinstance(m, dict) and m.get("nextShowDate")]
    inner["items"] = filtered
    if isinstance(inner.get("total"), int):
        inner["total"] = len(filtered)
    return payload


@tool
async def search_movies(
    query: str = "",
    genre: str = "",
    status: str = "hot_showing",
    page: int = 1,
    size: int = 10,
) -> str:
    """搜索影片。query=片名模糊搜索，genre=类型筛选，status=hot_showing|coming_soon。
    仅返回有影院上映（有排片、可购票）的影片，无排片影片会被过滤掉。"""
    url = f"{_base()}/movies"
    params: dict = {"status": status, "page": page, "size": size}
    if query:
        params["q"] = query
    if genre:
        params["genre"] = genre
    data = await get(url, params=params)
    return str(_filter_available_movies(data))


@tool
async def get_movie(movie_id: str) -> str:
    """查看影片详情。movie_id 为影片ID（如 m100）。"""
    data = await get(f"{_base()}/movies/{movie_id}")
    return str(data)


@tool
async def recommend_movies(limit: int = 10, exclude_movie_ids: str = "") -> str:
    """获取个性化影片推荐。limit=返回条数，exclude_movie_ids=排除的影片ID逗号分隔。"""
    params: dict = {"limit": limit}
    if exclude_movie_ids:
        params["excludeMovieIds"] = exclude_movie_ids
    data = await get(f"{_base()}/reco/personal", params=params)
    return str(data)
