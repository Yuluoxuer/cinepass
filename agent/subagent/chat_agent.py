"""通用对话 SubAgent（LangChain 1.x ``create_agent``）。"""
from __future__ import annotations

from json import tool
from typing import AsyncIterator

from langchain.agents import create_agent

from agent.llm import get_chat_model
from agent.subagent.base import SubAgent
from agent.subagent.lc_runtime import astream_agent_text, history_to_messages

_SYSTEM = "你是简洁友好的中文助手，回答短而清晰。"

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

        agent = create_agent(model, system_prompt=_SYSTEM, name="chat")
        messages = history_to_messages(message, history)
        async for text in astream_agent_text(agent, messages):
            yield text
