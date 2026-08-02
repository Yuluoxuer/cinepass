<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# nlp（意图与槽位提取）

## Purpose
从用户自然语言消息中提取 `intent`（意图）和 `slots`（槽位）。MVP 阶段使用关键词规则降级实现；P1 计划接入 LLM `with_structured_output`。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `langchain_nlp.py` | `extract_intent_slots(message)`：async 函数，返回 `{"intent": str, "slots": dict}` |

## For AI Agents

### Working In This Directory
- **当前 intent 枚举**：`buy_ticket`（默认）、`cancel`、`modify`、`browse`
- **当前 slots 字段**：`genre`（电影类型）、`count`（张数）、`date`（日期）、`timeWindow`（时段）
- **升级为 LLM 版本**：将函数体替换为 `llm.with_structured_output(IntentSlots).ainvoke(message)`，保持函数签名不变
- 规则关键词在文件顶部的常量中定义，扩展新意图只需修改常量

### Testing Requirements
- 单测：传入含关键词的句子，验证 `intent` 和 `slots` 正确提取
- 边界：空字符串应返回 `{"intent": "buy_ticket", "slots": {}}`

### Common Patterns
```python
result = await extract_intent_slots("明天下午两张喜剧")
# {"intent": "buy_ticket", "slots": {"genre": "喜剧", "count": 2, "date": "tomorrow", "timeWindow": "afternoon"}}
```

## Dependencies

### External
- `langchain-core` — 后续 LLM 接入时使用

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
