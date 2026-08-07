"""座位 SubAgent（LangChain create_agent + SEAT_TOOLS）。"""
from __future__ import annotations

from typing import AsyncIterator

from langchain.agents import create_agent

from agent.llm import get_chat_model
from agent.subagent.base import SubAgent
from agent.subagent.lc_runtime import astream_agent_text, history_to_messages
from agent.tools import SEAT_TOOLS

_SYSTEM = (
    "你是选座助手。当用户想看座位图、推荐座位、锁座或解锁时，"
    "必须调用对应的 Tool 获取真实数据，然后用中文简洁回复。"
    "锁座和解锁属于写操作，必须确认用户已登录。"
    "不要编造座位信息，不要替用户支付——支付需用户在支付页手动操作。"
)


class SeatSubAgent(SubAgent):
    name = "seat"

    async def run(
        self,
        message: str,
        *,
        history: list[dict[str, str]] | None = None,
    ) -> str:
        chunks: list[str] = []
        async for piece in self.astream(message, history=history):
            chunks.append(piece)
        return "".join(chunks)

    async def astream(
        self,
        message: str,
        *,
        history: list[dict[str, str]] | None = None,
    ) -> AsyncIterator[str]:
        model = get_chat_model()
        if model is None:
            yield "[seat/offline] 未配置 OPENAI_API_KEY，无法处理选座。请先配置 LLM。"
            return

        agent = create_agent(
            model,
            tools=SEAT_TOOLS,
            system_prompt=_SYSTEM,
            name="seat",
        )
        messages = history_to_messages(message, history)
        async for text in astream_agent_text(agent, messages):
            yield text
