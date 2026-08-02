<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# api（HTTP 路由层）

## Purpose
FastAPI 路由定义层，当前仅支持版本 v1。负责解析 HTTP 请求、注入请求上下文（`Authorization`、`X-Request-Id`）、调用 `process_agent_turn` 业务逻辑，并返回统一格式响应。路由层本身不含业务逻辑。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 导出 `api_router`，供 `app/main.py` 挂载 |
| `deps.py` | `get_request_context`：从请求头提取 `Authorization` 和 `X-Request-Id`，返回 `RequestContext` dataclass |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `v1/` | v1 版本路由定义（见 `v1/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 新增路由在 `v1/` 子目录下定义，并挂载到 `v1/__init__.py` 导出的 `api_router`
- `deps.py` 提供请求级上下文（`Authorization` JWT）；新增依赖注入函数在此处添加
- 路由函数保持薄层：解析入参 → 调用 `langchain/` 或 `clients/` → 包装 `ok()` 返回

### Testing Requirements
- 用 `httpx.AsyncClient(app=create_app(), base_url="http://test")` 做路由层集成测试
- `deps.py` 可通过 `app.dependency_overrides` 替换来注入测试上下文

### Common Patterns
- 响应统一用 `from app.models.response import ok`
- 依赖注入：`ctx: RequestContext = Depends(get_request_context)`

## Dependencies

### Internal
- `app/models/response.py` — 统一响应包装
- `app/langchain/turn/process_turn.py` — 业务逻辑入口

### External
- `fastapi` — `APIRouter`、`Depends`、`Header`

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
