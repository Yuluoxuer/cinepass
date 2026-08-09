"""agent4：监督者（Supervisor）模式的购票助手，自包含框架。

结构：
- ``api/``：外部调用 agent4 的 FastAPI 端点（/agent4）+ 前端契约/卡片
- ``graph/``：监督者图组装 + 短期记忆（PostgresSaver）
- ``SubAgents/``：7 个子 agent（chat/movie/cinema/show/seat/order/helper）
- ``tools/AgentTools/``：子 agent 业务工具（对 Java 中台的业务调用）
- ``tools/Http2BackendTools/``：访问 Java 后端的底层（HTTP + JWT 获取/携带）
- ``skills/``：子 agent 角色定义与指令（markdown）
- ``config.py`` / ``llm.py``：配置与 DeepSeek LLM
- ``MainAgent.py``：监督者 agent
"""
from agent4.graph import get_agent4

__all__ = ["get_agent4"]
