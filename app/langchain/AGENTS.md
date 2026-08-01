<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-31 | Updated: 2026-07-31 -->

# langchain（LangChain/LangGraph 编排层）

## Purpose
AI 对话购票的核心编排逻辑，实现从用户消息到结构化卡片回复的完整管道。管道顺序：NLP 意图槽位提取 → RAG FAQ 检索 → 确定性 Planner → 子 Agent Tools 调用 → Composer 生成卡片与回复文本。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `graph/` | 购票状态图骨架（`run_booking_graph` 串联全部节点），`state.py` 定义 `BookingGraphState` |
| `nlp/` | `langchain_nlp.py`：调用 LLM 提取意图（intent）与槽位（slots）：电影名、影院、日期、场次、座位数 |
| `planner/` | `planner.py`：确定性规划器，根据 draft 状态与 intent 输出下一步 action 列表（不调用 LLM） |
| `rag/` | `document_loader.py`（加载 FAQ PDF）、`retriever.py`（ChromaDB 向量检索） |
| `agents/` | 子 Agent：`movie_agent.py`、`cinema_agent.py`、`show_agent.py`、`seat_agent.py`、`order_agent.py` |
| `tools/` | 各领域 Tool：`movie_tools.py`、`cinema_tools.py`、`show_tools.py`、`seat_tools.py`、`order_tools.py`，及 `context.py` |
| `composer/` | `card_composer.py`：将 draft、plan、tool_results、rag_hits 合成 `reply_text`、`cards`、`progress` |
| `turn/` | 对话轮次状态管理 |

## For AI Agents

### Working In This Directory
- **管道顺序不可随意更改**：card_action 优先于 NLP → RAG → Planner → Tools → Composer（见 `graph/run_booking_graph`）
- **新增领域 Tool**：在 `tools/` 添加文件，通过 `app/clients/` 调用后端，**禁止 Tool 直接操作数据库**
- **新增子 Agent**：在 `agents/` 添加，绑定对应 Tools
- **Planner 是确定性的**（不调用 LLM），负责根据 draft 缺失字段决定下一步；避免将推理逻辑移入 LLM
- **RAG 仅用于 FAQ/政策检索**，事实性业务数据（场次、座位）走 Tools

### Testing Requirements
- `pytest` 单元测试各节点
- 测试 `run_booking_graph` 时可传入 mock state 验证管道输出

### Common Patterns
- 所有节点函数为 `async`，接受并返回 `BookingGraphState`（dict 子类）
- Tool 函数使用 httpx 异步客户端，异常时返回 `{"error": "..."}` 而非抛出
- Composer 输出固定结构：`{reply_text, cards, progress, need_login, draft?}`

## Dependencies

### Internal
- `app/clients/` — 各 Tool 通过此层调用票务中台 REST API
- `app/models/turn.py` — 对话轮次输入模型

### External
- `langchain-core` 0.3 + `langchain-community` 0.3 — LLM 调用抽象
- `langgraph` 0.2 — StateGraph 状态机（MVP 顺序执行，后续改 `add_node`/`add_edge`）
- `chromadb`（via `langchain-community`）— RAG 向量库

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
