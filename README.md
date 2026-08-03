# Agent Service

独立 Agent 服务：FastAPI（SSE）+ LangGraph / SubAgent 框架。不依赖票务中台。

## 目录

```text
fapi/                   # HTTP 层（包名不能叫 fastapi，会遮蔽 PyPI）
├── main.py             # uvicorn fapi.main:app
├── api/chat.py         # POST /api/v1/chat · /api/v1/chat/stream (SSE)
└── models/chat.py
agent/                  # 编排层
├── langgraph/          # StateGraph + stream_chat / run_chat
├── subagent/           # ChatSubAgent / HelperSubAgent
├── tools/              # 本地示例 Tool
└── llm.py
```

```text
Browser/Client
  └─ SSE/REST ─► fapi (FastAPI)
                    └─ agent.stream_chat / run_chat
                         └─ LangGraph router → SubAgent
```

## Quick start

```bash
python3.12 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # 可选：配置 OPENAI_API_KEY
uvicorn fastapi.main:app --reload --port 8001
```

- 健康检查：`GET /health`
- 非流式：`POST /api/v1/chat` Body `{"message":"你好"}`
- SSE：`POST /api/v1/chat/stream` Body `{"message":"现在几点"}`

请求头携带前端 JWT（可选，SubAgent 出站请求会透传）：

```http
Authorization: Bearer <jwt>
```

SSE 事件：`route` → `token*` → `done`（出错为 `error`）。

未配置 `OPENAI_API_KEY` 时走离线回退；含「时间 / echo」的消息路由到 `helper` SubAgent。
