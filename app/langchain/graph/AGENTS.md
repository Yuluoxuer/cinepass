<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# graph（状态图）

## Purpose
购票对话的主状态图。`state.py` 定义共享状态 `BookingGraphState`（TypedDict）；`booking_graph.py` 实现 `run_booking_graph`，MVP 阶段为顺序执行五个节点，后续迁移为 LangGraph `StateGraph`。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `state.py` | `BookingGraphState`：TypedDict（total=False），定义全部流转字段 |
| `booking_graph.py` | `run_booking_graph`：顺序执行 card_action → NLP → RAG → Planner → Composer；`build_graph` 预留 LangGraph 入口 |

## For AI Agents

### Working In This Directory
- **`BookingGraphState` 字段说明**：
  - 输入：`session_id`、`message`、`card_action`、`authorization`、`draft`、`messages`
  - 中间：`intent`、`slot_patch`、`rag_hits`、`plan`、`tool_results`、`tool_traces`
  - 输出：`reply_text`、`cards`、`progress`、`need_login`、`events`
- `card_action` 优先级高于 `message`：有 `card_action` 时跳过 NLP，直接更新 `slot_patch`
- 迁移到 LangGraph 时：用 `build_graph` 返回编译后的图，替换 `run_booking_graph` 的顺序调用

### Testing Requirements
- 单测时构造最小 `BookingGraphState` 传入 `run_booking_graph`，验证 `reply_text` 和 `events`
- 测试 `card_action` 路径：传入含 `itemId` 的 card_action，验证 `slot_patch` 正确合并

### Common Patterns
```python
state: BookingGraphState = {
    "session_id": "sess_abc",
    "message": "想看喜剧",
    "draft": {},
    "events": [],
}
state = await run_booking_graph(state)
```

## Dependencies

### Internal
- `app/langchain/nlp/` — `extract_intent_slots`
- `app/langchain/rag/` — `retrieve_faq`
- `app/langchain/planner/` — `plan_next_actions`
- `app/langchain/composer/` — `compose_cards`

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
