"""RAG 测试 SubAgent：独立验证 RAG Tool 的发现与调用，不影响业务 agent。"""
from __future__ import annotations

from typing import AsyncIterator

from langchain.agents import create_agent

from agent.llm import get_chat_model
from agent.subagent.base import SubAgent
from agent.subagent.lc_runtime import astream_agent_text, history_to_messages
from agent.tools.rag_tools import RAG_TOOLS

_SYSTEM = (
    "你是「妙语购票」的知识库问答助手，用简洁中文回答。\n"
    "你可以调用工具 search_knowledge_base 检索退票政策、改签规则、操作指南等知识。\n"
    "规则：用户只要问到退票、改签、政策、规则、怎么操作、常见问题，"
    "必须先调用 search_knowledge_base，再依据工具返回内容回答，不要凭空编造。"
)


class RagTestSubAgent(SubAgent):
    name = "rag_test"

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
                "[rag_test/offline] 未配置 OPENAI_API_KEY，无法用 LLM 验证工具调用。"
                "请先在 .env 配置密钥后再测。"
            )
            return

        agent = create_agent(
            model, tools=RAG_TOOLS, system_prompt=_SYSTEM, name="rag_test"
        )
        messages = history_to_messages(message, history)
        async for text in astream_agent_text(agent, messages):
            yield text
