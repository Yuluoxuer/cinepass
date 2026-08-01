<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-31 | Updated: 2026-07-31 -->

# cinepass_leijieming_aiagent（AI 对话购票服务）

## Purpose
基于 FastAPI + LangChain/LangGraph 的 AI 对话购票 Agent 服务。接收前端的自然语言购票请求，通过 NLP 意图槽位提取、RAG 检索增强、确定性 Planner 规划、子 Agent Tools 调用票务中台，最终生成结构化卡片回复（电影卡、座位卡、订单确认卡等）。

## Key Files

| File | Description |
|------|-------------|
| `requirements.txt` | Python 依赖（FastAPI 0.115, uvicorn, LangChain 0.3, LangGraph 0.2, pypdf, httpx 等） |
| `README.md` | 项目说明与启动文档 |
| `__init__.py` | 包入口 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `app/` | 全部应用代码（见 `app/AGENTS.md`） |
| `storage/` | RAG 持久化存储：`documents/`（源 PDF）、`vectorstore/`（ChromaDB）、`cache/`（临时） |
| `docs/` | Agent 设计文档 |

## For AI Agents

### Working In This Directory
- **Python 环境**：使用 uv 或 conda（修改前先询问用户），不要私自 `pip install`
- **服务启动**：`uvicorn app.main:app --reload --port 8001`
- **健康检查**：`curl http://localhost:8001/health` → `{"status":"ok"}`
- **架构约定（ADR-0001）**：LLM 只能通过具名 Tools 调用中台；禁止 Agent 直接写数据库或绕过后端
- RAG 向量库在 `storage/vectorstore/`（ChromaDB）；源文档 PDF 放 `storage/documents/`

### Testing Requirements
- `pytest`（需激活对应 Python 环境）
- 启动后 `curl http://localhost:8001/health` 验证

### Common Patterns
- 所有对外接口在 `app/api/v1/`
- LangChain 管道：NLP → Planner → Tools → Composer（见 `app/langchain/AGENTS.md`）
- 后端调用封装在 `app/clients/`（httpx）
- 配置统一在 `app/config.py`（pydantic-settings，从 `.env` 读取）

## Dependencies

### External
- `fastapi` 0.115.0 — API 框架
- `uvicorn[standard]` 0.30.6 — ASGI 服务器
- `langchain-core` 0.3.29 + `langchain-community` 0.3.7 — LLM 编排
- `langgraph` 0.2.60 — 状态机图执行
- `httpx` 0.27.2 — 调用票务中台 REST API
- `pypdf` 4.3.1 — 加载 FAQ/政策 PDF 至 RAG
- `pydantic-settings` 2.5.2 — 环境配置管理

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
