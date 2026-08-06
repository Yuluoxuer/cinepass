<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# tools（本地 Tools）

## Purpose
SubAgent 可调用的本地工具函数，不依赖外部服务。`__init__.py` 统一导出供 SubAgent 使用。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 导出 HELPER_TOOLS 与各 Tool |
| `sample_tools.py` | 本地 Tool：时间 / echo / JWT 状态 |
| `backend_tools.py` | 中台 HTTP Tool 示例（自动带 JWT） |

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

## Backend Tools（需 JWT）

- `backend_tools.py`：调用票务中台；JWT 由前端 `Authorization` → `fapi` → `use_authorization` → `agent.http` 自动附带
- 示例：`get_current_user` → `GET {BACKEND_BASE_URL}/api/v1/auth/me`
- 新增中台 Tool：在 `backend_tools.py` 用 `backend_url("/api/v1/...")` + `await get/post(...)`；不要手写 Authorization
