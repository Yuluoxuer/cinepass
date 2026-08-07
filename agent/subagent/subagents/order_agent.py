"""订单 SubAgent（LangChain create_agent + ORDER_TOOLS）。"""
from __future__ import annotations

from typing import AsyncIterator

from langchain.agents import create_agent

from agent.llm import get_chat_model
from agent.subagent.base import SubAgent
from agent.subagent.lc_runtime import astream_agent_text, history_to_messages
from agent.tools import ORDER_TOOLS

_SYSTEM = (
    "你是订单助手。当用户想创建订单、查询订单或取消订单时，"
    "必须调用对应的 Tool 获取真实数据，然后用中文简洁回复。"
    "创建订单需要有效的锁座凭证（lockId）。"
    "禁止替用户支付——支付需用户在支付页手动操作。"
    "不要编造订单信息。"
)


class OrderSubAgent(SubAgent):
    name = "order"

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
            yield "[order/offline] 未配置 OPENAI_API_KEY，无法处理订单。请先配置 LLM。"
            return

        agent = create_agent(
            model,
            tools=ORDER_TOOLS,
            system_prompt=_SYSTEM,
            name="order",
        )
        messages = history_to_messages(message, history)
        async for text in astream_agent_text(agent, messages):
            yield text
