"""按 Draft 完备度决定下一步 Tool 批次。"""
from __future__ import annotations

from typing import Any


BOOKING_STEPS = ("SelectMovie", "SelectCinema", "SelectShow", "SelectSeat", "ConfirmOrder")


def first_incomplete_step(draft: dict[str, Any]) -> str:
    if not draft.get("movieId"):
        return "SelectMovie"
    if not draft.get("cinemaId"):
        return "SelectCinema"
    if not draft.get("showId"):
        return "SelectShow"
    if not draft.get("lockId"):
        return "SelectSeat"
    if not draft.get("orderId"):
        return "ConfirmOrder"
    return "PayMock"


def plan_next_actions(
    *,
    draft: dict[str, Any],
    intent: str,
    slot_patch: dict[str, Any],
    card_action: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """返回 plan：target_state + tool_calls（骨架空列表，后续按状态机填充）。"""
    merged = {**draft, **(slot_patch or {})}
    if card_action and card_action.get("itemId"):
        action_id = card_action.get("actionId")
        item_id = card_action["itemId"]
        # 粗粒度：列表选型写入对应槽（正式实现按 card.type 映射）
        if action_id == "select" and not merged.get("movieId"):
            merged["movieId"] = item_id
        elif action_id == "select" and not merged.get("cinemaId"):
            merged["cinemaId"] = item_id
        elif action_id == "select" and not merged.get("showId"):
            merged["showId"] = item_id
        elif action_id == "payment_done":
            patch = card_action.get("draftPatch") or {}
            if patch.get("orderId"):
                merged["orderId"] = patch["orderId"]
                return {
                    "target_state": "TicketIssued",
                    "tool_calls": [{"tool": "getOrder", "args": {"orderId": patch["orderId"]}}],
                    "merged_draft": merged,
                }

    target = first_incomplete_step(merged)
    tool_calls: list[dict[str, Any]] = []
    if target == "SelectMovie":
        tool_calls.append({"tool": "searchMovies", "args": {"genre": merged.get("genre")}})
    elif target == "SelectCinema":
        tool_calls.append({"tool": "searchCinemas", "args": {}})
    elif target == "SelectShow":
        tool_calls.append(
            {
                "tool": "listShows",
                "args": {
                    "cinemaId": merged.get("cinemaId"),
                    "movieId": merged.get("movieId"),
                    "date": merged.get("date"),
                },
            }
        )
    elif target == "SelectSeat":
        tool_calls.append({"tool": "recommendSeats", "args": {"showId": merged.get("showId")}})
    elif target == "ConfirmOrder":
        tool_calls.append({"tool": "createOrder", "args": {"lockId": merged.get("lockId")}})

    if intent == "chitchat":
        tool_calls = []

    return {
        "target_state": target,
        "tool_calls": tool_calls,
        "merged_draft": merged,
        "intent": intent,
    }
