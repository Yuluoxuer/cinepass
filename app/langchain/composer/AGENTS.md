<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# composer（卡片合成器）

## Purpose
管道末端节点，将 Planner 输出的 `plan`、`draft`、`tool_results`、`rag_hits` 合成为前端可渲染的结构：`reply_text`（自然语言回复）、`cards`（结构化卡片列表）、`progress`（购票进度条）、`need_login`（是否需要登录）。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `card_composer.py` | `compose_cards`：纯函数，根据 `plan.target_state` 映射回复文本与进度索引；MVP 阶段 `cards` 返回空列表 |

## For AI Agents

### Working In This Directory
- `compose_cards` 是纯函数（无 IO、无 LLM 调用），可直接单测
- `PROGRESS_STEPS`（`["选片","影院","场次","选座","支付"]`）与 `models/turn.py` 的 `ProgressVO` 默认值保持一致
- `need_login` 条件：`target in ("SelectSeat", "ConfirmOrder") and not merged.get("userId")`
- 扩展卡片类型：在 `compose_cards` 中按 `target_state` 填充 `cards` 列表，格式参照 `AgentCardVO`

### Testing Requirements
- 传入不同 `plan.target_state` 验证 `reply_text`、`progress.currentIndex`、`need_login` 的正确性

### Common Patterns
```python
composed = compose_cards(draft=draft, plan=plan, tool_results=[], rag_hits=[])
# composed: {reply_text, cards, progress, need_login, draft, events}
```

## Dependencies

### Internal
- `app/models/turn.py` — `AgentCardVO` 结构参考

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
