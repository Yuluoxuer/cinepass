<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# agents（子 Agent 类）

## Purpose
各业务领域的子 Agent 封装类。每个 Agent 类持有一个 `ToolsContext` 引用，将方法调用委托给 `tools/` 中对应的 Tool 函数。当前为骨架实现，后续可绑定 LangChain `AgentExecutor`。

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `movie_agent.py` | `MovieAgent`：`search_movies`、`recommend_movies`、`get_movie` |
| `cinema_agent.py` | `CinemaAgent`：影院搜索相关操作 |
| `show_agent.py` | `ShowAgent`：场次列表查询 |
| `seat_agent.py` | `SeatAgent`：座位推荐与锁定 |
| `order_agent.py` | `OrderAgent`：订单创建与查询 |

## For AI Agents

### Working In This Directory
- 每个 Agent 类只是对 `tools/` 函数的薄包装，不含业务逻辑
- 新增 Agent：创建新文件，构造函数接受 `tools_ctx: Any`，方法调用对应 Tool 函数
- 后续接入 LangChain `AgentExecutor` 时，在此处绑定 Tool 列表和 LLM

### Common Patterns
```python
class MovieAgent:
    name = "MovieAgent"
    def __init__(self, tools_ctx: Any) -> None:
        self._ctx = tools_ctx
    async def search_movies(self, **kwargs: Any) -> Any:
        return await movie_tools.search_movies(self._ctx, **kwargs)
```

## Dependencies

### Internal
- `app/langchain/tools/` — 各领域 Tool 函数

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
