"""Tools 执行上下文：持有 TicketApiClient 与幂等键生成。"""
from __future__ import annotations

import uuid
from dataclasses import dataclass

from app.clients.ticket_api import TicketApiClient


@dataclass
class ToolsContext:
    api: TicketApiClient

    def idem_key(self) -> str:
        return str(uuid.uuid4())
