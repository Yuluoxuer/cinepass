# ticket-agent — 妙语购票 Python LangChain 服务

对齐：
- 前端系分 `docs/02-前端系分-购票UI与Agent壳.md` §7（`POST /api/v1/agent/turns`）
- 后端系分 `docs/01-后端系分-票务中台与Agent.md` §5.13（独立部署、Tools 回调中台）

## 目录

```text
app/
├── main.py                 # FastAPI 入口（启动时 init_db）
├── config.py
├── api/v1/agent.py         # POST /api/v1/agent/turns
├── db/                     # SQLAlchemy：agent_session / agent_message（PostgreSQL）
├── clients/ticket_api.py   # httpx → ticket-api（Draft + Tools；无 M2M Key）
└── langchain/              # Turn / Graph / Tools / RAG …
```

## 硬约束

1. 库存/支付/Draft 真相在 **ticket-api（Java）**；本服务只编排 + 存对话。
2. Tool 白名单**不含** pay；支付由前端显式调中台。
3. **对话消息**落在 Agent 自有库（PostgreSQL + SQLAlchemy），**不再**经中台 `X-Internal-Api-Key`。
4. **RAG 向量检索**走远端 Chroma；本仓库不落本地 vectorstore。

## Quick start

```bash
python3.12 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # 配置 DATABASE_URL（PostgreSQL）
uvicorn app.main:app --reload --port 8000
```

健康检查：`GET /health`  
Turn：`POST /api/v1/agent/turns` Body `{ "message": "周末想看个喜剧" }`
