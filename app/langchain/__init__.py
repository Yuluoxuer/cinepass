"""LangChain / LangGraph 编排层。

目录约定（对齐后端系分 §3 / §5.13，并按子 Agent / Graph / RAG 分文件夹）：
- agents/   子 Agent（Cinema / Movie / Show / Seat / Order）
- graph/    总 LangGraph 购票流程
- tools/    LangChain @tool → 中台 REST（无 pay）
- rag/      FAQ / 政策检索（P1）
- nlp/      意图与槽位 Structured Output
- planner/  确定性状态机（不经 LC ReAct 自由规划）
- composer/ 卡片与话术组装
- turn/     ProcessAgentTurn 入口
"""
