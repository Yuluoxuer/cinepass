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
uvicorn fapi.main:app --reload --port 8001
```

- 健康检查：`GET /health`（`checkpoint` 为 `postgres` 或 `off`）
- 非流式：`POST /api/v1/chat` Body `{"message":"你好"}`
- SSE：`POST /api/v1/chat/stream` Body `{"message":"现在几点"}`

### 短期记忆（Postgres Checkpointer）

在 `.env` 设置 `POSTGRES_URI`（见 `.env.example`）后，启动时会用官方 `AsyncPostgresSaver` 建表并挂到图上。

- `session_id` ↔ LangGraph `thread_id`；未传则服务端生成并在响应 / SSE 里回写
- 多轮对话请固定同一个 `session_id`；有 checkpoint 后可不传完整 `history`
- 未配置 `POSTGRES_URI` 时行为与以前一致（无状态，靠客户端 `history`）

```bash
# 示例
curl -s http://localhost:8001/api/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"我想看哪吒","session_id":"demo-1"}'
```

请求头携带前端 JWT（可选，SubAgent 出站请求会透传）：

```http
Authorization: Bearer <jwt>
```

`.env` 中 `BACKEND_BASE_URL`（默认 `http://127.0.0.1:8080`）供 Tools 调用中台。示例 Tool `get_current_user` → `GET /api/v1/auth/me`：

```bash
curl -s http://localhost:8001/api/v1/chat \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <jwt>' \
  -d '{"message":"我是谁"}'
```

SSE 事件：`route` → `token*` → `done`（出错为 `error`）；`session_id` 会出现在事件里。

未配置 `OPENAI_API_KEY` 时走离线回退；含「时间 / echo / 我是谁」的消息路由到 `helper` SubAgent。

### 影院查询

`CinemaAgent` 使用 `.env` 中的 `BACKEND_BASE_URL` 调用票务中台的只读接口：
`GET /api/v1/cinemas` 和 `GET /api/v1/cinemas/{cinemaId}`。查询附近影院时，Body 必须同时传
`latitude`、`longitude`（WGS84 坐标）；普通聊天与其他 Tool 不需要这两个字段。

```bash
# 查询附近影院（按距离排序）
curl -s http://localhost:8001/api/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"附近有什么影院","latitude":31.2989,"longitude":121.5140}'

# 查询指定影院详情（当前版本需要在消息中带影院 ID）
curl -s http://localhost:8001/api/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"查看影院详情 cinemaId=c12"}'
```

未配置 `OPENAI_API_KEY` 时，影院查询仍会按规则调用中台并返回格式化的纯文本结果；配置模型后，
模型仅可调用 `searchCinemas`、`getCinema` 两个影院只读 Tool。

### 终端直接对话

不需要启动 FastAPI；在项目根目录运行：

```powershell
.\.venv\python.exe cli.py
```

输入普通问题即可在终端看到流式回复。查询附近影院前，先设置位置：

```text
/location 31.2989 121.5140
帮我查附近影院
```

输入 `/location` 查看当前位置，输入 `/quit` 或 `/exit` 退出。若 `.env` 配置了
`POSTGRES_URI`，终端入口也会自动启用同一套对话记忆。
