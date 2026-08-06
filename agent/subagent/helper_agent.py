"""工具型 SubAgent（LangChain 1.x ``create_agent`` + Tools）。"""
from __future__ import annotations

import re
from typing import AsyncIterator

from langchain.agents import create_agent

from agent.llm import get_chat_model
from agent.subagent.base import SubAgent
from agent.subagent.lc_runtime import astream_agent_text, history_to_messages
from agent.tools import HELPER_TOOLS, auth_status, echo_text, get_current_time, get_current_user

_SYSTEM = (
    "你是简洁的中文工具助手。需要查时间、回显文本、查看 JWT、或查询当前登录用户时，"
    "必须调用对应工具，再根据工具结果用中文简短回复。"
)


async def _offline_reply(message: str) -> str:
    """无 LLM 时直接 invoke Tool，保持本地可演示。"""
    lower = message.lower()
    if re.search(r"时间|几点|now|time", message, re.I) or "time" in lower:
        return f"[helper/offline] 当前时间：{get_current_time.invoke({'tz_name': 'local'})}"
    if re.search(r"我是谁|当前用户|用户信息|auth/me|whoami", message, re.I):
        return f"[helper/offline] {await get_current_user.ainvoke({})}"
    if re.search(r"jwt|token|鉴权|authorization|登录状态", message, re.I):
        return f"[helper/offline] {auth_status.invoke({})}"
    if message.strip().startswith("echo ") or message.strip().startswith("回显"):
        payload = re.sub(r"^(echo |回显)", "", message.strip(), count=1)
        return f"[helper/offline] {echo_text.invoke({'text': payload})}"
    tips = [
        "[helper/offline] 未配置 OPENAI_API_KEY。我仍可本地调用工具：",
        "- 问「现在几点」查时间",
        "- 说「echo xxx」做回显",
        "- 问「jwt」查看 Token",
        "- 问「我是谁」调用中台 /api/v1/auth/me（需 Authorization）",
        f"你刚才说：{message}",
    ]
    return "\n".join(tips)


class HelperSubAgent(SubAgent):
    name = "helper"

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
            yield await _offline_reply(message)
            return

        agent = create_agent(
            model,
            tools=HELPER_TOOLS,
            system_prompt=_SYSTEM,
            name="helper",
        )
        messages = history_to_messages(message, history)
        async for text in astream_agent_text(agent, messages):
            yield text
