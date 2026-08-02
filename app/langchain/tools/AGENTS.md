<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# tools（业务 Tool 函数）

## Purpose
各业务领域的 Tool 函数，通过 `ToolsContext`（持有 `TicketApiClient`）调用票务中台 REST API。每个 Tool 函数对应一个中台接口，是 Planner `tool_calls` 中工具名的实现。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `context.py` | `ToolsContext` dataclass：持有 `api: TicketApiClient` 和 `session_id`，供所有 Tool 函数共享 |
| `movie_tools.py` | `search_movies`、`recommend_movies`、`get_movie` → `GET /api/v1/movies*` |
| `cinema_tools.py` | `search_cinemas` → `GET /api/v1/cinemas`（支持经纬度、关键词筛选） |
| `show_tools.py` | `list_shows` → `GET /api/v1/shows`（按 cinemaId + movieId + date 查询） |
| `seat_tools.py` | `recommend_seats`、`lock_seats` → 座位推荐与锁定接口 |
| `order_tools.py` | `create_order`、`get_order` → 订单创建与查询 |

## For AI Agents

### Working In This Directory
- 所有 Tool 函数签名：`async def xxx(ctx: ToolsContext, *, param: type) -> Any`
- **禁止**在 Tool 函数中直接操作数据库或绕过 `TicketApiClient`
- **禁止**在 Tool 函数中捕获并吞掉异常；调用方（`booking_graph.py`）负责错误处理
- 新增 Tool：创建新文件，函数通过 `ctx.api.get/post/put/delete` 调用中台，并在 `planner.py` 的 `tool_calls` 中注册工具名

### Testing Requirements
- 用 `respx` mock `httpx.AsyncClient`，单测每个 Tool 函数的参数构造和返回解析
- 验证 `lat`/`lng` 等可选参数的正确透传

### Common Patterns
```python
async def search_cinemas(ctx: ToolsContext, *, q: str | None = None) -> Any:
    return await ctx.api.get("/api/v1/cinemas", params={"q": q} if q else {})
```

## Dependencies

### Internal
- `app/clients/ticket_api.py` — `TicketApiClient`（通过 `ToolsContext` 持有）

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
