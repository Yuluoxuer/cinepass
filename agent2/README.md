# Movie Ticket Agent

基于LangChain ReAct Agent的电影票务智能助手

## 功能特性

- ✅ 电影信息查询（搜索、详情、推荐）
- ✅ 影院信息查询（附近影院、影院详情）
- ✅ 场次信息查询（排片、时间）
- ✅ 座位管理（座位图、推荐座位、锁定/解锁）
- ✅ 订单操作（创建、查询、取消）
- ✅ 对话历史持久化（PostgreSQL）
- ✅ 多轮对话记忆

## 技术栈

- **Agent框架**: LangChain 1.3.14 (ReAct Agent)
- **LLM**: DeepSeek Chat
- **数据库**: PostgreSQL (会话存储)
- **缓存**: Redis (可选)

## 安装

1. 安装依赖：
```bash
conda activate langchain
pip install asyncpg redis langchain-openai
```

2. 配置环境变量：
```bash
cp .env.example .env
# 编辑 .env 文件，填入你的配置
```

3. 初始化数据库：
```bash
psql -U postgres -d agent_db -f init_db.sql
```

## 使用

### 基本使用

```python
import asyncio
from agent import get_agent

async def main():
    agent = await get_agent()
    
    response = await agent.run(
        message="有什么热映电影？",
        session_id="user_123"
    )
    print(response)
    
    await agent.close()

asyncio.run(main())
```

### 运行测试

```bash
python test_agent.py
```

## 架构说明

### 核心组件

- `agent.py`: 主Agent类，封装ReAct Agent逻辑
- `checkpoint.py`: PostgreSQL状态存储
- `llm.py`: LLM配置
- `prompts.py`: Agent提示词模板
- `tools/`: 工具函数目录
- `config.py`: 配置管理

### 工作流程

1. 用户发送消息 → Agent接收
2. Agent从数据库加载历史消息
3. Agent使用ReAct循环：思考 → 选择工具 → 执行 → 观察结果
4. Agent生成回复
5. 保存对话到数据库

### 数据库结构

**sessions表**：
- session_id: 会话ID（主键）
- user_id: 用户ID
- created_at: 创建时间
- updated_at: 更新时间
- metadata: 元数据（JSONB）

**messages表**：
- id: 消息ID（主键）
- session_id: 会话ID（外键）
- role: 角色（user/assistant/system）
- content: 消息内容
- timestamp: 时间戳
- tool_calls: 工具调用记录（JSONB）

## 与原项目对比

| 方面 | 原项目(LangGraph) | 新项目(Agent) |
|------|------------------|---------------|
| 流程控制 | StateGraph固定流程 | Agent自主决策 |
| 意图识别 | LLM分类→路由subagent | LLM通过工具描述自选 |
| 状态管理 | GraphState | Memory + Checkpoint |
| 灵活性 | 受路由限制 | 完全灵活 |
| 维护性 | 需要修改图结构 | 只需添加工具 |

## License

MIT
