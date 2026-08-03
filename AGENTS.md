<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# cinepass_leijieming_aiagent（独立 Agent 服务）

## Purpose
基于 FastAPI + LangGraph 的独立 Agent 框架服务。HTTP/SSE 在 `fastapi/`，编排与子 Agent 在 `agent/`。不调用票务中台。

## Key Files

| File | Description |
|------|-------------|
| `requirements.txt` | Python 依赖 |
| `README.md` | 启动说明 |
| `.env.example` | 环境变量示例 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `fastapi/` | FastAPI：REST + SSE（见 `fastapi/AGENTS.md`） |
| `agent/` | LangGraph 工作流 + SubAgent + Tools（见 `agent/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- **Python 环境**：使用 uv 或 conda（修改前先询问用户），不要私自 `pip install`
- **服务启动**：`uvicorn fapi.main:app --reload --port 8001`
- **健康检查**：`curl http://localhost:8001/health` → `{"status":"ok",...}`
- 包名不要使用 `fastapi` / `langchain`（会遮蔽 PyPI）；HTTP 用 `fapi`，编排用 `agent`
- 新增能力：优先在 `agent/subagent` + `agent/langgraph` 扩展；`fapi` 只加薄路由

### Testing Requirements
- `pytest`（需激活对应 Python 环境）
- 启动后验证 `/health` 与 `/api/v1/chat`

## Dependencies

### External
- `fastapi` / `uvicorn` — HTTP + SSE
- `langgraph` / `langchain-core` / `langchain-openai` — 工作流与 LLM
- `pydantic-settings` — 配置

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
