"""子 Agent 工厂：统一用 create_react_agent + 全局 LLM + skill 提示词。"""
from __future__ import annotations

from typing import Any

from langchain.agents import create_agent

from agent4.llm import get_llm


def make_subagent(name: str, tools: list[Any], prompt: str) -> Any:
    """用 langchain 1.3.x 的 ``create_agent`` 构建子 agent。

    - ``name``：子 agent 名称（chat/movie/cinema/...）
    - ``tools``：该子 agent 可用的工具子集
    - ``prompt``：system 提示词（通常来自 ``agent4.skills.load_skill(name)``）

    ``create_agent`` 返回 ``CompiledStateGraph[AgentState]``，与旧 ``create_react_agent``
    契约一致：输入 ``{"messages": [...]}``，输出 ``{"messages": [...]}``（含 ToolMessage）。
    """
    llm = get_llm()
    return create_agent(model=llm, tools=tools, system_prompt=prompt, name=name)
