<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# turn（对话轮次编排）

## Purpose
单次对话 Turn 的完整编排逻辑。`process_agent_turn` 是路由层调用的核心入口：从中台 hydrate Draft → 从 Agent 自有库读对话历史 → 构造 `BookingGraphState` → 运行状态图 → 写回 Draft 至中台 → 写入消息至 Agent 自有库 → 返回 `AgentTurnResponse`。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `process_turn.py` | `process_agent_turn(req, ctx) -> AgentTurnResponse`：完整 Turn 编排，含 Draft hydrate、图执行、写回两端 |

## For AI Agents

### Working In This Directory
- **Draft 流转**：GET Draft（中台）→ 运行图 → PUT Draft（中台）；失败静默（`except: pass`），不影响对话
- **消息持久化**：`message_repo.list_messages`（读，limit=20）和 `message_repo.append_messages`（写 user+assistant）；失败回滚不抛出
- `_empty_draft` 在 Draft hydrate 失败时提供默认骨架，保证图始终有有效输入
- `debug=True` 时在响应中携带 `tool_traces`
- 数据库会话生命周期：每次操作独立 `SessionLocal()` + `try/finally close()`

### Testing Requirements
- 集成测试：mock `TicketApiClient` 和 `message_repo`，端到端验证 `AgentTurnResponse` 结构
- 测试 Draft hydrate 失败时的降级行为（应返回空 Draft 而非抛出）

### Common Patterns
```python
# 调用方（api/v1/agent.py）
result = await process_agent_turn(body, ctx)
return ok(result.model_dump(by_alias=True, exclude_none=True))
```

## Dependencies

### Internal
- `app/clients/ticket_api.py` — Draft GET/PUT
- `app/db/` — `SessionLocal`、`message_repo`
- `app/langchain/graph/booking_graph.py` — `run_booking_graph`
- `app/models/turn.py` — `AgentTurnRequest`、`AgentTurnResponse` 等

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
