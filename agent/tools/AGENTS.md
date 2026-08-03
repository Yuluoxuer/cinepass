<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# tools（本地 Tools）

## Purpose
SubAgent 可调用的本地工具函数，不依赖外部服务。`__init__.py` 统一导出供 SubAgent 使用。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 导出 `get_current_time`、`echo_text` |
| `sample_tools.py` | `get_current_time(tz_name)`：返回当前时间字符串；`echo_text(text)`：原样回显 |

## For AI Agents

### Working In This Directory
- 新增 Tool：在 `sample_tools.py`（或新建文件）中定义普通函数，再在 `__init__.py` 导出
- Tool 函数保持无副作用、无网络调用；需要外部 API 的 Tool 单独建文件并注明依赖
- 若接入 LangChain Tool 格式，用 `@tool` 装饰器包裹即可，`HelperSubAgent` 直接调用

### Common Patterns
```python
# 纯函数风格，带类型提示
def new_tool(param: str) -> str:
    """说明这个 Tool 做什么。"""
    ...

# __init__.py 导出
from agent.tools.sample_tools import new_tool
__all__ = [..., "new_tool"]
```

## Dependencies

### Internal
- 被 `agent/subagent/helper_agent.py` 调用

### External
- 仅标准库（`datetime`）

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
