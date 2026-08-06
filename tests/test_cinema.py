"""CinemaAgent 的接口、路由和中台适配测试。"""
from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from unittest.mock import AsyncMock

import httpx
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from agent.langgraph.graph import _route_message
from agent.http import _merge_auth_headers
from agent.request_context import use_authorization
from agent.subagent.cinema_agent import CinemaSubAgent
from agent.tools import cinema_tools
from fapi.main import app
from fapi.models.chat import ChatRequest


def test_chat_request_accepts_complete_coordinates() -> None:
    request = ChatRequest(message="附近影院", latitude=31.2989, longitude=121.5140)
    assert request.latitude == 31.2989
    assert request.longitude == 121.5140


@pytest.mark.parametrize(
    "payload",
    [
        {"message": "附近影院", "latitude": 31.2},
        {"message": "附近影院", "longitude": 121.5},
        {"message": "附近影院", "latitude": 91, "longitude": 121.5},
        {"message": "附近影院", "latitude": 31.2, "longitude": 181},
    ],
)
def test_chat_request_rejects_incomplete_or_invalid_coordinates(payload: dict[str, object]) -> None:
    with pytest.raises(ValidationError):
        ChatRequest(**payload)


def test_cinema_route_has_priority_over_helper() -> None:
    assert _route_message("附近影院现在几点") == "cinema"
    assert _route_message("现在几点") == "helper"
    assert _route_message("你好") == "chat"
    assert _route_message("CinemaId=cinema_test") == "cinema"


def test_fetch_cinemas_maps_query_and_timeout(monkeypatch: pytest.MonkeyPatch) -> None:
    mocked_get = AsyncMock(
        return_value={"code": 200, "message": "success", "data": {"items": []}}
    )
    monkeypatch.setattr(cinema_tools, "get", mocked_get)

    data = asyncio.run(
        cinema_tools.fetch_cinemas(
            movie_id="m100",
            lat=31.2989,
            lng=121.5140,
            radius_meters=3000,
            sort="price",
            page=2,
            size=5,
        )
    )

    assert data == {"items": []}
    _, kwargs = mocked_get.await_args
    assert kwargs["params"] == {
        "lat": 31.2989,
        "lng": 121.5140,
        "radiusMeters": 3000,
        "sort": "price",
        "page": 2,
        "size": 5,
        "movieId": "m100",
    }
    assert kwargs["timeout"] == 0.8


def test_fetch_cinema_maps_path_and_timeout(monkeypatch: pytest.MonkeyPatch) -> None:
    mocked_get = AsyncMock(
        return_value={"code": 200, "message": "success", "data": {"cinemaId": "c12"}}
    )
    monkeypatch.setattr(cinema_tools, "get", mocked_get)

    assert asyncio.run(cinema_tools.fetch_cinema("c12")) == {"cinemaId": "c12"}
    args, kwargs = mocked_get.await_args
    assert args[0].endswith("/api/v1/cinemas/c12")
    assert kwargs["timeout"] == 0.5


def test_offline_nearby_cinema_formats_list(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_fetch(**kwargs: object) -> dict[str, object]:
        assert kwargs["lat"] == 31.2989
        assert kwargs["lng"] == 121.5140
        return {
            "items": [
                {
                    "cinemaId": "c12",
                    "name": "万达影城",
                    "address": "淞沪路 77 号",
                    "distanceMeters": 1200,
                    "minPrice": 45,
                }
            ]
        }

    monkeypatch.setattr("agent.subagent.cinema_agent.fetch_cinemas", fake_fetch)
    reply = asyncio.run(
        CinemaSubAgent()._offline_reply("附近有什么影院", latitude=31.2989, longitude=121.5140)
    )
    assert "[c12] 万达影城" in reply
    assert "1.2 公里" in reply
    assert "¥45 起" in reply


def test_offline_nearby_cinema_requires_location(monkeypatch: pytest.MonkeyPatch) -> None:
    mocked_fetch = AsyncMock()
    monkeypatch.setattr("agent.subagent.cinema_agent.fetch_cinemas", mocked_fetch)
    reply = asyncio.run(CinemaSubAgent()._offline_reply("附近影院", latitude=None, longitude=None))
    assert "latitude 和 longitude" in reply
    mocked_fetch.assert_not_awaited()


def test_configured_model_still_requires_location_before_calling_llm(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("agent.subagent.cinema_agent.get_chat_model", lambda: object())
    monkeypatch.setattr(
        "agent.subagent.cinema_agent.create_agent",
        lambda *args, **kwargs: pytest.fail("缺坐标时不应创建 LLM Agent"),
    )

    async def collect() -> list[str]:
        return [
            part
            async for part in CinemaSubAgent().astream(
                "附近影院", latitude=None, longitude=None
            )
        ]

    assert "latitude 和 longitude" in "".join(asyncio.run(collect()))


def test_model_error_falls_back_to_rule_based_cinema_query(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("agent.subagent.cinema_agent.get_chat_model", lambda: object())
    monkeypatch.setattr("agent.subagent.cinema_agent.create_agent", lambda *args, **kwargs: object())

    async def failing_stream(*args: object, **kwargs: object):
        if False:
            yield ""
        raise RuntimeError("LLM unavailable")

    async def fake_fetch(**kwargs: object) -> dict[str, object]:
        return {
            "items": [
                {
                    "cinemaId": "c12",
                    "name": "万达影城",
                    "address": "淞沪路 77 号",
                    "distanceMeters": 1200,
                    "minPrice": 45,
                }
            ]
        }

    monkeypatch.setattr("agent.subagent.cinema_agent.astream_agent_text", failing_stream)
    monkeypatch.setattr("agent.subagent.cinema_agent.fetch_cinemas", fake_fetch)

    async def collect() -> list[str]:
        return [
            part
            async for part in CinemaSubAgent().astream(
                "附近影院", latitude=31.2, longitude=121.5
            )
        ]

    assert "万达影城" in "".join(asyncio.run(collect()))


def test_offline_cinema_detail_formats_optional_fields(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_fetch(cinema_id: str) -> dict[str, object]:
        assert cinema_id == "c12"
        return {
            "cinemaId": "c12",
            "name": "万达影城",
            "address": "淞沪路 77 号",
            "minPrice": 45,
            "tags": ["杜比", "停车"],
            "halls": [{"hallId": "h1", "name": "1 号厅"}],
            "trafficNote": "地铁 10 号线",
        }

    monkeypatch.setattr("agent.subagent.cinema_agent.fetch_cinema", fake_fetch)
    reply = asyncio.run(
        CinemaSubAgent()._offline_reply("查看影院详情 cinemaId=c12", latitude=None, longitude=None)
    )
    assert "杜比、停车" in reply
    assert "1 号厅" in reply
    assert "地铁 10 号线" in reply


def test_cinema_tool_turns_timeout_into_readable_message(monkeypatch: pytest.MonkeyPatch) -> None:
    request = httpx.Request("GET", "http://backend/api/v1/cinemas")
    monkeypatch.setattr(
        cinema_tools,
        "get",
        AsyncMock(side_effect=httpx.ReadTimeout("timeout", request=request)),
    )
    reply = asyncio.run(cinema_tools.search_cinemas.ainvoke({"lat": 31.2, "lng": 121.5}))
    assert "超时" in reply


def test_fetch_cinema_turns_not_found_into_readable_message(monkeypatch: pytest.MonkeyPatch) -> None:
    request = httpx.Request("GET", "http://backend/api/v1/cinemas/missing")
    response = httpx.Response(404, request=request)
    monkeypatch.setattr(
        cinema_tools,
        "get",
        AsyncMock(side_effect=httpx.HTTPStatusError("not found", request=request, response=response)),
    )
    with pytest.raises(cinema_tools.CinemaToolError, match="未找到该影院"):
        asyncio.run(cinema_tools.fetch_cinema("missing"))


def test_fetch_cinemas_turns_business_error_into_readable_message(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        cinema_tools,
        "get",
        AsyncMock(return_value={"code": 400, "message": "无效坐标", "data": None}),
    )
    with pytest.raises(cinema_tools.CinemaToolError, match="无效坐标"):
        asyncio.run(cinema_tools.fetch_cinemas(lat=31.2, lng=121.5))


def test_existing_http_client_adds_context_jwt() -> None:
    with use_authorization("plain-token"):
        assert _merge_auth_headers({}) == {"Authorization": "Bearer plain-token"}


def test_chat_api_forwards_coordinates(monkeypatch: pytest.MonkeyPatch) -> None:
    captured: dict[str, object] = {}

    async def fake_run_chat(message: str, **kwargs: object) -> dict[str, object]:
        captured.update(kwargs)
        return {"route": "cinema", "reply": "影院结果", "events": ["cinema_done"], "session_id": "s1"}

    monkeypatch.setattr("fapi.api.chat.run_chat", fake_run_chat)
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/chat",
            json={"message": "附近影院", "latitude": 31.2, "longitude": 121.5},
        )
    assert response.status_code == 200
    assert response.json()["route"] == "cinema"
    assert captured["latitude"] == 31.2
    assert captured["longitude"] == 121.5


def test_stream_api_emits_cinema_route_and_done(monkeypatch: pytest.MonkeyPatch) -> None:
    async def fake_stream_chat(*args: object, **kwargs: object) -> AsyncIterator[dict[str, object]]:
        yield {"type": "route", "route": "cinema", "session_id": "s1"}
        yield {"type": "token", "content": "影院结果"}
        yield {"type": "done", "route": "cinema", "reply": "影院结果", "session_id": "s1"}

    monkeypatch.setattr("fapi.api.chat.stream_chat", fake_stream_chat)
    with TestClient(app) as client:
        response = client.post(
            "/api/v1/chat/stream",
            json={"message": "附近影院", "latitude": 31.2, "longitude": 121.5},
        )
    assert response.status_code == 200
    assert "event: route" in response.text
    assert '"route": "cinema"' in response.text
    assert "event: done" in response.text
