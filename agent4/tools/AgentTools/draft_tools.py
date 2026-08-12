"""中台购票草稿 Tools：读写中台 /booking-drafts/{sid}，与手动购票页面共用同一份草稿。

页面手动选的影片/影院/场次/座位，agent4 下一轮直接读取中台即是最新。
"""
from __future__ import annotations

import json
from typing import Any

from langchain.tools import tool

from agent4.tools.Http2BackendTools.http import backend_url, get, post
from agent4.tools.AgentTools.booking_draft import get_session_id


async def load_draft(sid: str | None = None) -> dict[str, Any]:
    """从中台读取当前草稿；失败返回空 dict。"""
    sid = sid or get_session_id()
    if not sid:
        return {}
    try:
        payload = await get(backend_url(f"/booking-drafts/{sid}"), timeout=1.0)
        if isinstance(payload, dict) and payload.get("code") in (0, 200, None):
            data = payload.get("data")
            if isinstance(data, dict):
                return data
    except Exception:
        pass
    return {}


async def save_draft(draft: dict[str, Any], sid: str | None = None) -> dict[str, Any]:
    """把草稿合并写回中台；失败返回原草稿。"""
    sid = sid or get_session_id()
    if not sid:
        return draft
    try:
        payload = await post(
            backend_url(f"/booking-drafts/{sid}/merge"),
            json={"draft": draft},
            timeout=1.0,
        )
        if isinstance(payload, dict) and payload.get("code") in (0, 200, None):
            data = payload.get("data")
            if isinstance(data, dict):
                return data
    except Exception:
        pass
    return draft


@tool
async def get_booking_draft() -> str:
    """查看当前购票草稿（与手动购票页面同步）。返回原始 JSON（movieId/cinemaId/showId/seatIds 等字段）。"""
    draft = await load_draft()
    public = {k: v for k, v in draft.items() if not str(k).startswith("_")}
    return json.dumps(public, ensure_ascii=False) if public else "{}"


@tool
async def update_booking_draft(field: str, value: str) -> str:
    """记录购票草稿中的一个字段（已设置的字段不允许覆盖）。
    可用字段：movieId/filmTitle、cinemaId/cinemaName、showId、date、timeWindow、count、seatIds。
    每次用户补充信息后都要调用本工具记录。"""
    field = field.strip()
    value = value.strip()
    if not field:
        return "字段名不能为空。"
    draft = await load_draft()
    if field in draft and draft[field]:
        return f"字段 {field} 已设置为 {draft[field]}，请不要覆盖。如需更换，请先调用 clearBookingDraft 清空后重设。"
    draft[field] = value
    await save_draft(draft)
    return json.dumps({k: v for k, v in draft.items() if not str(k).startswith("_")}, ensure_ascii=False)


@tool
async def clear_booking_draft() -> str:
    """清空当前购票草稿（下单完成后调用）。"""
    await save_draft({})
    return "购票草稿已清空。"
