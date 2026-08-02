<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# planner（确定性规划器）

## Purpose
根据当前 Draft 的完备度，确定性地决定下一步要执行的 Tool 调用批次。**不调用 LLM**，是纯粹的状态机逻辑。购票流程被拆解为五个有序步骤：SelectMovie → SelectCinema → SelectShow → SelectSeat → ConfirmOrder。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `planner.py` | `plan_next_actions`：返回 `{target_state, tool_calls, merged_draft, intent}`；`first_incomplete_step`：按 Draft 字段判断当前步骤 |

## For AI Agents

### Working In This Directory
- **Draft 字段→步骤映射**：`movieId` 缺失 → SelectMovie；`cinemaId` → SelectCinema；`showId` → SelectShow；`lockId` → SelectSeat；`orderId` → ConfirmOrder
- `card_action.actionId == "payment_done"` 时直接跳转到 `TicketIssued`，不走常规流程
- `intent == "chitchat"` 时 `tool_calls` 清空（不调用任何中台接口）
- 新增步骤：在 `BOOKING_STEPS` 元组中插入，并在 `first_incomplete_step` 和 `plan_next_actions` 中添加对应逻辑

### Testing Requirements
- 单测各 Draft 状态下的 `target_state` 和 `tool_calls` 输出
- 测试 `card_action` 的 `select`/`payment_done` 分支

### Common Patterns
```python
plan = plan_next_actions(
    draft={"movieId": "m1", "cinemaId": "c1"},  # showId 缺失
    intent="buy_ticket",
    slot_patch={},
)
# plan["target_state"] == "SelectShow"
# plan["tool_calls"] == [{"tool": "listShows", "args": {...}}]
```

## Dependencies
（无外部依赖，纯 Python 逻辑）

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
