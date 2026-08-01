"""意图分类 + 槽位提取（系分 §5.2）。"""
from __future__ import annotations

from typing import Any


_GENRE_KEYWORDS = ("喜剧", "动作", "爱情", "科幻", "动画", "悬疑", "恐怖")


async def extract_intent_slots(message: str) -> dict[str, Any]:
    """MVP：规则降级；后续接 with_structured_output。"""
    text = (message or "").strip()
    intent = "buy_ticket"
    if any(k in text for k in ("取消", "不要了")):
        intent = "cancel"
    elif any(k in text for k in ("换", "太贵", "改")):
        intent = "modify"
    elif any(k in text for k in ("有什么", "推荐", "好看")):
        intent = "browse"

    slots: dict[str, Any] = {}
    for g in _GENRE_KEYWORDS:
        if g in text:
            slots["genre"] = g
            break
    if "两张" in text or "2张" in text:
        slots["count"] = 2
    if "明天" in text:
        slots["date"] = "tomorrow"
    if "下午" in text:
        slots["timeWindow"] = "afternoon"
    return {"intent": intent, "slots": slots}
