<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# fapi（HTTP / SSE 层）

## Purpose
对外 REST + SSE（基于 FastAPI 框架）。只负责协议与校验，对话编排全部委托 `agent` 包。

包名必须是 `fapi`，**不能**叫 `fastapi`：会遮蔽 PyPI 的 `fastapi`，导致 `from fastapi import FastAPI` 失败。

## Key Files

| File | Description |
|------|-------------|
| `main.py` | `uvicorn fapi.main:app` |
| `config.py` | `Settings`（`app_name` / `app_version` / `debug`） |
| `api/chat.py` | `POST /api/v1/chat`、`POST /api/v1/chat/stream`（SSE） |
| `models/chat.py` | ChatRequest / ChatResponse |

## For AI Agents

### Working In This Directory
- 新接口挂到 `api/` 并 include 进 `api_router`
- 流式对话只调用 `agent.stream_chat`，不要在路由里写业务编排
- 本地模块用 `from fapi.xxx import ...`；框架用 `from fastapi import FastAPI`

### Testing Requirements
- 启动：`uvicorn fapi.main:app --reload --port 8001`
- 健康检查：`GET /health`

## Dependencies

### Internal
- `agent` — `run_chat` / `stream_chat`

### External
- `fastapi` / `uvicorn` — HTTP 框架
