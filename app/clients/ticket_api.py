"""中台 HTTP 客户端 — Tool / Draft 经 ticket-api；对话消息走 Agent 自有库。

调用方：process_turn、tools/*。覆盖已有 ticket_api.py。
用户指令：去掉 X-Internal-Api-Key；Agent 自有库 SQLAlchemy。
"""
from __future__ import annotations

from typing import Any

import httpx

from app.config import get_settings


class TicketApiClient:
    """httpx 封装：透传用户 Authorization；不再附带 X-Internal-Api-Key。"""

    def __init__(
        self,
        authorization: str | None = None,
        *,
        base_url: str | None = None,
        timeout: float | None = None,
    ) -> None:
        settings = get_settings()
        self._base_url = (base_url or settings.ticket_api_base_url).rstrip("/")
        self._timeout = timeout or settings.ticket_api_timeout_s
        self._authorization = authorization

    def _headers(self, *, idempotency_key: str | None = None) -> dict[str, str]:
        headers: dict[str, str] = {"Accept": "application/json"}
        if self._authorization:
            headers["Authorization"] = self._authorization
        if idempotency_key:
            headers["Idempotency-Key"] = idempotency_key
        return headers

    async def get(self, path: str, *, params: dict[str, Any] | None = None) -> Any:
        async with httpx.AsyncClient(
            base_url=self._base_url, timeout=self._timeout
        ) as client:
            resp = await client.get(path, params=params, headers=self._headers())
            resp.raise_for_status()
            return resp.json()

    async def post(
        self,
        path: str,
        *,
        json: dict[str, Any] | None = None,
        idempotency_key: str | None = None,
    ) -> Any:
        async with httpx.AsyncClient(
            base_url=self._base_url, timeout=self._timeout
        ) as client:
            resp = await client.post(
                path,
                json=json,
                headers=self._headers(idempotency_key=idempotency_key),
            )
            resp.raise_for_status()
            return resp.json()

    async def put(self, path: str, *, json: dict[str, Any] | None = None) -> Any:
        async with httpx.AsyncClient(
            base_url=self._base_url, timeout=self._timeout
        ) as client:
            resp = await client.put(path, json=json, headers=self._headers())
            resp.raise_for_status()
            return resp.json()

    async def delete(self, path: str) -> Any:
        async with httpx.AsyncClient(
            base_url=self._base_url, timeout=self._timeout
        ) as client:
            resp = await client.delete(path, headers=self._headers())
            resp.raise_for_status()
            return resp.json() if resp.content else None

    async def get_draft(self, session_id: str) -> Any:
        return await self.get(f"/api/v1/booking-drafts/{session_id}")

    async def put_draft(self, session_id: str, body: dict[str, Any]) -> Any:
        return await self.put(f"/api/v1/booking-drafts/{session_id}", json=body)
