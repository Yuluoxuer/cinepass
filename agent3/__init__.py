"""agent3：监督者（Supervisor）模式的购票助手。

主 agent（supervisor）用 LLM 决定每一轮调用哪个子 agent（movie/cinema/show/seat/order/chat/helper），
子 agent 各自是 ``create_react_agent``，工具调用结果映射为前端动态卡片。
"""
from agent3.graph import get_agent3
from agent3.state import Agent3State

__all__ = ["get_agent3", "Agent3State"]
