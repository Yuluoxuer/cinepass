<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# subagent（子 Agent）

## Purpose
可插拔子 Agent。主图按 route 调用对应实例。

## Key Files

| File | Description |
|------|-------------|
| `base.py` | `SubAgent`：`run` / `astream` |
| `subagents/chat_agent.py` | 通用对话（LLM 或离线回退） |
| `subagents/helper_agent.py` | 本地 Tool 示例（时间 / echo） |

## For AI Agents
- 新 SubAgent：继承 `SubAgent`，在 `get_subagents()` 注册，并在 `langgraph/graph.py` 接线
