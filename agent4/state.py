"""agent4 购票流程图的状态。

流程阶段（``stage``）：
- ``intent``：意图识别（新消息进入，识别购票 or 聊天）
- ``collect``：上一轮追问后，用户补充了信息，重新提取草稿
- ``confirm``：草稿已完整，展示给用户确认
- ``pay``：用户已确认，进入锁座+支付
- ``chat``：非购票对话
"""
from __future__ import annotations

from typing import Annotated, Any, Literal, TypedDict


def _replace(left, right):
    """每轮/每节点覆盖，避免跨轮无限追加。"""
    return right if right is not None else left


class Agent4State(TypedDict, total=False):
    # ---- 输入 ----
    message: str                       # 用户本轮消息
    sessionId: str
    authorization: str | None
    latitude: float | None
    longitude: float | None

    # ---- 流程控制 ----
    stage: str                         # intent/collect/confirm/pay/chat
    intent: Literal["booking", "chat"] | None   # 意图识别结果
    optimized_message: str             # LLM 优化后的用户消息
    missing: list[str]                 # 草稿缺失字段
    confirmed: bool                    # 用户是否已确认下单

    # ---- 数据 ----
    bookingdraft: Annotated[dict[str, Any], _replace]  # 购票草稿（每节点覆盖：extract 输出完整草稿，含清空）
    history: Annotated[list[dict[str, str]], _replace]      # 对话历史
    reply_parts: Annotated[list[str], _replace]             # 本轮回复片段
    cards: Annotated[list[dict[str, Any]], _replace]        # 动态卡片

    # ---- 内部 ----
    steps: Annotated[int, _replace]    # 循环步数（防死循环）
    last_reply: Annotated[str, _replace]  # 上一步输出
