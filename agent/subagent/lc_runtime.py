"""对接本服务接口的小工具：history 格式、SSE token 抽取。

Agent 本体请直接用官方 ``langchain.agents.create_agent``，不必再包一层。
"""
from __future__ import annotations

from typing import Any, AsyncIterator


def history_to_messages(
    message: str, history: list[dict[str, str]] | None = None
) -> list[dict[str, str]]:
    """把本服务的 history + 当前用户消息转成 create_agent 输入格式。"""
    messages: list[dict[str, str]] = []
    for turn in history or []:
        role = turn.get("role")
        content = turn.get("content") or ""
        if role in ("user", "assistant") and content:
            messages.append({"role": role, "content": content})
    messages.append({"role": "user", "content": message})
    return messages


def content_to_text(content: Any) -> str:
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for part in content:
            if isinstance(part, dict):
                parts.append(str(part.get("text") or ""))
            else:
                parts.append(str(getattr(part, "text", None) or part))
        return "".join(parts)
    return str(content)


async def astream_agent_text(agent: Any, messages: list[dict[str, str]]) -> AsyncIterator[str]:
    """流式产出模型 token 文本（忽略非 model 节点）。"""
    async for item in agent.astream({"messages": messages}, stream_mode="messages"):
        if not isinstance(item, tuple) or len(item) < 2:
            continue
        msg, meta = item[0], item[1]
        if not isinstance(meta, dict) or meta.get("langgraph_node") != "model":
            continue
        text = content_to_text(getattr(msg, "content", None))
        if text:
            yield text
