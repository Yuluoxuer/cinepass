"""终端 Agent 入口测试。"""
from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator

import cli


def test_parse_location() -> None:
    assert cli._parse_location("/location 31.2989 121.5140") == (31.2989, 121.514)
    assert cli._parse_location("/location 31.2") is None
    assert cli._parse_location("/location 91 121") is None
    assert cli._parse_location("/location east west") is None


def test_terminal_chat_streams_tokens_and_forwards_location(monkeypatch) -> None:
    prompts = iter(["/location 31.2 121.5", "查附近影院", "/exit"])
    printed: list[tuple[object, str]] = []
    captured: dict[str, object] = {}

    def fake_input(_: str) -> str:
        return next(prompts)

    def fake_output(*values: object, end: str = "\n", **_: object) -> None:
        printed.append(("".join(map(str, values)), end))

    async def fake_stream_chat(*args: object, **kwargs: object) -> AsyncIterator[dict[str, object]]:
        captured["message"] = args[0]
        captured.update(kwargs)
        yield {"type": "route", "route": "cinema"}
        yield {"type": "token", "content": "找到 1 家影院"}
        yield {"type": "done", "route": "cinema"}

    monkeypatch.setattr(cli, "stream_chat", fake_stream_chat)
    asyncio.run(cli.run_terminal_chat(input_fn=fake_input, output=fake_output))

    assert captured["message"] == "查附近影院"
    assert captured["latitude"] == 31.2
    assert captured["longitude"] == 121.5
    assert any("找到 1 家影院" in text for text, _ in printed)


def test_terminal_location_query_and_invalid_input() -> None:
    prompts = iter(["/location", "/location 91 121", "/quit"])
    printed: list[str] = []

    def fake_output(*values: object, **_: object) -> None:
        printed.append("".join(map(str, values)))

    asyncio.run(cli.run_terminal_chat(input_fn=lambda _: next(prompts), output=fake_output))

    assert any("尚未设置" in text for text in printed)
    assert any("位置格式错误" in text for text in printed)
