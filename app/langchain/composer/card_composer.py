"""根据 plan / tool 结果生成前端卡片（系分 §7.3）。"""
from __future__ import annotations

from typing import Any

PROGRESS_STEPS = ["选片", "影院", "场次", "选座", "支付"]

_STATE_INDEX = {
    "Idle": 0,
    "SelectMovie": 0,
    "SelectCinema": 1,
    "SelectShow": 2,
    "SelectSeat": 3,
    "ConfirmOrder": 4,
    "PayMock": 4,
    "TicketIssued": 5,
}


def compose_cards(
    *,
    draft: dict[str, Any],
    plan: dict[str, Any],
    tool_results: list[dict[str, Any]],
    rag_hits: list[dict[str, Any]],
) -> dict[str, Any]:
    _ = (tool_results, rag_hits)
    target = plan.get("target_state", "SelectMovie")
    merged = plan.get("merged_draft") or draft or {}
    idx = _STATE_INDEX.get(target, 0)

    reply_map = {
        "SelectMovie": "想看点什么类型？我可以帮你挑几部。",
        "SelectCinema": "选好影片了，接下来选一家影院吧。",
        "SelectShow": "选好影院了，看看有哪些场次。",
        "SelectSeat": "场次已定，我来帮你推荐座位方案。",
        "ConfirmOrder": "座位已锁，请确认订单。",
        "PayMock": "订单已创建，请扫码支付（我不会代付）。",
        "TicketIssued": "出票成功，祝观影愉快！",
    }
    return {
        "reply_text": reply_map.get(target, "你好，我是妙语助手，可以帮你订票。"),
        "cards": [],
        "progress": {
            "steps": PROGRESS_STEPS,
            "currentIndex": min(idx, 5),
            "state": target,
        },
        "need_login": target in ("SelectSeat", "ConfirmOrder") and not merged.get("userId"),
        "events": ["card_show"] if target != "Idle" else [],
        "draft": {**merged, "state": target},
    }
