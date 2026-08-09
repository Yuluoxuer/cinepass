"""agent4 对外 API：与前端契约对齐的 FastAPI 端点（/agent4）。

- ``POST /agent4/turns``：监督者对话一轮（返回 replyText/draft/cards）
- ``GET/POST /agent4/sessions``：会话列表 / 新建会话
- ``GET /agent4/sessions/{id}/messages``：会话历史

外部调用 agent4 的唯一入口；图实例经 ``agent4.graph.get_agent4`` 懒加载。
"""
from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query

from agent4.api.contract import (
    AgentTurnEnvelope,
    AgentTurnRequest,
    AgentTurnResponse,
    HistoryEnvelope,
    HistoryMessage,
    SessionCreateEnvelope,
    SessionCreateRequest,
    SessionListEnvelope,
    SessionMeta,
    _compose_message,
    _extract_user_id,
    _generate_session_id,
    _list_sessions,
    _record_session,
    _to_draft_vo,
)
from agent4.api.deps import get_authorization
from agent4.api.draft_merge import resolve_draft_merge
from agent4.tools.AgentTools.booking_draft import (
    load_draft as _load_local,
    save_draft as _save_local,
    use_session_id,
)
from agent4.tools.AgentTools.draft_tools import (
    load_draft as _load_middle,
    save_draft as _save_middle,
)
from agent4.tools.Http2BackendTools.auth import use_authorization, use_location

router = APIRouter(prefix="/agent4", tags=["agent4"])

# 历史内嵌在草稿里的保留键
_HISTORY_KEY = "_history"


def _split_draft(loaded: dict[str, Any]) -> tuple[dict[str, Any], list[dict[str, str]]]:
    """把草稿记录拆成「购票草稿 + 历史」。"""
    history = loaded.get(_HISTORY_KEY) or []
    if not isinstance(history, list):
        history = []
    draft = {k: v for k, v in loaded.items() if k != _HISTORY_KEY}
    return draft, history


async def _session_owner(session_id: str) -> str | None:
    """查询会话归属用户；会话不存在或查询失败返回 None。"""
    try:
        from agent4.tools.AgentTools.booking_draft import _get_pool
        pool = await _get_pool()
        async with pool.acquire() as conn:
            row = await conn.fetchrow(
                "SELECT user_id FROM agent_sessions WHERE session_id = $1", session_id
            )
            return row["user_id"] if row else None
    except Exception:
        return None


async def _run_agent4(body: AgentTurnRequest, authorization: str | None, sid: str) -> dict[str, Any]:
    """跑一轮购票流程图，返回 {reply, cards, draft, history, events}。"""
    from agent4.graph import get_agent4  # 延迟导入，避免与 graph→MainAgent→api.cards 循环
    from agent4.graph.nodes import _QUERY_RE

    message = _compose_message(body)

    # 支付完成（前端轮询到订单已出票后点「已完成支付」）→ 直接返回下单成功，
    # 不再走图流程（避免重复锁座/重复确认）
    if body.cardAction and body.cardAction.actionId == "payment_done":
        order_id = body.cardAction.itemId or ""
        reply = (
            "🎉 恭喜您下单成功！您的电影票已出票，取票码可在「我的票夹」查看。\n\n"
            "祝您观影愉快！😊 如果还想购买其他电影，请点击「新建对话」重新开始。"
        )
        if order_id:
            reply = (
                "🎉 恭喜您下单成功！\n"
                f"📦 订单号：{order_id}\n\n"
                "您的电影票已出票，取票码可在「我的票夹」查看。祝您观影愉快！😊\n\n"
                "如果还想购买其他电影，请点击「新建对话」重新开始。"
            )
        try:
            draft = await _load_middle(sid) or {}
        except Exception:
            draft = {}
        # 清空草稿（订单已完成），避免残留状态
        try:
            await _save_middle({}, sid)
        except Exception:
            pass
        return {"reply": reply, "cards": [], "draft": {}, "history": [], "events": 1}

    # 历史：存在本地 booking_drafts 表的 _history 键（不污染中台草稿）
    local = {}
    try:
        local = await _load_local(sid) or {}
    except Exception:
        pass
    history = local.get(_HISTORY_KEY) or []
    if not isinstance(history, list):
        history = []

    # 跨轮阶段：存在本地草稿的内部字段（_stage/_intent/_confirmed）
    # 点卡操作（cardAction）或查询性提问（"有什么电影可看"）→ 回到提取/选片阶段，
    # 避免残留的 confirm/pay 阶段让新查询走到旧确认流程
    stage = local.get("_stage") or "intent"
    if body.cardAction is not None:
        stage = "collect"
    elif _QUERY_RE.search(message):
        stage = "collect"
    intent = local.get("_intent")
    confirmed = bool(local.get("_confirmed"))

    # 草稿：从中台读取（与手动页面共用同一份），再合并 clientDraft + 点卡（需 JWT 上下文）
    _DRAFT_KEYS = (
        "movieId", "filmTitle", "cinemaId", "cinemaName", "showId",
        "date", "timeWindow", "count", "seatIds",
        "preferRow", "preferSide", "together", "lockId", "orderId", "expireAt",
    )

    def _pick(src: dict[str, Any] | None) -> dict[str, Any]:
        return {k: v for k, v in (src or {}).items() if k in _DRAFT_KEYS and v not in (None, "", [])}

    draft: dict[str, Any] = {}
    graph = get_agent4()
    with use_authorization(authorization), use_session_id(sid), use_location(
        body.latitude, body.longitude
    ):
        # ① 页面草稿同步：乐观锁合并 clientDraft + clientDraftVersion
        try:
            existing = await _load_middle(sid) or {}
            merged, _new_v = resolve_draft_merge(existing, body.clientDraft, body.clientDraftVersion)
            # ② 点卡操作：明确选择优先（覆盖）
            patch = body.cardAction.draftPatch if body.cardAction else None
            if patch:
                merged = {**merged, **_pick(patch)}
                # 用户通过「X张」卡片明确确认了票数 → 记录内部标记（不落中台草稿），
                # 供 collect_node 判断是否还需先问票数
                if "count" in patch:
                    merged["_count_set"] = True
            await _save_middle(merged, sid)
            draft = merged
        except Exception:
            pass  # 中台草稿不可用时忽略

        # thread_id=sessionId：短期记忆经 PostgresSaver 落库；authorization 不入图状态
        config = {"configurable": {"thread_id": sid}}
        result = await graph.ainvoke({
            "message": message,
            "stage": stage,
            "intent": intent,
            "confirmed": confirmed,
            "history": history,
            "reply_parts": [],
            "cards": [],
            "bookingdraft": draft,
            "sessionId": sid,
            "authorization": authorization,
            "latitude": body.latitude,
            "longitude": body.longitude,
            "steps": 0,
            "last_reply": "",
        }, config)

    reply_parts = result.get("reply_parts") or []
    reply = "\n".join(part for part in reply_parts if part).strip() or "抱歉，我暂时无法处理这个请求。"
    cards = result.get("cards") or []
    new_draft = result.get("bookingdraft") or draft
    # 历史由 api 层统一记录（不依赖图节点写 history）：本轮 user + assistant 追加。
    # assistant 消息附带 cards，供历史会话恢复时重新渲染动态卡片
    new_history = history + [
        {"role": "user", "content": message},
        {"role": "assistant", "content": reply, "cards": cards},
    ]
    new_stage = result.get("stage") or stage
    new_intent = result.get("intent") or intent
    new_confirmed = bool(result.get("confirmed") or confirmed)

    # 回写：草稿 → 中台；历史 → 本地（去重）
    deduped: list[dict[str, str]] = []
    seen: set[str] = set()
    for h in new_history:
        key = f"{h.get('role')}|{h.get('content')}"
        if key in seen:
            continue
        seen.add(key)
        deduped.append(h)
    if sid:
        try:
            with use_authorization(authorization):
                await _save_middle(new_draft, sid)
        except Exception:
            pass
        try:
            clean_local = {k: v for k, v in local.items() if k != _HISTORY_KEY and not str(k).startswith("_")}
            await _save_local(
                {
                    **clean_local,
                    _HISTORY_KEY: deduped[-40:],
                    "_stage": new_stage,
                    "_intent": new_intent,
                    "_confirmed": new_confirmed,
                },
                sid,
            )
        except Exception:
            pass

    return {
        "reply": reply,
        "cards": cards,
        "draft": new_draft,
        "history": deduped,
        "events": result.get("steps", 0),
    }


@router.post("/turns", response_model=AgentTurnEnvelope)
async def agent4_turns(
    body: AgentTurnRequest,
    authorization: str | None = Depends(get_authorization),
) -> AgentTurnEnvelope:
    """监督者模式对话一轮。"""
    user_id = _extract_user_id(authorization)
    sid = (body.sessionId or "").strip() or _generate_session_id(user_id)
    message = _compose_message(body)

    # 会话归属校验（IDOR 防护）：已存在的会话必须属于当前用户。
    # - 匿名会话（owner="anon"）被登录用户访问：转移所有权给当前用户（登录前后同会话）
    # - 已登录用户的会话被匿名访问：拒绝（防止匿名越权读他人会话）
    # - 不同登录用户之间：拒绝
    if body.sessionId and body.sessionId.strip():
        owner = await _session_owner(sid)
        if owner is not None and owner != user_id:
            if owner == "anon" and user_id != "anon":
                # 登录后接管匿名会话：转移归属
                await _record_session(sid, user_id)
            else:
                raise HTTPException(status_code=403, detail="无权访问该会话")

    if not message:
        return AgentTurnEnvelope(
            data=AgentTurnResponse(
                sessionId=sid,
                replyText="请告诉我您想看什么电影，或者有什么需要帮您处理的？",
                draft=_to_draft_vo(sid, {}),
            )
        )

    out = await _run_agent4(body, authorization, sid)
    await _record_session(sid, user_id, message[:50] if message else None)

    need_login = any(kw in out["reply"] for kw in ("登录", "JWT", "未携带", "鉴权"))
    return AgentTurnEnvelope(
        data=AgentTurnResponse(
            sessionId=sid,
            replyText=out["reply"],
            draft=_to_draft_vo(sid, out["draft"]),
            cards=out["cards"],
            progress={},
            needLogin=need_login,
            events=[f"agent4_done:{out['events']}"],
            toolTraces=[],
        )
    )


# ---------- 会话管理 ----------


@router.get("/sessions", response_model=SessionListEnvelope)
async def list_sessions(
    authorization: str | None = Depends(get_authorization),
) -> SessionListEnvelope:
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
    user_id = _extract_user_id(authorization)
    sid = _generate_session_id(user_id)
    title = (body.title if body else None) or "新对话"
    await _record_session(sid, user_id, title)
    import datetime as _dt
    now = _dt.datetime.now().isoformat()
    return SessionCreateEnvelope(
        data=SessionMeta(
            sessionId=sid,
            userId=user_id,
            title=title,
            createdAt=now,
            lastMessageAt=now,
        )
    )


@router.get("/sessions/{session_id}/messages", response_model=HistoryEnvelope)
async def get_session_messages(
    session_id: str,
    limit: int = Query(default=5, ge=1, le=50),
    offset: int = Query(default=0, ge=0),
    authorization: str | None = Depends(get_authorization),
) -> HistoryEnvelope:
    user_id = _extract_user_id(authorization)
    rows = await _list_sessions(user_id)
    owned_ids = {r["session_id"] for r in rows}
    if session_id not in owned_ids:
        return HistoryEnvelope(data=[])
    try:
        loaded = await _load_local(session_id) or {}
    except Exception:
        loaded = {}
    _, history = _split_draft(loaded)
    if offset:
        page = history[offset:offset + limit]
    else:
        page = history[-limit:]
    return HistoryEnvelope(
        data=[
            HistoryMessage(
                role=m.get("role", "assistant"),
                content=m.get("content", ""),
                cards=m.get("cards") or [],
            )
            for m in page
            if isinstance(m, dict)
        ]
    )
