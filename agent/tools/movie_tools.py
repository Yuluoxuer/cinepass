"""MovieAgent Tools — 调用票务中台 /movies 相关接口。"""
from __future__ import annotations

from langchain.tools import tool

from agent.http import backend_url, get
from agent.tools._common import safe_api_call


@tool("searchMovies")
async def search_movies(
    query: str = "",
    genre: str = "",
    status: str = "hot_showing",
    page: int = 1,
    size: int = 10,
) -> str:
    """搜索影片。query=片名模糊搜索，genre=类型筛选，status=hot_showing|coming_soon。"""
    params: dict = {"status": status, "page": page, "size": size}
    if query:
        params["q"] = query
    if genre:
        params["genre"] = genre
    data = await safe_api_call(
        get(backend_url("/movies"), params=params, timeout=5.0),
        domain="影片",
    )
    if isinstance(data, str):
        return data
    return str(data)


@tool("getMovie")
async def get_movie(movie_id: str) -> str:
    """查看影片详情。movie_id 为影片ID（如 m100）。"""
    data = await safe_api_call(
        get(backend_url(f"/movies/{movie_id}"), timeout=5.0),
        domain="影片",
        detail=True,
    )
    if isinstance(data, str):
        return data
    return str(data)


@tool("recommendMovies")
async def recommend_movies(limit: int = 10, exclude_movie_ids: str = "") -> str:
    """获取个性化影片推荐。limit=返回条数，exclude_movie_ids=排除的影片ID逗号分隔。"""
    params: dict = {"limit": limit}
    if exclude_movie_ids:
        params["excludeMovieIds"] = exclude_movie_ids
    data = await safe_api_call(
        get(backend_url("/reco/personal"), params=params, timeout=5.0),
        domain="影片推荐",
    )
    if isinstance(data, str):
        return data
    return str(data)
