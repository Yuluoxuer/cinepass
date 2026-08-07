"""SeatAgent Tools — 调用票务中台座位图/推荐/锁座/解锁接口。"""
from __future__ import annotations

import json
from typing import Any

import httpx
from langchain.tools import tool

from agent.http import backend_url, delete, get, post
from agent.request_context import has_authorization


class SeatToolError(RuntimeError):
    """把中台或网络异常转换为可直接展示给用户的消息。"""


def _unwrap(payload: Any) -> Any:
    if not isinstance(payload, dict):
        raise SeatToolError("座位服务返回的数据格式不正确，请稍后再试。")
    code = payload.get("code")
    message = str(payload.get("message") or "")
    if code not in (0, 200, None):
        raise SeatToolError(f"座位操作失败：{message or f'业务错误 code={code}'}")
    return payload.get("data")


def _http_error(exc: httpx.HTTPStatusError, *, detail: bool) -> SeatToolError:
    status = exc.response.status_code
    if status == 404 and detail:
        return SeatToolError("未找到该场次的座位图，请确认场次 ID 是否正确。")
    if status == 401:
        return SeatToolError("请先登录后再进行座位操作。")
    if status == 409:
        return SeatToolError("座位已被他人锁定或选走，请刷新座位图后重试。")
    return SeatToolError(f"座位服务暂时不可用（HTTP {status}），请稍后再试。")


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
    try:
        data = _unwrap(
            await get(backend_url(f"/shows/{show_id}/seat-map"), timeout=0.8)
        )
    except httpx.TimeoutException as exc:
        return str(SeatToolError("查询座位图超时，请稍后再试。"))
    except httpx.HTTPStatusError as exc:
        return str(_http_error(exc, detail=True))
    except httpx.HTTPError as exc:
        return str(SeatToolError("座位服务连接失败，请稍后再试。"))
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
    try:
        data = _unwrap(
            await post(backend_url("/reco/seats"), json=body, timeout=1.2)
        )
    except httpx.TimeoutException:
        return str(SeatToolError("推荐座位超时，请稍后再试。"))
    except httpx.HTTPStatusError as exc:
        return str(_http_error(exc, detail=False))
    except httpx.HTTPError:
        return str(SeatToolError("座位服务连接失败，请稍后再试。"))

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
    if not has_authorization():
        return "请先登录后再锁座。"

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

    try:
        data = _unwrap(
            await post(
                backend_url("/locks"),
                json=body,
                headers={"Idempotency-Key": idem_key},
                timeout=1.5,
            )
        )
    except httpx.TimeoutException:
        return str(SeatToolError("锁座超时，请稍后再试。"))
    except httpx.HTTPStatusError as exc:
        return str(_http_error(exc, detail=False))
    except httpx.HTTPError:
        return str(SeatToolError("座位服务连接失败，请稍后再试。"))

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
    if not has_authorization():
        return "请先登录后再操作。"

    params: dict[str, Any] = {}
    if session_id:
        params["sessionId"] = session_id

    try:
        data = _unwrap(
            await delete(
                backend_url(f"/locks/{lock_id}"),
                params=params,
                timeout=0.8,
            )
        )
    except httpx.TimeoutException:
        return str(SeatToolError("解锁超时，请稍后再试。"))
    except httpx.HTTPStatusError as exc:
        return str(_http_error(exc, detail=False))
    except httpx.HTTPError:
        return str(SeatToolError("座位服务连接失败，请稍后再试。"))

    if not isinstance(data, dict):
        return "解锁结果数据格式不正确，请稍后再试。"
    if data.get("released"):
        return f"座位已释放（锁座凭证 {data.get('lockId', lock_id)}）。"
    return f"座位释放失败，请稍后再试。"


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
