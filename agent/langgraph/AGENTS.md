<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# langgraph（主工作流）

## Purpose
用 LangGraph `StateGraph` 做 router → SubAgent 编排；对外提供 `build_graph` / `run_chat` / `stream_chat`。

## Key Files

| File | Description |
|------|-------------|
| `state.py` | `GraphState` |
| `graph.py` | 编译图：START→router→chat\|helper→END |
| `runner.py` | `run_chat`（ainvoke）；`stream_chat`（SSE 事件流） |

## For AI Agents
- 新节点：`add_node` + 在 router / conditional_edges 注册
- `stream_chat` 为 SSE 优化：先确定性路由，再 SubAgent.astream；与 `run_chat` 路由规则保持一致
