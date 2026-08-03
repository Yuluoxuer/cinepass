<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# models（Pydantic 模型）

## Purpose
存放所有 FastAPI 请求／响应的 Pydantic 模型，与路由逻辑解耦。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `chat.py` | `ChatMessage`、`ChatRequest`、`ChatResponse` |

## For AI Agents

### Working In This Directory
- 新增模型：在对应文件（或新建文件）中定义 `BaseModel` 子类
- 不要在模型里引入业务逻辑或数据库调用；保持纯 schema
- `ChatRequest.history` 为 `list[ChatMessage]`，`session_id` 可选；扩展字段时保持向后兼容

### Common Patterns
```python
from pydantic import BaseModel, Field

class MyRequest(BaseModel):
    field: str = Field(..., description="...")
    optional: str | None = None
```

## Dependencies

### Internal
- 被 `../api/chat.py` 引用

### External
- `pydantic` — `BaseModel`, `Field`

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
