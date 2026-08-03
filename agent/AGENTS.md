<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# agent（Agent 框架核心）

## Purpose
独立 Agent 服务的编排层：LangGraph 主工作流 + SubAgent 插件。不依赖票务中台；`fapi` 仅通过 `stream_chat` / `run_chat` 调用本包。

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `langgraph/` | StateGraph、状态、流式 runner |
| `subagent/` | 子 Agent 基类与示例（chat / helper） |
| `tools/` | SubAgent 本地 Tools（时间、echo） |

## Key Files

| File | Description |
|------|-------------|
| `llm.py` | ChatModel 工厂；无 `OPENAI_API_KEY` 时离线回退 |
| `settings.py` | Agent 侧 LLM 配置 |

## For AI Agents

### Working In This Directory
- 新增 SubAgent：在 `subagent/` 实现 `SubAgent`，并在 `get_subagents()` 注册；在 `langgraph/graph.py` 增加节点与路由
- 新增 Tool：放 `tools/`，由对应 SubAgent 调用
- **禁止**本包 import `fapi.*`（边界：HTTP 在 fapi，编排在 agent）

### Common Patterns
```python
from agent import stream_chat
async for event in stream_chat("现在几点"):
    ...  # route | token | done
```
