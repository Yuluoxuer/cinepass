<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# clients（中台 HTTP 客户端）

## Purpose
封装对票务中台 ticket-api（Java 服务）的所有 HTTP 调用。使用 httpx 异步客户端，透传用户 `Authorization` JWT，不附加 `X-Internal-Api-Key`。对话消息不经此层，由 `app/db/` 直接存 Agent 自有库。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `ticket_api.py` | `TicketApiClient`：封装 `get`/`post`/`put`/`delete`；`get_draft`/`put_draft` 为 Draft 专用方法 |

## For AI Agents

### Working In This Directory
- `TicketApiClient` 每次调用创建新的 `httpx.AsyncClient` 上下文（短连接），超时由 `settings.ticket_api_timeout_s` 控制
- **禁止**在此层捕获并吞掉 HTTP 错误；调用方（`process_turn.py`）负责 `try/except`
- 新增中台接口：在 `ticket_api.py` 添加专用方法（类似 `get_draft`/`put_draft`），不在 Tools 中直接构造 URL
- `Authorization` 头从 `RequestContext` 透传，不从配置读取

### Testing Requirements
- 用 `respx` 或 `unittest.mock.AsyncMock` mock `httpx.AsyncClient`
- 验证 `Authorization` 头是否正确透传
- 验证 `raise_for_status()` 在 4xx/5xx 时抛出异常

### Common Patterns
```python
api = TicketApiClient(authorization=ctx.authorization)
result = await api.get("/api/v1/movies", params={"genre": "comedy"})
```

## Dependencies

### Internal
- `app/config.py` — `get_settings()`（base_url、timeout）

### External
- `httpx` 0.27 — 异步 HTTP 客户端

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
