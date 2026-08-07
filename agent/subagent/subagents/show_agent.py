"""场次 SubAgent（LangChain create_agent + SHOW_TOOLS）。"""
from __future__ import annotations

from typing import AsyncIterator

from langchain.agents import create_agent

from agent.llm import get_chat_model
from agent.subagent.base import SubAgent
from agent.subagent.lc_runtime import astream_agent_text, history_to_messages
from agent.tools import SHOW_TOOLS

_SYSTEM = (
    "你是场次查询助手。当用户想查排片、场次时间或场次详情时，"
    "必须调用对应的 Tool 获取真实数据，然后用中文简洁回复。"
    "不要编造场次信息。"
)


class ShowSubAgent(SubAgent):
    name = "show"

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
            yield "[show/offline] 未配置 OPENAI_API_KEY，无法查询场次。请先配置 LLM。"
            return

        agent = create_agent(
            model,
            tools=SHOW_TOOLS,
            system_prompt=_SYSTEM,
            name="show",
        )
        messages = history_to_messages(message, history)
        async for text in astream_agent_text(agent, messages):
            yield text
