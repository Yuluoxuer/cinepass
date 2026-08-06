"""MovieAgent Tools — 调用票务中台 /api/v1/movies 相关接口。"""
from __future__ import annotations

from langchain.tools import tool

from agent.http import get
from agent.settings import get_agent_settings


def _base() -> str:
    return get_agent_settings().backend_base_url


@tool
async def search_movies(
    query: str = "",
    genre: str = "",
    status: str = "hot_showing",
    page: int = 1,
    size: int = 10,
) -> str:
    """搜索影片。query=片名模糊搜索，genre=类型筛选，status=hot_showing|coming_soon。"""
    url = f"{_base()}/movies"
    params: dict = {"status": status, "page": page, "size": size}
    if query:
        params["q"] = query
    if genre:
        params["genre"] = genre
    data = await get(url, params=params)
    return str(data)


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
