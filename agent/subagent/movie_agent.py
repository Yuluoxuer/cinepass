"""电影 SubAgent（LangChain create_agent + MOVIE_TOOLS）。"""
from __future__ import annotations

from typing import AsyncIterator

from langchain.agents import create_agent

from agent.llm import get_chat_model
from agent.subagent.base import SubAgent
from agent.subagent.lc_runtime import astream_agent_text, history_to_messages
from agent.tools import MOVIE_TOOLS, search_movies, get_movie

_SYSTEM = (
    "你是电影查询助手。当用户想找电影、查影片详情或获取推荐时，"
    "必须调用对应的 Tool 获取真实数据，然后用中文简洁回复。"
    "不要编造电影信息。"
)


class MovieSubAgent(SubAgent):
    name = "movie"

    async def run(self, message: str, *, history: list[dict[str, str]] | None = None) -> str:
        chunks: list[str] = []
        async for piece in self.astream(message, history=history):
            chunks.append(piece)
        return "".join(chunks)

    async def astream(
        self, message: str, *, history: list[dict[str, str]] | None = None
    ) -> AsyncIterator[str]:
        model = get_chat_model()
        if model is None:
            yield "[movie/offline] 未配置 OPENAI_API_KEY，无法查询电影。请先配置 LLM。"
            return

        agent = create_agent(
            model,
            tools=MOVIE_TOOLS,
            system_prompt=_SYSTEM,
            name="movie",
        )
        messages = history_to_messages(message, history)
        async for text in astream_agent_text(agent, messages):
            yield text
