"""MovieAgent — searchMovies / recommendMovies / getMovie。"""
from __future__ import annotations

from typing import Any

from app.langchain.tools import movie_tools


class MovieAgent:
    """影片子 Agent：目录检索与推荐，副作用无。"""

    name = "MovieAgent"

    def __init__(self, tools_ctx: Any) -> None:
        self._ctx = tools_ctx

    async def search_movies(self, **kwargs: Any) -> Any:
        return await movie_tools.search_movies(self._ctx, **kwargs)

    async def recommend_movies(self, **kwargs: Any) -> Any:
        return await movie_tools.recommend_movies(self._ctx, **kwargs)

    async def get_movie(self, movie_id: str) -> Any:
        return await movie_tools.get_movie(self._ctx, movie_id)
