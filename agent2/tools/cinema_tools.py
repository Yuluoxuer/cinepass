"""影院查询 Tools：仅调用票务中台的公开只读接口，返回原始数据。"""
from __future__ import annotations

from typing import Any

import httpx
from langchain.tools import tool

from agent2.http import backend_url, get
from agent2.request_context import get_location


class CinemaToolError(RuntimeError):
    """把中台或网络异常转换为可识别的错误消息。"""


def _unwrap(payload: Any) -> Any:
    if not isinstance(payload, dict):
        raise CinemaToolError("影院服务返回的数据格式不正确，请稍后再试。")

    code = payload.get("code")
    message = str(payload.get("message") or "")
    if code not in (0, 200, None):
        raise CinemaToolError(f"查询影院失败：{message or f'业务错误 code={code}'}")
    return payload.get("data")


def _http_error(exc: httpx.HTTPStatusError, *, detail: bool) -> CinemaToolError:
    status = exc.response.status_code
    if status == 404 and detail:
        return CinemaToolError("未找到该影院，请确认影院 ID 是否正确。")
    return CinemaToolError(f"影院服务暂时不可用（HTTP {status}），请稍后再试。")


async def fetch_cinemas(
    *,
    movie_id: str | None = None,
    lat: float,
    lng: float,
    radius_meters: int = 5000,
    sort: str = "distance",
    page: int = 1,
    size: int = 20,
) -> Any:
    """请求附近影院列表，返回中台 data（原始 JSON）。"""
    params: dict[str, Any] = {
        "lat": lat,
        "lng": lng,
        "radiusMeters": radius_meters,
        "sort": sort,
        "page": page,
        "size": size,
    }
    if movie_id:
        params["movieId"] = movie_id
    try:
        payload = await get(backend_url("/cinemas"), params=params, timeout=0.8)
        return _unwrap(payload)
    except httpx.TimeoutException as exc:
        raise CinemaToolError("查询附近影院超时，请稍后再试。") from exc
    except httpx.HTTPStatusError as exc:
        raise _http_error(exc, detail=False) from exc
    except httpx.HTTPError as exc:
        raise CinemaToolError("影院服务连接失败，请稍后再试。") from exc


async def fetch_cinema(cinema_id: str) -> Any:
    """请求指定影院详情，返回中台 data（原始 JSON）。"""
    try:
        payload = await get(backend_url(f"/cinemas/{cinema_id}"), timeout=0.5)
        return _unwrap(payload)
    except httpx.TimeoutException as exc:
        raise CinemaToolError("查询影院详情超时，请稍后再试。") from exc
    except httpx.HTTPStatusError as exc:
        raise _http_error(exc, detail=True) from exc
    except httpx.HTTPError as exc:
        raise CinemaToolError("影院服务连接失败，请稍后再试。") from exc


@tool("searchCinemas")
async def search_cinemas(
    lat: float | None = None,
    lng: float | None = None,
    movie_id: str | None = None,
    radius_meters: int = 5000,
    sort: str = "distance",
    page: int = 1,
    size: int = 20,
) -> str:
    """查询附近影院。lat/lng 可选：未传时自动使用用户当前位置，不要编造坐标。可按 movie_id、半径、距离或最低价排序和分页。返回原始 JSON 数据。"""
    if lat is None or lng is None:
        location = get_location()
        if location:
            lat, lng = location
    if lat is None or lng is None:
        return "未获取到用户位置，请开启定位或明确提供经纬度后再查询。"
    try:
        data = await fetch_cinemas(
            movie_id=movie_id,
            lat=lat,
            lng=lng,
            radius_meters=radius_meters,
            sort=sort,
            page=page,
            size=size,
        )
    except CinemaToolError as exc:
        return str(exc)
    return str(data)


@tool("getCinema")
async def get_cinema(cinema_id: str) -> str:
    """按 cinema_id 查询一家影院的公开详情。返回原始 JSON 数据。"""
    try:
        data = await fetch_cinema(cinema_id)
    except CinemaToolError as exc:
        return str(exc)
    return str(data)


CINEMA_TOOLS = [search_cinemas, get_cinema]

__all__ = [
    "CINEMA_TOOLS",
    "CinemaToolError",
    "fetch_cinema",
    "fetch_cinemas",
    "get_cinema",
    "search_cinemas",
]
