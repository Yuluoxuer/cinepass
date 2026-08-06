<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# langgraph（主工作流）

## Purpose
用 LangGraph `StateGraph` 做 router → SubAgent 编排；对外提供 `build_graph` / `run_chat` / `stream_chat`。

## Key Files

| File | Description |
|------|-------------|
| `state.py` | `GraphState` |
| `checkpoint.py` | 官方 `AsyncPostgresSaver`（`POSTGRES_URI`） |
| `graph.py` | 编译图：START→router→chat\|helper→END |
| `runner.py` | `run_chat` / `stream_chat`；`session_id`→`thread_id` |

## For AI Agents
- 新节点：`add_node` + 在 router / conditional_edges 注册
- `stream_chat` 为 SSE 优化：先确定性路由，再 SubAgent.astream；有 checkpoint 时用 `aupdate_state` 落盘
- Checkpointer 由 `fapi.main` lifespan 启动；业务代码用 `get_checkpointer()`，不要自己建连接