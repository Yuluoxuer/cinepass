<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# api/v1（v1 路由）

## Purpose
v1 版本的 HTTP 路由定义。当前唯一端点：`POST /api/v1/agent/turns`，接收用户自然语言消息或卡片交互动作，触发完整 Agent 对话流，返回回复文本、卡片列表与进度状态。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 导出 `api_router`，将 `agent.router` 挂载到 `/` 前缀 |
| `agent.py` | `POST /agent/turns`：解析 `AgentTurnRequest` → 调用 `process_agent_turn` → 返回 `ok(...)` |

## For AI Agents

### Working In This Directory
- 新增 v1 端点：新建独立路由文件（如 `session.py`），在 `__init__.py` 中 `include_router`
- `agent.py` 是薄层：仅做类型转换和 `ok()` 包装，**业务逻辑禁止放在此处**
- `debug=true` 请求体会触发 `ToolTraceVO` 列表写入响应（供前端调试用）

### Testing Requirements
- `POST /api/v1/agent/turns` 集成测试：传 `{"message":"想看喜剧"}` 验证响应结构
- 验证 `message` 与 `cardAction` 均缺失时返回 422

### Common Patterns
```python
@router.post("/turns")
async def agent_turns(body: AgentTurnRequest, ctx: RequestContext = Depends(...)) -> dict:
    result = await process_agent_turn(body, ctx)
    return ok(result.model_dump(by_alias=True, exclude_none=True))
```

## Dependencies

### Internal
- `app/api/deps.py` — `RequestContext`、`get_request_context`
- `app/langchain/turn/process_turn.py` — `process_agent_turn`
- `app/models/turn.py` — `AgentTurnRequest`
- `app/models/response.py` — `ok`

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
