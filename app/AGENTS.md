<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-31 | Updated: 2026-07-31 -->

# app（应用核心代码）

## Purpose
FastAPI 应用的全部业务代码。分为 API 路由层、LangChain 编排层、后端客户端层和数据模型层，共同实现"用户自然语言输入 → Agent 购票对话流 → 结构化卡片回复"的端到端流程。

## Key Files

| File | Description |
|------|-------------|
| `main.py` | FastAPI 应用入口：创建 app、注册 CORS、挂载 `api_router`，健康检查 `/health` |
| `config.py` | pydantic-settings 配置（从 `.env` 读取后端 URL、LLM 密钥、向量库路径等） |
| `__init__.py` | 包入口 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `api/` | FastAPI 路由定义，当前版本 v1 |
| `langchain/` | LangChain/LangGraph 编排管道：NLP → Planner → Tools → Composer（见 `langchain/AGENTS.md`） |
| `clients/` | 调用票务中台的 httpx HTTP 客户端封装 |
| `models/` | Pydantic 响应模型：`response.py`（统一 ok/err 包装）、`turn.py`（对话轮次模型） |

## For AI Agents

### Working In This Directory
- 所有环境变量通过 `app/config.py` 的 `get_settings()` 读取，**禁止在代码中硬编码 API Key 或 URL**
- 新增路由必须挂载到 `api/v1/api_router`
- `main.py` 的 CORS 当前为 `allow_origins=["*"]`，生产部署需缩窄

### Common Patterns
- 统一响应：`from app.models.response import ok, err`
- 配置访问：`from app.config import get_settings; settings = get_settings()`
- 后端 HTTP 调用：通过 `app/clients/` 中的异步 httpx 客户端

## Dependencies

### Internal
- `app/langchain/graph/` — 主图执行入口 `run_booking_graph()`
- `app/clients/` — 票务中台 HTTP 调用

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
