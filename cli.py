"""本地终端对话入口：直接调用 Agent，不启动 FastAPI 服务。"""
from __future__ import annotations

import asyncio
import uuid
from collections.abc import Callable

from agent import stream_chat
from agent.langgraph.checkpoint import checkpoint_lifespan
from agent.langgraph.runner import reset_graph_cache

Input = Callable[[str], str]
Output = Callable[..., None]


def _parse_location(command: str) -> tuple[float, float] | None:
    """解析 ``/location <纬度> <经度>``，格式不正确时返回 None。"""
    pieces = command.split()
    if len(pieces) != 3:
        return None
    try:
        latitude, longitude = float(pieces[1]), float(pieces[2])
    except ValueError:
        return None
    if not -90 <= latitude <= 90 or not -180 <= longitude <= 180:
        return None
    return latitude, longitude


async def run_terminal_chat(
    *,
    input_fn: Input = input,
    output: Output = print,
) -> None:
    """运行交互循环，便于手工使用和自动化测试。"""
    session_id = str(uuid.uuid4())
    latitude: float | None = None
    longitude: float | None = None

    output("终端 Agent 已启动。输入 /help 查看命令，/quit 或 /exit 退出。")
    output(f"本次会话 ID：{session_id}")
    while True:
        try:
            message = input_fn("你：").strip()
        except (EOFError, KeyboardInterrupt):
            output("\n已退出终端 Agent。")
            return

        if not message:
            continue
        if message.lower() in {"/quit", "/exit"}:
            output("已退出终端 Agent。")
            return
        if message == "/help":
            output("命令：/location <纬度> <经度> 设置位置；/location 查看当前位置；/quit 或 /exit 退出。")
            continue
        if message.startswith("/location"):
            if message == "/location":
                if latitude is None or longitude is None:
                    output("当前位置尚未设置。示例：/location 31.2989 121.5140")
                else:
                    output(f"当前位置：纬度 {latitude}，经度 {longitude}")
                continue
            location = _parse_location(message)
            if location is None:
                output("位置格式错误。请输入：/location <纬度(-90~90)> <经度(-180~180)>")
                continue
            latitude, longitude = location
            output(f"位置已设置：纬度 {latitude}，经度 {longitude}。现在可查询附近影院。")
            continue

        output("助手：", end="", flush=True)
        received_token = False
        try:
            async for event in stream_chat(
                message,
                session_id=session_id,
                latitude=latitude,
                longitude=longitude,
            ):
                if event.get("type") == "token":
                    output(str(event.get("content", "")), end="", flush=True)
                    received_token = True
                elif event.get("type") == "error":
                    output(str(event.get("message", "发生未知错误")), end="", flush=True)
                    received_token = True
        except Exception as exc:  # noqa: BLE001 - 终端入口需将内部异常转成可读提示
            if received_token:
                output()
            output(f"请求失败：{exc}")
            continue
        output()


async def main() -> None:
    """初始化可选 Postgres Checkpointer，并确保图缓存随进程清理。"""
    async with checkpoint_lifespan():
        reset_graph_cache()
        try:
            await run_terminal_chat()
        finally:
            reset_graph_cache()


if __name__ == "__main__":
    asyncio.run(main())
