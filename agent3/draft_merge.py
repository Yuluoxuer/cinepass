"""草稿乐观锁合并（clientDraftVersion）——三套 Agent 端点共用。

对齐 ``docs/agent-draft-optimistic-lock.md`` §4 设计：
- ``cv == null`` → 兼容旧客户端：退化为「固定优先级合并」（client 决策字段优先 + Agent 成果保护）
- ``cv == sv``   → 无冲突：正常合并（决策字段 client 优先，锁座/下单成果保留）
- ``cv <  sv``   → 服务端较新：以 server_draft 为准，忽略 client_draft
- ``cv >  sv``   → 客户端较新：决策字段以 client_draft 为准，与 server_draft 合并；
                   但 lockId/orderId/expireAt 以 server_draft 为准；version = max(cv, sv) + 1

合并后按依赖关系级联清空（§4.3）：改影片→清影院/场次/座位/锁/单，改 count→清座位；
并保护锁座/下单成果（§4.4）：服务端非空 lockId/orderId/expireAt 不被页面旧快照冲掉。
"""
from __future__ import annotations

from typing import Any

DRAFT_KEYS = (
    "movieId", "filmTitle", "cinemaId", "cinemaName", "showId",
    "date", "timeWindow", "count", "seatIds",
    "preferRow", "preferSide", "together", "lockId", "orderId", "expireAt",
)

# 决策字段变更后的级联清空（§4.3）
_CASCADE: dict[str, tuple[str, ...]] = {
    "movieId": ("cinemaId", "cinemaName", "showId", "seatIds", "lockId", "orderId", "expireAt"),
    "cinemaId": ("showId", "seatIds", "lockId", "orderId", "expireAt"),
    "showId": ("seatIds", "lockId", "orderId", "expireAt"),
    "count": ("seatIds",),
    "seatIds": ("lockId", "orderId", "expireAt"),
}

_PROGRESS_KEYS = ("lockId", "orderId", "expireAt")


def _pick(src: dict[str, Any] | None) -> dict[str, Any]:
    """只取草稿契约字段且跳过空值。"""
    return {k: v for k, v in (src or {}).items() if k in DRAFT_KEYS and v not in (None, "", [])}


def _empty(v: Any) -> bool:
    return v is None or v == "" or v == []


def _changed(field: str, server: dict[str, Any], client: dict[str, Any]) -> bool:
    """client 明确携带某决策字段且与服务端不同（视为本端变更）。"""
    if field not in client:
        return False
    cv = client.get(field)
    if _empty(cv):
        return False
    return cv != server.get(field)


def _protect_progress(merged: dict[str, Any], server: dict[str, Any], client: dict[str, Any]) -> None:
    """锁座/下单成果保护（§4.4）：server 非空优先，其次 client 非空。"""
    for k in _PROGRESS_KEYS:
        sv = server.get(k)
        if not _empty(sv):
            merged[k] = sv
        else:
            cv = client.get(k)
            if not _empty(cv):
                merged[k] = cv


def _cascade_clear(merged: dict[str, Any], client: dict[str, Any], server: dict[str, Any]) -> None:
    """依赖字段级联清空：决策字段变更时，client 未重新提供的依赖字段清空。

    ``seatIds`` 契约上是数组，清空时置 ``[]``（而非删除键），其余字段删除。
    """
    for field, dependents in _CASCADE.items():
        if not _changed(field, server, client):
            continue
        for dep in dependents:
            # client 已提供该依赖字段的全新值则保留，否则清空（旧依赖失效）
            if dep not in client or _empty(client.get(dep)):
                if dep == "seatIds":
                    merged["seatIds"] = []
                else:
                    merged.pop(dep, None)


def resolve_draft_merge(
    server_draft: dict[str, Any] | None,
    client_draft: dict[str, Any] | None,
    client_version: int | None,
) -> tuple[dict[str, Any], int]:
    """按乐观锁语义合并草稿，返回 ``(合并后的 draft, 新 version)``。

    - ``server_draft``：服务端草稿（含 ``version`` 字段）
    - ``client_draft``：前端手动页面/中台草稿快照
    - ``client_version``：请求携带的 ``clientDraftVersion``（无则 None，兼容旧客户端）
    """
    server = dict(server_draft or {})
    client = _pick(client_draft)
    sv = int(server.get("version") or 0)

    if client_version is None:
        # 分支 1：旧客户端 → 固定优先级合并
        merged = {**server, **client}
        _protect_progress(merged, server, client)
        _cascade_clear(merged, client, server)
        merged["version"] = sv
        return merged, sv

    cv = int(client_version)
    if cv < sv:
        # 分支 3：服务端较新（Agent/其他端改过）→ 以 server 为准，前端覆盖本地
        server["version"] = sv
        return server, sv

    if cv > sv:
        # 分支 4：客户端较新（页面本地改动未同步）→ 决策字段 client 优先 + 成果保护
        new_v = max(cv, sv) + 1
        merged = {**server, **client}
        _protect_progress(merged, server, client)
        _cascade_clear(merged, client, server)
        merged["version"] = new_v
        return merged, new_v

    # 分支 2：无冲突 → 正常合并（决策字段页面优先，锁座/下单成果保留）
    merged = {**server, **client}
    _protect_progress(merged, server, client)
    _cascade_clear(merged, client, server)
    merged["version"] = sv
    return merged, sv
