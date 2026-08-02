<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# cinepass_leijieming_aiagent（妙语购票 AI Agent 服务）

## Purpose
基于 FastAPI + LangChain/LangGraph 的 AI 对话购票 Agent 服务（ticket-agent）。接收前端自然语言购票请求，经 NLP 意图槽位提取 → RAG FAQ 检索 → 确定性 Planner → 子 Agent Tools 调用票务中台 → Composer 生成结构化卡片回复（电影卡、座位卡、订单确认卡等）。对话消息存 Agent 自有 PostgreSQL 库；库存/订单/Draft 真相在 ticket-api（Java）。

## Key Files

| File | Description |
|------|-------------|
| `.env.example` | 环境变量模板（`DATABASE_URL`、`TICKET_API_BASE_URL`、`OPENAI_API_KEY` 等） |
| `.gitignore` | 忽略 `.env`、`storage/`、`__pycache__`、`.venv` 等 |
| `README.md` | 项目说明、快速启动、架构约束 |
| `pyproject.toml` | Python 依赖与构建配置 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `app/` | 全部应用代码（见 `app/AGENTS.md`） |
| `storage/` | 本地存储占位（向量库已迁至远端 Chroma，此目录正常为空） |

## For AI Agents

### Working In This Directory
- **Python 环境**：使用 `uv` 或 `conda`，修改前先询问用户所用环境，**不要私自执行 `pip install`**
- **服务启动**：`uvicorn app.main:app --reload --port 8001`
- **健康检查**：`curl http://localhost:8001/health` → `{"code":0,"data":{"status":"ok"}}`
- **硬约束**：LLM 只能通过具名 Tools 调用中台；禁止 Agent 直接写数据库或绕过后端
- 对话记录落 Agent 自有 PostgreSQL（`DATABASE_URL`）；**不**经 ticket-api 的 `X-Internal-Api-Key`
- RAG 向量库由远端 Chroma 提供；`storage/` 下不再落本地 vectorstore

### Testing Requirements
- `pytest`（需先激活对应 Python 环境）
- 启动服务后 `curl http://localhost:8001/health` 验证
- `POST http://localhost:8001/api/v1/agent/turns` Body `{"message":"周末想看喜剧"}` 验证完整 Turn 链路

### Common Patterns
- 所有对外接口在 `app/api/v1/`（目前仅 `/agent/turns`）
- LangChain 管道定义在 `app/langchain/`，入口为 `run_booking_graph`
- 中台调用封装在 `app/clients/ticket_api.py`（httpx，透传用户 `Authorization`）
- 全局配置在 `app/config.py`（pydantic-settings，从 `.env` 读取，`lru_cache` 单例）

## Dependencies

### External
- `fastapi` 0.115 — API 框架
- `uvicorn[standard]` 0.30 — ASGI 服务器
- `langchain-core` 0.3 + `langchain-community` 0.3 — LLM 编排抽象
- `langgraph` 0.2 — 状态机图执行（MVP 顺序执行，后续改 `StateGraph`）
- `httpx` 0.27 — 调用票务中台 REST API
- `pypdf` 4.3 — 加载 FAQ/政策 PDF 至 RAG
- `pydantic-settings` 2.5 — 环境配置管理
- `sqlalchemy` + `psycopg2` — Agent 自有 PostgreSQL 对话库

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->