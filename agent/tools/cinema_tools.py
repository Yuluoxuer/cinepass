"""影院查询 Tools：仅调用票务中台的公开只读接口。"""
from __future__ import annotations

from typing import Any

from langchain.tools import tool

from agent.http import backend_url, get
from agent.tools._common import ToolError, safe_api_call

# 向后兼容别名
CinemaToolError = ToolError


def _format_distance(value: Any) -> str:
    if not isinstance(value, (int, float)):
        return "距离暂未提供"
    if value >= 1000:
        return f"距离 {value / 1000:.1f} 公里"
    return f"距离 {int(value)} 米"


def _format_price(value: Any) -> str:
    if isinstance(value, (int, float)):
        return f"¥{value:g} 起"
    return "票价暂未提供"


def format_cinema_list(data: Any) -> str:
    """把列表成功数据转换为稳定的当前纯文本回复协议。"""
    items = data.get("items") if isinstance(data, dict) else None
    if not isinstance(items, list) or not items:
        return "附近暂未找到影院，可以扩大搜索范围后再试。"
    lines = [f"为您找到 {len(items)} 家附近影院："]
    for item in items:
        if not isinstance(item, dict):
            continue
        cinema_id = item.get("cinemaId") or "-"
        name = item.get("name") or "未命名影院"
        address = item.get("address") or "地址暂未提供"
        lines.append(
            f"- [{cinema_id}] {name}｜{address}｜"
            f"{_format_distance(item.get('distanceMeters'))}｜{_format_price(item.get('minPrice'))}"
        )
    return "\n".join(lines)


def format_cinema_detail(data: Any) -> str:
    """把详情成功数据转换为稳定的当前纯文本回复协议。"""
    if not isinstance(data, dict):
        return "影院详情数据格式不正确，请稍后再试。"
    lines = [f"影院详情：{data.get('name') or '未命名影院'}（ID：{data.get('cinemaId') or '-'}）"]
    lines.append(f"- 地址：{data.get('address') or '暂未提供'}")
    lines.append(f"- 最低票价：{_format_price(data.get('minPrice'))}")
    tags = data.get("tags") or data.get("labels")
    if isinstance(tags, list) and tags:
        lines.append(f"- 标签：{'、'.join(str(tag) for tag in tags)}")
    halls = data.get("halls")
    if isinstance(halls, list) and halls:
        names = [str(hall.get("name") or hall.get("hallId") or "影厅") for hall in halls if isinstance(hall, dict)]
        if names:
            lines.append(f"- 影厅：{'、'.join(names)}")
    traffic = data.get("trafficNote") or data.get("traffic")
    if traffic:
        lines.append(f"- 交通：{traffic}")
    return "\n".join(lines)


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
    """请求附近影院列表，供离线模式和 LangChain Tool 共用。"""
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
    result = await safe_api_call(
        get(backend_url("/cinemas"), params=params, timeout=0.8),
        domain="影院",
    )
    if isinstance(result, str):
        raise CinemaToolError(result)
    return result


async def fetch_cinema(cinema_id: str) -> Any:
    """请求指定影院详情，供离线模式和 LangChain Tool 共用。"""
    result = await safe_api_call(
        get(backend_url(f"/cinemas/{cinema_id}"), timeout=0.5),
        domain="影院",
        detail=True,
    )
    if isinstance(result, str):
        raise CinemaToolError(result)
    return result


@tool("searchCinemas")
async def search_cinemas(
    lat: float,
    lng: float,
    movie_id: str | None = None,
    radius_meters: int = 5000,
    sort: str = "distance",
    page: int = 1,
    size: int = 20,
) -> str:
    """查询附近影院。lat/lng 必填；可按 movie_id、半径、距离或最低价排序和分页。"""
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
    return format_cinema_list(data)


@tool("getCinema")
async def get_cinema(cinema_id: str) -> str:
    """按 cinema_id 查询一家影院的公开详情。"""
    try:
        data = await fetch_cinema(cinema_id)
    except CinemaToolError as exc:
        return str(exc)
    return format_cinema_detail(data)


CINEMA_TOOLS = [search_cinemas, get_cinema]

__all__ = [
    "CINEMA_TOOLS",
    "CinemaToolError",
    "fetch_cinema",
    "fetch_cinemas",
    "format_cinema_detail",
    "format_cinema_list",
    "get_cinema",
    "search_cinemas",
]
