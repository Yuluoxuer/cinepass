"""RAG Tool 调用链路验证脚本（不启动 FastAPI，不依赖后端）。

用法（在 agent 仓库根目录）：
    python scripts/test_rag_tool.py

验证两件事：
1. 离线直接调用 search_knowledge_base，确认 Tool 本身可执行。
2. 若配置了 OPENAI_API_KEY，则通过 RagTestSubAgent 发起一轮对话，
   观察 LLM 是否会自动发现并调用 search_knowledge_base。
"""
from __future__ import annotations

import asyncio
import sys
from pathlib import Path

# 允许以脚本方式直接运行：把仓库根目录加入 sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# Windows 控制台默认 GBK，强制 UTF-8 以正确输出中文与符号
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

from agent.llm import get_chat_model
from agent.subagent.subagents.rag_test_agent import RagTestSubAgent
from agent.tools.rag_tools import search_knowledge_base


def test_direct_call() -> None:
    """验证点 1：Tool 直接调用（不经过 LLM）。"""
    print("=" * 50)
    print("[1] 直接调用 Tool（绕过 LLM）")
    print("=" * 50)
    result = search_knowledge_base.invoke({"query": "开场前能退票吗"})
    print("返回：")
    print(result)
    print()


async def test_agent_call() -> None:
    """验证点 2：通过 Agent（LLM）自动发现并调用 Tool。"""
    print("=" * 50)
    print("[2] 通过 Agent 自动调用 Tool（需要 LLM）")
    print("=" * 50)

    if get_chat_model() is None:
        print("[WARN] 未配置 OPENAI_API_KEY，跳过 Agent 验证。")
        print("  请在 .env 中配置后重新运行本脚本以验证完整链路。\n")
        return

    agent = RagTestSubAgent()
    question = "这家影院开场前多久可以退票？"
    print(f"提问：{question}")
    print("回答：", end="", flush=True)

    async for piece in agent.astream(question):
        print(piece, end="", flush=True)
    print()


async def main() -> None:
    test_direct_call()
    await test_agent_call()


if __name__ == "__main__":
    asyncio.run(main())
