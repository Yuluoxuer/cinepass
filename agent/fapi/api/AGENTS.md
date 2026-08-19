<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# api（路由层）

## Purpose
挂载所有 API 路由。`__init__.py` 汇总为 `api_router`（前缀 `/api/v1`），由 `../main.py` include。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 创建 `api_router`（`/api/v1`），include 各子路由 |
| `chat.py` | `POST /api/v1/chat`（同步）+ `POST /api/v1/chat/stream`（SSE） |

## For AI Agents

### Working In This Directory
- 新增路由：在 `api/` 下新建文件，实现 `APIRouter`，然后在 `__init__.py` include
- `chat.py` 只做协议层：调用 `agent.run_chat` / `agent.stream_chat`

### Common Patterns

```python
from fastapi import APIRouter

router = APIRouter(prefix="/xxx", tags=["xxx"])

# 在 api/__init__.py 中注册
from fastapi.api.xxx import router as xxx_router

api_router.include_router(xxx_router)
```

## Dependencies

### Internal
- `agent` — `run_chat` / `stream_chat`
- `fapi.models.chat` — `ChatRequest` / `ChatResponse`
