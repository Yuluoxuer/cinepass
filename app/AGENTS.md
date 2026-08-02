<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# app（应用核心代码）

## Purpose
ticket-agent 服务的全部应用代码。以 FastAPI 为 Web 框架，`main.py` 为入口，启动时初始化 PostgreSQL 数据库（`init_db`）。各子模块职责明确：`api/` 暴露 HTTP 接口，`langchain/` 承载 AI 编排管道，`clients/` 封装对票务中台的 HTTP 调用，`db/` 管理 Agent 自有对话库，`models/` 定义契约数据模型。

## Key Files

| File | Description |
|------|-------------|
| `main.py` | FastAPI 应用工厂（`create_app`）：注册 CORS、`/health` 端点、`lifespan` 启动钩子（调用 `init_db`） |
| `config.py` | `Settings`（pydantic-settings）：从 `.env` 读取所有配置；`get_settings()` 以 `lru_cache` 返回单例 |
| `__init__.py` | 包入口（空） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `api/` | HTTP 路由层，版本 v1（见 `api/AGENTS.md`） |
| `clients/` | 中台 HTTP 客户端封装（见 `clients/AGENTS.md`） |
| `db/` | SQLAlchemy 模型与仓储：建表（`init_db`）、会话工厂（`SessionLocal`）、消息读写（`message_repo`） |
| `langchain/` | LangChain/LangGraph AI 编排管道（见 `langchain/AGENTS.md`） |
| `models/` | Pydantic 请求/响应契约模型（见 `models/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- **入口文件**：`main.py` — 新增全局中间件、生命周期事件在此处注册
- **配置扩展**：新增配置项只改 `config.py` 的 `Settings` 类，同时更新 `.env.example`；**禁止硬编码 API Key 或 URL**
- **`db/` 目录**：`init_db()` 服务启动时自动建表；对话历史读写通过 `message_repo`，不直接操作 ORM model
- **CORS**：当前 `allow_origins=["*"]`，生产部署需缩窄
- **禁止**：不在 `app/` 根下放业务逻辑；业务逻辑分别归属 `langchain/`（AI 管道）或 `clients/`（中台调用）

### Testing Requirements
- 单元测试用 `pytest`，启动前需激活 Python 环境（询问用户 uv 或 conda）
- `create_app()` 可在测试中直接调用，结合 `httpx.AsyncClient` 做集成测试
- `config.py` 测试时可用 `monkeypatch.setenv` 覆盖环境变量

### Common Patterns
- 统一响应包装：`from app.models.response import ok`
- 配置访问：`from app.config import get_settings; settings = get_settings()`
- 数据库会话：`db = SessionLocal()` → `try/finally db.close()`
- 请求级上下文（`Authorization`、`X-Request-Id`）通过 `api/deps.py` 的 `RequestContext` 传递

## Dependencies

### Internal
- `app/langchain/turn/process_turn.py` — `agent_turns` 路由的核心调用链
- `app/db/` — `init_db`、`SessionLocal`、`message_repo`

### External
- `fastapi` 0.115 — Web 框架
- `pydantic-settings` 2.5 — 环境配置
- `sqlalchemy` + `psycopg2` — PostgreSQL ORM

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->