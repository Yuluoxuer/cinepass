"""前端契约端点 /api/v1/agent/turns：内部走 agent2（create_react_agent），由工具轨迹生成动态卡片。

系分 §8.1 定义的前端契约端点；本模块把 agent2 的回复 + 工具调用结果映射为前端
``AgentTurnResponse`` 形状（replyText/sessionId/draft/cards），让前端零改动接入
agent2 的多轮购票流程。
"""
from __future__ import annotations

import ast
import base64
import datetime
import json
import uuid
from typing import Any

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field

from agent2.agent import get_agent
from agent2.request_context import use_authorization
from agent2.tools.booking_draft import load_draft, save_draft
from fapi.api._draft_merge import resolve_draft_merge
from fapi.deps import get_authorization

router = APIRouter(prefix="/agent", tags=["agent"])

# ---------- 前端契约模型（对齐 src/types/index.ts） ----------


class CardActionBody(BaseModel):
    cardId: str
    actionId: str
    itemId: str | None = None
    draftPatch: dict[str, Any] | None = None


class AgentTurnRequest(BaseModel):
    """前端 AgentTurnRequest。message 与 cardAction 至少二选一。"""

    sessionId: str | None = None
    message: str | None = None
    cardAction: CardActionBody | None = None
    clientDraftVersion: int | None = None
    debug: bool = False
    latitude: float | None = None
    longitude: float | None = None
    # 前端手动页面/中台草稿快照，用于同步到 agent2 草稿表（避免 Agent 不知道手动选片）
    clientDraft: dict[str, Any] | None = None


class AgentTurnDraftVO(BaseModel):
    """前端 BookingDraft 必填字段（agent2 booking_draft 子集 + 必填补全）。"""

    sessionId: str
    source: str = "agent"
    state: str = "Idle"
    movieId: str | None = None
    filmTitle: str | None = None
    cinemaId: str | None = None
    cinemaName: str | None = None
    showId: str | None = None
    date: str | None = None
    timeWindow: str | None = None
    count: int = 2
    seatIds: list[str] = Field(default_factory=list)
    preferRow: str | None = None
    preferSide: str | None = None
    together: bool | None = None
    lockId: str | None = None
    orderId: str | None = None
    expireAt: str | None = None
    version: int = 1


class AgentTurnResponse(BaseModel):
    sessionId: str
    replyText: str
    draft: AgentTurnDraftVO
    cards: list[dict[str, Any]] = Field(default_factory=list)
    progress: dict[str, Any] = Field(default_factory=dict)
    needLogin: bool = False
    events: list[str] = Field(default_factory=list)
    toolTraces: list[dict[str, Any]] = Field(default_factory=list)


class AgentTurnEnvelope(BaseModel):
    """前端 client.ts 期望的 {code, message, data} 信封格式。"""

    code: int = 200
    message: str = "ok"
    data: AgentTurnResponse


# ---------- 辅助 ----------


def _extract_user_id(authorization: str | None) -> str:
    """从 JWT payload 解析 userId（仅 decode，不验证签名）。"""
    if not authorization:
        return "anon"
    token = authorization.removeprefix("Bearer ").strip()
    parts = token.split(".")
    if len(parts) < 2:
        return "anon"
    try:
        payload = parts[1]
        payload += "=" * (4 - len(payload) % 4)
        data = json.loads(base64.urlsafe_b64decode(payload))
        return str(data.get("userId") or data.get("user_id") or data.get("sub") or "anon")
    except Exception:
        return "anon"


def _generate_session_id(user_id: str) -> str:
    """生成 session_id：sess_{userId前8位}_{时间戳}_{uuid前8位}。"""
    prefix = (user_id or "anon")[:8]
    ts = datetime.datetime.now().strftime("%Y%m%d%H%M%S")
    suffix = uuid.uuid4().hex[:8]
    return f"sess_{prefix}_{ts}_{suffix}"


_session_table_ready = False


async def _ensure_session_table() -> None:
    """确保 agent_sessions 表存在（进程内只建一次）。"""
    global _session_table_ready
    if _session_table_ready:
        return
    try:
        from agent2.tools.booking_draft import _get_pool
        pool = await _get_pool()
    except Exception:
        return
    async with pool.acquire() as conn:
        await conn.execute(
            """
            CREATE TABLE IF NOT EXISTS agent_sessions (
                session_id     VARCHAR(128) PRIMARY KEY,
                user_id        VARCHAR(64)  NOT NULL,
                title          VARCHAR(200),
                created_at     TIMESTAMPTZ  DEFAULT NOW(),
                last_message_at TIMESTAMPTZ  DEFAULT NOW()
            )
            """
        )
        await conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_agent_sessions_user ON agent_sessions(user_id, last_message_at DESC)"
        )
    _session_table_ready = True


async def _record_session(session_id: str, user_id: str, title: str | None = None) -> None:
    """插入或更新 session 记录。"""
    try:
        from agent2.tools.booking_draft import _get_pool
        pool = await _get_pool()
        await _ensure_session_table()
        async with pool.acquire() as conn:
            await conn.execute(
                """
                INSERT INTO agent_sessions (session_id, user_id, title, created_at, last_message_at)
                VALUES ($1, $2, $3, NOW(), NOW())
                ON CONFLICT (session_id)
                DO UPDATE SET last_message_at = NOW(),
                              title = COALESCE(EXCLUDED.title, agent_sessions.title)
                """,
                session_id,
                user_id,
                title,
            )
    except Exception:
        pass


async def _list_sessions(user_id: str, limit: int = 20) -> list[dict[str, Any]]:
    """获取用户的会话列表。"""
    try:
        from agent2.tools.booking_draft import _get_pool
        pool = await _get_pool()
        await _ensure_session_table()
        async with pool.acquire() as conn:
            rows = await conn.fetch(
                """
                SELECT session_id, user_id, title, created_at, last_message_at
                FROM agent_sessions
                WHERE user_id = $1
                ORDER BY last_message_at DESC
                LIMIT $2
                """,
                user_id,
                limit,
            )
        return [dict(r) for r in rows] if rows else []
    except Exception:
        return []


# ---------- 卡片生成（从 agent2 工具轨迹映射） ----------


def _parse_tool_result(content: str) -> Any:
    try:
        return ast.literal_eval(content)
    except Exception:
        return content


def _derive_state(draft: dict[str, Any]) -> str:
    """根据草稿完备度推导前端 BookingState。"""
    if draft.get("orderId"):
        return "PayMock"
    if draft.get("lockId"):
        return "ConfirmOrder"
    if draft.get("seatIds"):
        return "ConfirmOrder"
    if draft.get("showId"):
        return "SelectSeat"
    if draft.get("cinemaId"):
        return "SelectShow"
    if draft.get("movieId"):
        return "SelectCinema"
    return "SelectMovie"


def _to_draft_vo(sid: str, draft: dict[str, Any]) -> AgentTurnDraftVO:
    seat_ids = draft.get("seatIds") or []
    if isinstance(seat_ids, str):
        seat_ids = [s.strip() for s in seat_ids.split(",") if s.strip()]
    return AgentTurnDraftVO(
        sessionId=sid,
        source="agent",
        state=_derive_state(draft),
        movieId=draft.get("movieId"),
        filmTitle=draft.get("filmTitle"),
        cinemaId=draft.get("cinemaId"),
        cinemaName=draft.get("cinemaName"),
        showId=draft.get("showId"),
        date=draft.get("date"),
        timeWindow=draft.get("timeWindow"),
        count=int(draft.get("count") or 2),
        seatIds=list(seat_ids),
        preferRow=draft.get("preferRow"),
        preferSide=draft.get("preferSide"),
        together=draft.get("together"),
        lockId=draft.get("lockId"),
        orderId=draft.get("orderId"),
        expireAt=draft.get("expireAt"),
        version=int(draft.get("version") or 0),
    )


def _build_cards(tool_calls: list[dict[str, Any]], draft: dict[str, Any]) -> list[dict[str, Any]]:
    """把 agent2 本轮工具调用结果映射为前端动态卡片。

    按购票阶段控制卡片：
    - 电影卡片只在选片阶段（草稿尚无 movieId）出现——浏览选片（如"周末看喜剧"）也会出卡，
      一旦选定影片后续轮次不再重复弹电影卡；
    - 影院/场次/座位卡片只在对应前置字段已选定后出现，避免非购票轮次或错误阶段出卡。
    """
    cards: list[dict[str, Any]] = []
    for tc in tool_calls:
        name = tc.get("name", "")
        raw = _parse_tool_result(tc.get("content", ""))
        if not isinstance(raw, dict):
            continue
        # agent2 工具返回中台信封 {code, message, data}，先解包内层
        data = raw.get("data") if isinstance(raw.get("data"), dict) else raw
        if not isinstance(data, dict):
            continue

        if (
            name in ("search_movies", "searchMovies")
            and not draft.get("movieId")
            and not draft.get("lockId")
            and not draft.get("orderId")
        ):
            items = data.get("items") or []
            if isinstance(items, list) and items:
                cards.append({
                    "cardId": f"movie_{uuid.uuid4().hex[:8]}",
                    "type": "movie_list",
                    "title": "为您找到这些电影",
                    "payload": {"movies": items},
                    "actions": [
                        {
                            "actionId": "select",
                            "label": "选这部",
                            "itemId": m.get("movieId", ""),
                            "draftPatch": {"movieId": m.get("movieId"), "filmTitle": m.get("title")},
                        }
                        for m in items
                        if isinstance(m, dict) and m.get("movieId")
                    ],
                })
        elif name in ("searchCinemas", "search_cinemas") and draft.get("movieId") and not draft.get("cinemaId"):
            items = data.get("items") or []
            if isinstance(items, list) and items:
                cards.append({
                    "cardId": f"cinema_{uuid.uuid4().hex[:8]}",
                    "type": "cinema_list",
                    "title": "附近影院",
                    "payload": {"cinemas": items},
                    "actions": [
                        {
                            "actionId": "select",
                            "label": "选这家",
                            "itemId": c.get("cinemaId", ""),
                            "draftPatch": {"cinemaId": c.get("cinemaId"), "cinemaName": c.get("name")},
                        }
                        for c in items
                        if isinstance(c, dict) and c.get("cinemaId")
                    ],
                })
        elif name in ("list_shows", "listShows") and draft.get("cinemaId") and not draft.get("showId"):
            items = data.get("items") or []
            if isinstance(items, list) and items:
                cards.append({
                    "cardId": f"show_{uuid.uuid4().hex[:8]}",
                    "type": "show_list",
                    "title": "可选场次",
                    "payload": {"shows": items},
                    "actions": [
                        {
                            "actionId": "select",
                            "label": "选这场",
                            "itemId": s.get("showId", ""),
                            "draftPatch": {"showId": s.get("showId")},
                        }
                        for s in items
                        if isinstance(s, dict) and s.get("showId")
                    ],
                })
        elif name in ("getSeatMap", "get_seat_map", "recommendSeats", "recommend_seats") and draft.get("showId") and not draft.get("lockId"):
            is_reco = name in ("recommendSeats", "recommend_seats")
            show_id = data.get("showId") or draft.get("showId") or ""
            seat_map = data if not is_reco else None
            plans = data.get("plans") if is_reco and isinstance(data.get("plans"), list) else []
            compromise = data.get("compromise") if is_reco else None
            cards.append({
                "cardId": f"seat_{uuid.uuid4().hex[:8]}",
                "type": "seat_plans",
                "title": "选择座位",
                "payload": {
                    "showId": show_id,
                    "count": int(draft.get("count") or 2),
                    "seatMap": seat_map,
                    "plans": plans,
                    "compromise": compromise,
                },
                "actions": [
                    {
                        "actionId": "confirm",
                        "label": "确认选座",
                        "itemId": show_id,
                    }
                ],
            })
        elif name in ("create_order", "createOrder") and data.get("orderId"):
            cards.append({
                "cardId": f"pay_{uuid.uuid4().hex[:8]}",
                "type": "pay_mock",
                "title": "扫码支付",
                "payload": {
                    "orderId": data.get("orderId", ""),
                    "amount": data.get("amount") or data.get("totalAmount") or 0,
                    "payUrl": data.get("payUrl") or data.get("pay_url") or data.get("paymentUrl") or "",
                    "pollIntervalMs": 2000,
                },
                "actions": [
                    {"actionId": "payment_done", "label": "已完成支付", "itemId": data.get("orderId", "")},
                ],
            })
    # 去重：LLM 可能重复调用同一工具（如 search_movies），同类型同内容的卡片只保留一张
    seen: set[tuple[str, tuple[str, ...]]] = set()
    deduped: list[dict[str, Any]] = []
    for c in cards:
        payload = c.get("payload") or {}
        items = payload.get("movies") or payload.get("cinemas") or payload.get("shows") or []
        ids = tuple(sorted(
            str(it.get("movieId") or it.get("cinemaId") or it.get("showId") or "")
            for it in items
            if isinstance(it, dict)
        ))
        key = (c.get("type", ""), ids)
        if key in seen:
            continue
        seen.add(key)
        deduped.append(c)
    return deduped


def _compose_message(body: AgentTurnRequest) -> str:
    """把 message 或 cardAction 合成为发给 agent2 的自然语言。

    卡片操作合成为自然口语，不携带 movieId/cinemaId/showId 等内部技术字段——
    选择信息已预先写入 booking_draft，agent 读草稿即可继续流程，不必把技术话术带进对话历史。
    """
    if body.message and body.message.strip():
        return body.message.strip()
    if body.cardAction:
        ca = body.cardAction
        patch = ca.draftPatch or {}
        if patch.get("filmTitle"):
            return f"我选择了电影《{patch['filmTitle']}》，请继续帮我完成购票。"
        if patch.get("cinemaName"):
            return f"我选择了影院 {patch['cinemaName']}，请继续。"
        if patch.get("seatIds"):
            seats = ", ".join(str(s) for s in patch["seatIds"])
            return f"我选择了座位 {seats}，请帮我锁座并确认下单。"
        if patch.get("showId"):
            if patch.get("date"):
                return f"我选择了 {patch['date']} 的场次，请帮我查询座位并选座。"
            return "我选择了场次，请帮我查询座位并选座。"
        if patch:
            kv = ", ".join(f"{k}={v}" for k, v in patch.items())
            return f"（点卡操作：{kv}）"
        return f"（点卡操作 {ca.actionId}）"
    return ""


# ---------- 中台草稿同步辅助 ----------


async def _load_middle_draft(sid: str) -> dict[str, Any]:
    """从中台 /booking-drafts 读取草稿（手动购票页面的数据源）。"""
    from agent2.http import backend_url, get
    try:
        payload = await get(backend_url(f"/booking-drafts/{sid}"), timeout=1.0)
        if isinstance(payload, dict) and payload.get("code") in (0, 200, None):
            data = payload.get("data")
            if isinstance(data, dict):
                return data
    except Exception:
        pass
    return {}


async def _save_middle_draft(sid: str, draft: dict[str, Any]) -> None:
    """把草稿合并写回中台，供手动页面读取。"""
    from agent2.http import backend_url, post
    try:
        await post(backend_url(f"/booking-drafts/{sid}/merge"), json={"draft": draft}, timeout=1.0)
    except Exception:
        pass


# ---------- 端点 ----------


@router.post("/turns", response_model=AgentTurnEnvelope)
async def agent_turns(
    body: AgentTurnRequest,
    authorization: str | None = Depends(get_authorization),
) -> AgentTurnEnvelope:
    """前端契约端点：内部走 agent2，由工具轨迹生成动态卡片。"""
    user_id = _extract_user_id(authorization)
    sid = (body.sessionId or "").strip() or _generate_session_id(user_id)
    message = _compose_message(body)

    if not message:
        return AgentTurnEnvelope(
            data=AgentTurnResponse(
                sessionId=sid,
                replyText="请告诉我您想看什么电影，或者有什么需要帮您处理的？",
                draft=_to_draft_vo(sid, {}),
            )
        )

    # ---------- 草稿同步：中台(手动页面) ↔ 本地(agent2) ----------
    # 手动页面草稿存中台，agent2 草稿存本地表；每轮对话前把中台/页面快照/点卡合并进本地表，
    # 让 agent 的 getBookingDraft 读到最新；对话后再把 agent 结果写回中台。
    _DRAFT_KEYS = (
        "movieId", "filmTitle", "cinemaId", "cinemaName", "showId",
        "date", "timeWindow", "count", "seatIds",
        "preferRow", "preferSide", "together", "lockId", "orderId", "expireAt",
    )

    def _pick(src: dict[str, Any] | None) -> dict[str, Any]:
        return {k: v for k, v in (src or {}).items() if k in _DRAFT_KEYS and v not in (None, "", [])}

    with use_authorization(authorization):
        try:
            middle = await _load_middle_draft(sid)
            local_existing = await load_draft(sid) or {}
            middle_clean = {k: v for k, v in middle.items() if not str(k).startswith("_")}
            # 乐观锁：以中台草稿为 server_draft（version 以中台为准，页面基于它发 clientDraftVersion）
            sv = int(middle_clean.get("version") or local_existing.get("version") or 0)
            server_draft = {**local_existing, **middle_clean, "version": sv}
            merged, _new_v = resolve_draft_merge(server_draft, body.clientDraft, body.clientDraftVersion)
            # 点卡操作：明确选择优先（覆盖）
            patch = body.cardAction.draftPatch if body.cardAction else None
            if patch:
                merged = {**merged, **_pick(patch)}
            # agent 已锁座/下单成果以本地为准，不被页面/中台旧快照覆盖
            for k in ("lockId", "orderId", "expireAt"):
                if local_existing.get(k):
                    merged[k] = local_existing[k]
            await save_draft(merged, sid)
        except Exception:
            pass  # 草稿库/中台不可用则忽略

    agent = await get_agent()
    # 坐标经 ContextVar 注入（run_with_trace 内部 use_location），不再拼进消息文本，
    # 避免污染对话历史；searchCinemas 未显式传经纬度时会自动回退到该位置。
    trace = await agent.run_with_trace(
        message,
        session_id=sid,
        authorization=authorization,
        latitude=body.latitude,
        longitude=body.longitude,
    )
    reply = trace.get("reply", "")
    tool_calls = trace.get("tool_calls", [])

    try:
        draft = await load_draft(sid) or {}
    except Exception:
        draft = {}

    # ---------- 锁座成功：先把 lockId 写入 draft（必须在 _build_cards 之前） ----------
    # 用户在座位卡片点「确认选座」→ LLM 调 lockSeats 成功。
    # 先写 lockId 再构建卡片，这样 _build_cards 的 seat_plans/电影卡片分支
    # 会因为 draft.lockId 已存在而跳过，避免锁座后再次出现选座/电影卡片。
    lock_success: dict[str, Any] | None = None
    for tc in tool_calls:
        if tc.get("name") in ("lockSeats", "lock_seats"):
            raw = _parse_tool_result(tc.get("content", ""))
            if isinstance(raw, dict):
                data = raw.get("data") if isinstance(raw.get("data"), dict) else raw
                if isinstance(data, dict) and data.get("lockId"):
                    lock_success = data
                    break

    if lock_success and not draft.get("orderId"):
        draft["lockId"] = lock_success["lockId"]
        if lock_success.get("expireAt"):
            draft["expireAt"] = lock_success["expireAt"]
        # 锁座接口可能返回 seatIds，回填 draft 确保订单摘要能读到已选座位
        locked_seat_ids = lock_success.get("seatIds") or lock_success.get("seat_ids")
        if locked_seat_ids and not draft.get("seatIds"):
            if isinstance(locked_seat_ids, list):
                draft["seatIds"] = [str(s) for s in locked_seat_ids]
            else:
                draft["seatIds"] = str(locked_seat_ids)
        try:
            await save_draft(draft, sid)
        except Exception:
            pass  # 写库失败不阻塞主流程

    # 同步 agent2 本地草稿回中台（让手动页面看到 agent 的选择/锁座/下单）
    if sid and draft:
        try:
            with use_authorization(authorization):
                await _save_middle_draft(sid, draft)
        except Exception:
            pass  # 中台不可用不阻塞主流程

    cards = _build_cards(tool_calls, draft)

    # ---------- 锁座成功：自动创建订单生成付款码 ----------
    if lock_success and not draft.get("orderId"):
        # 自动创建订单（座位已锁，直接下单）
        try:
            from agent2.http import backend_url, get, post
            from agent2.request_context import use_authorization

            with use_authorization(authorization):
                order_resp = await post(
                    backend_url("/orders"),
                    json={"lockId": draft["lockId"], "sessionId": sid},
                    timeout=2.0,
                )
            if isinstance(order_resp, dict) and order_resp.get("code") in (0, 200, None):
                order_data = order_resp.get("data") or {}
                if order_data.get("orderId"):
                    draft["orderId"] = order_data["orderId"]
                    try:
                        await save_draft(draft, sid)
                    except Exception:
                        pass
                    # 拉取支付二维码
                    pay_qr: dict[str, Any] = {}
                    try:
                        with use_authorization(authorization):
                            pay_resp = await get(
                                backend_url(f"/orders/{order_data['orderId']}/pay-qrcode"),
                                timeout=2.0,
                            )
                        if isinstance(pay_resp, dict) and pay_resp.get("code") in (0, 200, None):
                            pay_qr = pay_resp.get("data") or {}
                    except Exception:
                        pass
                    # 生成付款卡片（若本轮尚未生成），并携带已选座位供前端回显
                    if not any(c.get("type") == "pay_mock" for c in cards):
                        seat_ids = draft.get("seatIds") or []
                        if isinstance(seat_ids, str):
                            seat_ids = [s.strip() for s in seat_ids.split(",") if s.strip()]
                        cards.append({
                            "cardId": f"pay_{uuid.uuid4().hex[:8]}",
                            "type": "pay_mock",
                            "title": "扫码支付",
                            "payload": {
                                "orderId": order_data["orderId"],
                                "amount": order_data.get("amount") or pay_qr.get("amount") or 0,
                                "payUrl": pay_qr.get("payUrl") or "",
                                "pollIntervalMs": pay_qr.get("pollIntervalMs") or 2000,
                                "movieTitle": draft.get("filmTitle") or "",
                                "cinemaName": draft.get("cinemaName") or "",
                                "showId": draft.get("showId") or "",
                                "seatIds": seat_ids,
                                "count": int(draft.get("count") or 0),
                            },
                            "actions": [
                                {"actionId": "payment_done", "label": "已完成支付", "itemId": order_data["orderId"]},
                            ],
                        })
        except Exception:
            pass  # 下单失败不阻塞，用户可后续手动处理

    # ---------- 未选日期但已选影院 → 生成未来三天场次卡片 ----------
    if (draft.get("movieId") and draft.get("cinemaId") and not draft.get("date")
            and not draft.get("showId") and not draft.get("lockId")
            and not any(c.get("type") == "date_show_list" for c in cards)):
        try:
            from datetime import date, timedelta
            from agent2.http import backend_url, get

            days_data: list[dict[str, Any]] = []
            today = date.today()
            labels = ["今天", "明天", "后天"]
            for i in range(3):
                d = today + timedelta(days=i)
                date_str = d.isoformat()
                try:
                    resp = await get(
                        backend_url("/shows"),
                        params={
                            "cinemaId": draft["cinemaId"],
                            "movieId": draft["movieId"],
                            "date": date_str,
                        },
                        timeout=2.0,
                    )
                    items: list[dict[str, Any]] = []
                    if isinstance(resp, dict):
                        data = resp.get("data") if resp.get("code") in (0, 200, None) else None
                        if isinstance(data, dict):
                            items = data.get("items") or []
                    days_data.append({"date": date_str, "label": labels[i], "shows": items})
                except Exception:
                    days_data.append({"date": date_str, "label": labels[i], "shows": []})
            if any(d["shows"] for d in days_data):
                actions: list[dict[str, Any]] = []
                for d in days_data:
                    for s in d["shows"]:
                        if isinstance(s, dict) and s.get("showId"):
                            actions.append({
                                "actionId": "select",
                                "label": "选这场",
                                "itemId": s.get("showId"),
                                "draftPatch": {"showId": s.get("showId"), "date": d["date"]},
                            })
                cards.append({
                    "cardId": f"date_shows_{uuid.uuid4().hex[:8]}",
                    "type": "date_show_list",
                    "title": "未来三天场次",
                    "payload": {"days": days_data},
                    "actions": actions,
                })
        except Exception:
            pass  # 日期卡片生成失败不阻塞主流程

    # 若座位卡片缺座位图/推荐方案，主动补拉（供前端渲染可点击座位网格并自动预选）
    for c in cards:
        if c.get("type") != "seat_plans":
            continue
        payload = c.get("payload") or {}
        show_id = payload.get("showId")
        if not show_id:
            continue
        try:
            from agent2.http import backend_url, get, post
            if not payload.get("seatMap"):
                sm = await get(backend_url(f"/shows/{show_id}/seat-map"), timeout=3.0)
                if isinstance(sm, dict) and sm.get("code") in (0, 200, None):
                    payload["seatMap"] = sm.get("data")
            # 补拉推荐方案，让前端自动预选（避免"已选 0/N 座"需要用户手挑）
            if not payload.get("plans"):
                reco_payload = await post(
                    backend_url("/reco/seats"),
                    json={
                        "showId": show_id,
                        "count": int(payload.get("count") or draft.get("count") or 2),
                        "preferRow": draft.get("preferRow") or "middle",
                        "preferSide": draft.get("preferSide") or "center",
                        "together": draft.get("together", True),
                    },
                    timeout=2.0,
                )
                if isinstance(reco_payload, dict) and reco_payload.get("code") in (0, 200, None):
                    reco_data = reco_payload.get("data") or {}
                    if isinstance(reco_data.get("plans"), list):
                        payload["plans"] = reco_data["plans"]
                    payload["compromise"] = reco_data.get("compromise")
        except Exception:
            pass  # 补拉失败不阻塞

    # 兜底：草稿已有场次、未锁座且未选座，且本轮没生成座位卡片 → 主动补一张（保证前端可选座）
    if (draft.get("showId") and not draft.get("seatIds") and not draft.get("lockId")
            and not any(c.get("type") == "seat_plans" for c in cards)):
        try:
            from agent2.http import backend_url, get
            sm = await get(backend_url(f"/shows/{draft['showId']}/seat-map"), timeout=3.0)
            if isinstance(sm, dict) and sm.get("code") in (0, 200, None):
                seat_map = sm.get("data")
                if isinstance(seat_map, dict) and seat_map.get("seats"):
                    # 补调推荐座位，让前端能自动预选（避免"已选 0/N 座"需要用户手挑）
                    reco_plans: list[dict[str, Any]] = []
                    reco_compromise: dict[str, Any] | None = None
                    try:
                        reco_payload = await post(
                            backend_url("/reco/seats"),
                            json={
                                "showId": draft["showId"],
                                "count": int(draft.get("count") or 2),
                                "preferRow": draft.get("preferRow") or "middle",
                                "preferSide": draft.get("preferSide") or "center",
                                "together": draft.get("together", True),
                            },
                            timeout=2.0,
                        )
                        if isinstance(reco_payload, dict) and reco_payload.get("code") in (0, 200, None):
                            reco_data = reco_payload.get("data") or {}
                            if isinstance(reco_data.get("plans"), list):
                                reco_plans = reco_data["plans"]
                            reco_compromise = reco_data.get("compromise")
                    except Exception:
                        pass  # 推荐失败不阻塞，前端仍可手动选座
                    cards.append({
                        "cardId": f"seat_{uuid.uuid4().hex[:8]}",
                        "type": "seat_plans",
                        "title": "选择座位",
                        "payload": {
                            "showId": draft["showId"],
                            "count": int(draft.get("count") or 2),
                            "seatMap": seat_map,
                            "plans": reco_plans,
                            "compromise": reco_compromise,
                        },
                        "actions": [
                            {
                                "actionId": "confirm",
                                "label": "确认选座",
                                "itemId": draft["showId"],
                            }
                        ],
                    })
        except Exception:
            pass  # 兜底失败不阻塞

    # 记录/更新 session 元数据
    title = message[:50] if message else None
    await _record_session(sid, user_id, title)

    # 基于实际 JWT 判定是否需要登录，而非从 LLM 回复中猜关键词。
    # LLM 可能在解释流程时提到"登录"，导致误判 needLogin=true 进入死循环。
    need_login = user_id == "anon"

    return AgentTurnEnvelope(
        data=AgentTurnResponse(
            sessionId=sid,
            replyText=reply,
            draft=_to_draft_vo(sid, draft),
            cards=cards,
            progress={},
            needLogin=need_login,
            events=[f"agent2_done:{len(tool_calls)}"],
            toolTraces=tool_calls,
        )
    )


# ---------- 会话管理 ----------


class SessionMeta(BaseModel):
    sessionId: str
    userId: str
    title: str | None = None
    createdAt: str | None = None
    lastMessageAt: str | None = None


class SessionListEnvelope(BaseModel):
    code: int = 200
    message: str = "ok"
    data: list[SessionMeta]


class SessionCreateRequest(BaseModel):
    title: str | None = None


class SessionCreateEnvelope(BaseModel):
    code: int = 200
    message: str = "ok"
    data: SessionMeta


class HistoryMessage(BaseModel):
    role: str
    content: str


class HistoryEnvelope(BaseModel):
    code: int = 200
    message: str = "ok"
    data: list[HistoryMessage]


@router.get("/sessions", response_model=SessionListEnvelope)
async def list_sessions(
    authorization: str | None = Depends(get_authorization),
) -> SessionListEnvelope:
    """获取当前用户的会话列表（按最后消息时间降序）。"""
    user_id = _extract_user_id(authorization)
    rows = await _list_sessions(user_id)
    return SessionListEnvelope(
        data=[
            SessionMeta(
                sessionId=r["session_id"],
                userId=r["user_id"],
                title=r.get("title"),
                createdAt=str(r["created_at"]) if r.get("created_at") else None,
                lastMessageAt=str(r["last_message_at"]) if r.get("last_message_at") else None,
            )
            for r in rows
        ]
    )


@router.post("/sessions", response_model=SessionCreateEnvelope)
async def create_session(
    body: SessionCreateRequest | None = None,
    authorization: str | None = Depends(get_authorization),
) -> SessionCreateEnvelope:
    """新建会话，返回 session_id。"""
    user_id = _extract_user_id(authorization)
    sid = _generate_session_id(user_id)
    title = (body.title if body else None) or "新对话"
    await _record_session(sid, user_id, title)
    return SessionCreateEnvelope(
        data=SessionMeta(
            sessionId=sid,
            userId=user_id,
            title=title,
            createdAt=datetime.datetime.now().isoformat(),
            lastMessageAt=datetime.datetime.now().isoformat(),
        )
    )


@router.get("/sessions/{session_id}/messages", response_model=HistoryEnvelope)
async def get_session_messages(
    session_id: str,
    limit: int = Query(default=5, ge=1, le=50),
    offset: int = Query(default=0, ge=0),
    authorization: str | None = Depends(get_authorization),
) -> HistoryEnvelope:
    """获取某会话的历史消息（分页，从 agent2 PostgresSaver 读取）。"""
    agent = await get_agent()
    history = await agent.get_history(session_id, limit=limit + offset)
    if len(history) > limit:
        page = history[:limit]
    else:
        page = history[-limit:]
    return HistoryEnvelope(data=[HistoryMessage(role=m["role"], content=m["content"]) for m in page])
