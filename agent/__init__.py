"""Agent 框架：LangGraph 工作流 + SubAgent。

对外入口：
- ``build_graph`` — 编译工作流
- ``stream_chat`` — 异步流式对话（供 FastAPI SSE 使用）
- ``run_chat`` — 一次性跑完一轮
"""
from agent.langgraph.graph import build_graph
from agent.langgraph.runner import run_chat, stream_chat

__all__ = ["build_graph", "run_chat", "stream_chat"]
