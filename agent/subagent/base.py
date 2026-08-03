"""SubAgent 基类约定。"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any, AsyncIterator


class SubAgent(ABC):
    """子 Agent：接收用户消息，产出回复文本（可流式）。"""

    name: str = "SubAgent"

    @abstractmethod
    async def run(self, message: str, *, history: list[dict[str, str]] | None = None) -> str:
        """跑完一轮，返回完整回复。"""

    async def astream(
        self, message: str, *, history: list[dict[str, str]] | None = None
    ) -> AsyncIterator[str]:
        """默认：整段回复后一次性 yield；有 LLM 的子类可覆盖为 token 流。"""
        reply = await self.run(message, history=history)
        if reply:
            yield reply

    def describe(self) -> dict[str, Any]:
        return {"name": self.name}
