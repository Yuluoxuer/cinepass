<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# langchain（LangChain/LangGraph 编排层）

## Purpose
AI 对话购票的核心编排逻辑，实现从用户消息到结构化卡片回复的完整管道。管道顺序：card_action 优先于 NLP → NLP 意图槽位提取 → RAG FAQ 检索 → 确定性 Planner → 子 Agent Tools 调用 → Composer 生成卡片与回复文本。入口为 `graph/booking_graph.py` 的 `run_booking_graph`。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `agents/` | 子 Agent 类：`MovieAgent`、`CinemaAgent`、`ShowAgent`、`SeatAgent`、`OrderAgent`（见 `agents/AGENTS.md`） |
| `composer/` | `card_composer.py`：将 draft、plan、tool_results、rag_hits 合成回复（见 `composer/AGENTS.md`） |
| `graph/` | `booking_graph.py`（`run_booking_graph` 主图）、`state.py`（`BookingGraphState`）（见 `graph/AGENTS.md`） |
| `nlp/` | `langchain_nlp.py`：规则降级 MVP，提取 intent 与 slots；后续接 LLM（见 `nlp/AGENTS.md`） |
| `planner/` | `planner.py`：确定性规划器，不调用 LLM，按 Draft 缺失字段决定下一步（见 `planner/AGENTS.md`） |
| `rag/` | `retriever.py`（FAQ 检索）、`document_loader.py`（语料加载）（见 `rag/AGENTS.md`） |
| `tools/` | 各领域 Tool 函数：movie/cinema/show/seat/order，及 `context.py`（见 `tools/AGENTS.md`） |
| `turn/` | `process_turn.py`：对话轮次编排，hydrate Draft → 运行图 → 写回（见 `turn/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- **管道顺序不可随意更改**：card_action 优先于 NLP → RAG → Planner → Tools → Composer（见 `graph/booking_graph.py`）
- **新增领域 Tool**：在 `tools/` 添加文件，通过 `app/clients/` 调用后端，**禁止 Tool 直接操作数据库**
- **新增子 Agent**：在 `agents/` 添加，绑定对应 Tools
- **Planner 是确定性的**（不调用 LLM），不要将推理逻辑移入 LLM
- **RAG 仅用于 FAQ/政策检索**，事实性业务数据走 Tools
- `BookingGraphState` 是 TypedDict（total=False），所有节点函数均接受并返回此类型

### Testing Requirements
- `pytest` 单元测试各节点
- 测试 `run_booking_graph` 时可传入 mock state 验证管道输出
- NLP、Planner、Composer 均可独立单测

### Common Patterns
- 所有节点函数为 `async`，接受并返回 `BookingGraphState`
- Tool 函数通过 `ToolsContext` 持有的 `TicketApiClient` 调用中台
- Composer 输出固定结构：`{reply_text, cards, progress, need_login, draft?, events}`

## Dependencies

### Internal
- `app/clients/` — 各 Tool 通过此层调用票务中台 REST API
- `app/models/turn.py` — 对话轮次输入/输出模型
- `app/db/` — `message_repo` 读写对话历史（在 `turn/process_turn.py` 中调用）

### External
- `langchain-core` 0.3 + `langchain-community` 0.3 — LLM 调用抽象
- `langgraph` 0.2 — StateGraph 状态机（MVP 顺序执行，后续改 `add_node`/`add_edge`）
- `chromadb` — RAG 远端向量库客户端

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
