"""影院查询子 Agent。"""
from __future__ import annotations

import re
from typing import AsyncIterator

from langchain.agents import create_agent

from agent.llm import get_chat_model
from agent.subagent.base import SubAgent
from agent.subagent.lc_runtime import astream_agent_text, history_to_messages
from agent.tools import CINEMA_TOOLS
from agent.tools.cinema_tools import (
    CinemaToolError,
    fetch_cinema,
    fetch_cinemas,
    format_cinema_detail,
    format_cinema_list,
)

_CINEMA_ID_RE = re.compile(
    r"(?:cinema(?:id)?|影院\s*(?:ID|id|编号)?)[：:=\s]*([A-Za-z0-9][A-Za-z0-9_-]*)",
    re.I,
)
_MOVIE_ID_RE = re.compile(r"(?:movie(?:Id)?|影片\s*(?:ID|id|编号)?)[：:=\s]*([A-Za-z0-9_-]+)")
_INTEGER_PARAM_RE = {
    "radius_meters": re.compile(r"radiusMeters[：:=\s]*(\d+)", re.I),
    "page": re.compile(r"page[：:=\s]*(\d+)", re.I),
    "size": re.compile(r"size[：:=\s]*(\d+)", re.I),
}


def _extract_cinema_id(message: str) -> str | None:
    match = _CINEMA_ID_RE.search(message)
    return match.group(1) if match else None


def _extract_movie_id(message: str) -> str | None:
    match = _MOVIE_ID_RE.search(message)
    return match.group(1) if match else None


def _extract_int(message: str, name: str, default: int) -> int:
    match = _INTEGER_PARAM_RE[name].search(message)
    return int(match.group(1)) if match else default


def _system_prompt(latitude: float | None, longitude: float | None) -> str:
    location = (
        f"已知用户坐标：lat={latitude}, lng={longitude}。"
        if latitude is not None and longitude is not None
        else "当前没有用户坐标。"
    )
    return (
        "你是影院查询助手，只能使用 searchCinemas 和 getCinema 两个只读工具。"
        f"{location}"
        "查询附近影院时必须调用 searchCinemas，并将已知坐标原样传入；缺坐标时请明确提醒用户提供 latitude 和 longitude。"
        "用户给出影院 ID 且要求详情时必须调用 getCinema。工具的成功输出已是用户可读的影院文本，"
        "必须原样输出；不得删改名称、地址、距离、票价、标签、影厅或交通信息，也不要编造信息。"
    )


class CinemaSubAgent(SubAgent):
    name = "cinema"

    async def run(
        self,
        message: str,
        *,
        history: list[dict[str, str]] | None = None,
        latitude: float | None = None,
        longitude: float | None = None,
    ) -> str:
        chunks: list[str] = []
        async for piece in self.astream(
            message, history=history, latitude=latitude, longitude=longitude
        ):
            chunks.append(piece)
        return "".join(chunks)

    async def astream(
        self,
        message: str,
        *,
        history: list[dict[str, str]] | None = None,
        latitude: float | None = None,
        longitude: float | None = None,
    ) -> AsyncIterator[str]:
        # 附近影院必须先有完整坐标；不能让不可用的 LLM 把这个确定性校验变成外部错误。
        if _extract_cinema_id(message) is None and (latitude is None or longitude is None):
            yield "查询附近影院需要同时提供 latitude 和 longitude，请开启定位后重试。"
            return

        model = get_chat_model()
        if model is None:
            yield await self._offline_reply(message, latitude=latitude, longitude=longitude)
            return

        try:
            agent = create_agent(
                model,
                tools=CINEMA_TOOLS,
                system_prompt=_system_prompt(latitude, longitude),
                name="cinema",
            )
            messages = history_to_messages(message, history)
            async for text in astream_agent_text(agent, messages):
                yield text
        except Exception:  # noqa: BLE001 - 模型异常应降级为可用的规则查询
            yield await self._offline_reply(message, latitude=latitude, longitude=longitude)

    async def _offline_reply(
        self, message: str, *, latitude: float | None, longitude: float | None
    ) -> str:
        cinema_id = _extract_cinema_id(message)
        try:
            if cinema_id:
                return format_cinema_detail(await fetch_cinema(cinema_id))
            if latitude is None or longitude is None:
                return "查询附近影院需要同时提供 latitude 和 longitude，请开启定位后重试。"
            return format_cinema_list(
                await fetch_cinemas(
                    movie_id=_extract_movie_id(message),
                    lat=latitude,
                    lng=longitude,
                    radius_meters=_extract_int(message, "radius_meters", 5000),
                    sort="price" if "最便宜" in message or "价格" in message else "distance",
                    page=_extract_int(message, "page", 1),
                    size=_extract_int(message, "size", 20),
                )
            )
        except CinemaToolError as exc:
            return str(exc)
