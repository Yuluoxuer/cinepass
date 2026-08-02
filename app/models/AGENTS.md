<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# models（Pydantic 数据模型）

## Purpose
定义服务的请求/响应 Pydantic 契约模型，对齐前端系分 §7.5 和后端系分 §8.1。字段使用 camelCase alias 供 JSON 序列化，内部代码用 snake_case 访问。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `response.py` | `ok(data)` — 统一成功响应包装，返回 `{"code": 0, "data": ...}` |
| `turn.py` | 对话轮次契约：`AgentTurnRequest`、`AgentTurnResponse`、`CardAction`、`AgentCardVO`、`ProgressVO`、`ToolTraceVO` |

## For AI Agents

### Working In This Directory
- **字段命名**：JSON alias 用 camelCase（`alias="sessionId"`），Python 属性用 snake_case；`model_config = {"populate_by_name": True}` 两者均可接受
- **`AgentTurnRequest`** 有验证器：`message` 与 `cardAction` 至少填一个
- **`ProgressVO`** 的 `steps` 默认值与 `card_composer.py` 中的 `PROGRESS_STEPS` 保持一致
- 序列化时用 `model.model_dump(by_alias=True, exclude_none=True)`

### Common Patterns
```python
# 统一响应
return ok(result.model_dump(by_alias=True, exclude_none=True))
```

## Dependencies

### External
- `pydantic` — `BaseModel`、`Field`、`model_validator`

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
