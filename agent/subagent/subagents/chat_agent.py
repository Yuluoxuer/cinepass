"""通用对话 SubAgent（LangChain 1.x ``create_agent``）。

挂载只读 Tools，使闲聊节点也能从数据库查询电影/影院/场次信息。
"""
from __future__ import annotations

from typing import AsyncIterator

from langchain.agents import create_agent

from agent.llm import get_chat_model
from agent.subagent.base import SubAgent
from agent.subagent.lc_runtime import astream_agent_text, history_to_messages
from agent.tools import (
    get_cinema,
    get_movie,
    get_seat_map,
    get_show,
    list_shows,
    recommend_movies,
    search_cinemas,
    search_movies,
)

# 只读 Tools：chat agent 可查询数据库但不写入
CHAT_TOOLS = [
    search_movies,      # 搜索影片（按片名/类型）
    get_movie,          # 影片详情
    recommend_movies,   # 个性化推荐
    search_cinemas,     # 搜索附近影院
    get_cinema,         # 影院详情
    list_shows,         # 场次列表
    get_show,           # 场次详情
    get_seat_map,       # 座位图（只读）
]

_SYSTEM = (
    "你是「妙语购票」的智能助手，简洁友好地用中文回答用户问题。\n"
    "你可以使用以下工具从数据库中查询真实数据来回答用户：\n"
    "- search_movies：按片名或类型搜索热映/即将上映的电影\n"
    "- get_movie：查看某部电影的详情\n"
    "- recommend_movies：获取个性化影片推荐\n"
    "- search_cinemas：搜索附近影院（需经纬度）\n"
    "- get_cinema：查看影院详情\n"
    "- list_shows：查看某影院某电影某天的场次排片\n"
    "- get_show：查看场次详情\n"
    "- get_seat_map：查看座位图\n\n"
    "回答规则：\n"
    "1. 当用户问电影推荐、有什么电影、某类型电影时，先调 search_movies 查数据库再回答\n"
    "2. 当用户问影院、排片、场次时，调对应工具查询后再回答\n"
    "3. 不要编造不存在的电影或场次，一切以数据库查询结果为准\n"
    "4. 回答简短清晰，列表用序号排列\n"
    "5. 如果工具查询失败或无结果，如实告知用户"
)


class ChatSubAgent(SubAgent):
    name = "chat"

    async def run(
        self,
        message: str,
        *,
        history: list[dict[str, str]] | None = None,
        latitude: float | None = None,
        longitude: float | None = None,
    ) -> str:
        chunks: list[str] = []
        async for piece in self.astream(
            message, history=history, latitude=latitude, longitude=longitude
        ):
            chunks.append(piece)
        return "".join(chunks)

    async def astream(
        self,
        message: str,
        *,
        history: list[dict[str, str]] | None = None,
        latitude: float | None = None,
        longitude: float | None = None,
    ) -> AsyncIterator[str]:
        model = get_chat_model()
        if model is None:
            yield (
                f"[chat/offline] 收到：{message}\n"
                "未配置 OPENAI_API_KEY，当前为本地回退回复。"
            )
            return

        # 把用户位置注入 system prompt，让 LLM 调 searchCinemas 时能传入 lat/lng
        system = _SYSTEM
        if latitude is not None and longitude is not None:
            system += (
                f"\n\n用户当前位置：纬度 {latitude}，经度 {longitude}。"
                "调用 searchCinemas 时请使用此经纬度。"
            )

        agent = create_agent(model, tools=CHAT_TOOLS, system_prompt=system, name="chat")
        messages = history_to_messages(message, history)
        async for text in astream_agent_text(agent, messages):
            yield text
