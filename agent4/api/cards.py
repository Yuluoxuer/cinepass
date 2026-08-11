"""agent4 卡片生成：把子 agent 的工具调用结果映射为前端四种动态卡片。"""
from __future__ import annotations

import ast
import uuid
from typing import Any


def _parse_tool_result(content: str) -> Any:
    try:
        return ast.literal_eval(content)
    except Exception:
        return None


def build_cards(tool_calls: list[dict[str, Any]], draft: dict[str, Any]) -> list[dict[str, Any]]:
    """按购票阶段生成动态卡片（电影/影院/场次/座位），仅购票相关工具调用才会出卡。"""
    cards: list[dict[str, Any]] = []
    for tc in tool_calls:
        name = tc.get("name", "")
        raw = _parse_tool_result(tc.get("content", ""))
        if not isinstance(raw, dict):
            continue
        # 中台信封 {code, message, data}，解包内层
        data = raw.get("data") if isinstance(raw.get("data"), dict) else raw
        if not isinstance(data, dict):
            continue

        if name in ("search_movies", "searchMovies") and not draft.get("movieId"):
            items = data.get("items") or []
            if isinstance(items, list) and items:
                cards.append({
                    "cardId": f"movie_{uuid.uuid4().hex[:8]}",
                    "type": "movie_list",
                    "title": "为您找到这些电影",
                    "payload": {"movies": items},
                    "actions": [
                        {"actionId": "select", "label": "选这部", "itemId": m.get("movieId", ""),
                         "draftPatch": {"movieId": m.get("movieId"), "filmTitle": m.get("title")}}
                        for m in items if isinstance(m, dict) and m.get("movieId")
                    ],
                })
        elif name in ("searchCinemas", "search_cinemas") and draft.get("movieId") and not draft.get("cinemaId"):
            items = data.get("items") or []
            if isinstance(items, list) and items:
                cards.append({
                    "cardId": f"cinema_{uuid.uuid4().hex[:8]}",
                    "type": "cinema_list",
                    "title": "附近影院",
                    "payload": {"cinemas": items},
                    "actions": [
                        {"actionId": "select", "label": "选这家", "itemId": c.get("cinemaId", ""),
                         "draftPatch": {"cinemaId": c.get("cinemaId"), "cinemaName": c.get("name")}}
                        for c in items if isinstance(c, dict) and c.get("cinemaId")
                    ],
                })
        elif name in ("list_shows", "listShows") and draft.get("cinemaId") and not draft.get("showId"):
            items = data.get("items") or []
            if isinstance(items, list) and items:
                cards.append({
                    "cardId": f"show_{uuid.uuid4().hex[:8]}",
                    "type": "show_list",
                    "title": "可选场次",
                    "payload": {"shows": items},
                    "actions": [
                        {"actionId": "select", "label": "选这场", "itemId": s.get("showId", ""),
                         "draftPatch": {"showId": s.get("showId"), "date": draft.get("date")}}
                        for s in items if isinstance(s, dict) and s.get("showId")
                    ],
                })
        elif name in ("getSeatMap", "get_seat_map", "recommendSeats", "recommend_seats") and draft.get("showId"):
            is_reco = name in ("recommendSeats", "recommend_seats")
            show_id = data.get("showId") or draft.get("showId") or ""
            seat_map = data if not is_reco else None
            plans = data.get("plans") if is_reco and isinstance(data.get("plans"), list) else []
            compromise = data.get("compromise") if is_reco else None
            cards.append({
                "cardId": f"seat_{uuid.uuid4().hex[:8]}",
                "type": "seat_plans",
                "title": "选择座位",
                "payload": {
                    "showId": show_id,
                    "count": int(draft.get("count") or 2),
                    "seatMap": seat_map,
                    "plans": plans,
                    "compromise": compromise,
                },
                "actions": [
                    {"actionId": "confirm", "label": "确认选座", "itemId": show_id,
                     "draftPatch": {"showId": show_id, "seatIds": []}}
                ],
            })

    # 去重：同类型同内容的卡片只保留一张
    seen: set[tuple[str, tuple[str, ...]]] = set()
    deduped: list[dict[str, Any]] = []
    for c in cards:
        payload = c.get("payload") or {}
        items = payload.get("movies") or payload.get("cinemas") or payload.get("shows") or []
        ids = tuple(sorted(
            str(it.get("movieId") or it.get("cinemaId") or it.get("showId") or "")
            for it in items if isinstance(it, dict)
        ))
        key = (c.get("type", ""), ids)
        if key in seen:
            continue
        seen.add(key)
        deduped.append(c)
    return deduped
