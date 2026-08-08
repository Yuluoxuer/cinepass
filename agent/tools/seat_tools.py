"""SeatAgent Tools — 调用票务中台座位图/推荐/锁座/解锁接口。"""
from __future__ import annotations

from typing import Any

from langchain.tools import tool

from agent.http import backend_url, delete, get, post
from agent.tools._common import ToolError, require_auth, safe_api_call

# 向后兼容别名
SeatToolError = ToolError


# ---------- getSeatMap ----------

def format_seat_map(data: Any) -> str:
    """把座位图数据格式化为用户可读文本。"""
    if not isinstance(data, dict):
        return "座位图数据格式不正确，请稍后再试。"
    seats = data.get("seats") or []
    available = sum(1 for s in seats if isinstance(s, dict) and s.get("status") == "available")
    lines = [
        f"场次 {data.get('showId', '-')} 座位图：",
        f"- {data.get('rows', '?')} 排 × {data.get('cols', '?')} 列",
        f"- 票价：¥{data.get('price', '?')}",
        f"- 可选座位：{available} 个",
    ]
    legend = data.get("legend")
    if isinstance(legend, dict):
        lines.append(f"- 图例：{'、'.join(f'{k}={v}' for k, v in legend.items())}")
    return "\n".join(lines)


@tool("getSeatMap")
async def get_seat_map(show_id: str) -> str:
    """查看某场次的座位图。show_id 为场次ID。返回座位布局、可选数量和票价。"""
    data = await safe_api_call(
        get(backend_url(f"/shows/{show_id}/seat-map"), timeout=0.8),
        domain="座位",
        detail=True,
    )
    if isinstance(data, str):
        return data
    return format_seat_map(data)


# ---------- recommendSeats ----------

@tool("recommendSeats")
async def recommend_seats(
    show_id: str,
    count: int = 2,
    prefer_row: str = "middle",
    prefer_side: str = "center",
    together: bool = True,
) -> str:
    """智能推荐座位。show_id=场次ID，count=票数(1-4)，prefer_row=front|middle|back，prefer_side=center|aisle|edge，together=是否连座。"""
    body = {
        "showId": show_id,
        "count": count,
        "preferRow": prefer_row,
        "preferSide": prefer_side,
        "together": together,
    }
    data = await safe_api_call(
        post(backend_url("/reco/seats"), json=body, timeout=1.2),
        domain="座位",
    )
    if isinstance(data, str):
        return data

    if not isinstance(data, dict):
        return "推荐结果数据格式不正确，请稍后再试。"
    plans = data.get("plans") or []
    if not plans:
        compromise = data.get("compromise")
        if compromise:
            return f"当前条件暂无理想座位。建议：{compromise.get('suggestion', '可尝试其他场次')}"
        return "当前场次暂无可用座位推荐，请尝试其他场次或减少票数。"

    lines = [f"为您推荐 {len(plans)} 个选座方案："]
    for i, plan in enumerate(plans, 1):
        if not isinstance(plan, dict):
            continue
        seat_ids = plan.get("seatIds") or []
        seats = plan.get("seats") or []
        seat_names = [
            s.get("seatName", s.get("seatId", "?")) if isinstance(s, dict) else "?"
            for s in seats
        ] or seat_ids
        lines.append(
            f"- 方案{i}：{'、'.join(str(n) for n in seat_names)}"
            f"（评分 {plan.get('score', '?')}，{plan.get('explain', '')}）"
        )
    return "\n".join(lines)


# ---------- lockSeats ----------

@tool("lockSeats")
async def lock_seats(
    show_id: str,
    seat_ids: str,
    ttl_seconds: int = 900,
    session_id: str = "",
) -> str:
    """锁定座位。show_id=场次ID，seat_ids=座位ID逗号分隔(如 "sm1:6:7,sm1:6:8")，ttl_seconds=锁定时长(60-900)，session_id=可选会话ID。"""
    auth_err = require_auth("请先登录后再锁座。")
    if auth_err:
        return auth_err

    seat_list = [s.strip() for s in seat_ids.split(",") if s.strip()]
    if not seat_list:
        return "请提供至少一个座位 ID。"
    if len(seat_list) > 4:
        return "单次最多锁定 4 个座位。"

    body: dict[str, Any] = {
        "showId": show_id,
        "seatIds": seat_list,
        "ttlSeconds": ttl_seconds,
    }
    if session_id:
        body["sessionId"] = session_id

    # 幂等键：同一用户+场次+座位组合重复锁座不产生副作用
    idem_key = f"idem_lock_{show_id}_{'_'.join(seat_list)}"
    if session_id:
        idem_key = f"idem_lock_{session_id}_{show_id}_{'_'.join(seat_list)}"

    data = await safe_api_call(
        post(backend_url("/locks"), json=body, headers={"Idempotency-Key": idem_key}, timeout=1.5),
        domain="座位",
    )
    if isinstance(data, str):
        return data

    if not isinstance(data, dict):
        return "锁座结果数据格式不正确，请稍后再试。"
    seat_names = ", ".join(data.get("seatIds", []))
    return (
        f"锁座成功！\n"
        f"- 锁座凭证：{data.get('lockId', '-')}（下单时需要）\n"
        f"- 已锁座位：{seat_names}\n"
        f"- 有效期至：{data.get('expireAt', '-')}\n"
        f"- 请在 {data.get('ttlSeconds', 900)} 秒内完成支付"
    )


# ---------- unlockSeats ----------

@tool("unlockSeats")
async def unlock_seats(lock_id: str, session_id: str = "") -> str:
    """释放锁定的座位。lock_id=锁座凭证ID，session_id=可选会话ID(清空Draft锁字段)。"""
    auth_err = require_auth("请先登录后再操作。")
    if auth_err:
        return auth_err

    params: dict[str, Any] = {}
    if session_id:
        params["sessionId"] = session_id

    data = await safe_api_call(
        delete(backend_url(f"/locks/{lock_id}"), params=params, timeout=0.8),
        domain="座位",
    )
    if isinstance(data, str):
        return data

    if not isinstance(data, dict):
        return "解锁结果数据格式不正确，请稍后再试。"
    if data.get("released"):
        return f"座位已释放（锁座凭证 {data.get('lockId', lock_id)}）。"
    return "座位释放失败，请稍后再试。"


SEAT_TOOLS = [get_seat_map, recommend_seats, lock_seats, unlock_seats]

__all__ = [
    "SEAT_TOOLS",
    "SeatToolError",
    "get_seat_map",
    "recommend_seats",
    "lock_seats",
    "unlock_seats",
    "format_seat_map",
]
