"""agent4 购票流程的节点实现。

流程节点：
1. ``optimize_node``：LLM 优化用户提示词
2. ``intent_node``：LLM 意图识别（购票 or 聊天）
3. ``chat_node``：聊天回复（可读电影/影院数据）
4. ``extract_node``：LLM struct 提取 bookingdraft 字段
5. ``collect_node``：按缺失字段生成追问 + 动态卡片
6. ``confirm_node``：展示草稿确认；用户确认则进入支付
7. ``pay_node``：锁座 + 生成支付二维码卡片
"""
from __future__ import annotations

import ast
import json
import re
import uuid
from typing import Any, Literal

from langchain_core.messages import SystemMessage, ToolMessage
from pydantic import BaseModel, Field

from agent4.api.cards import build_cards
from agent4.llm import get_llm
from agent4.state import Agent4State
from agent4.tools.AgentTools import (
    list_shows,
    lock_seats,
    recommend_seats,
    search_cinemas,
    search_knowledge_base,
    search_movies,
    search_movies_by_time_range,
)
from agent4.tools.Http2BackendTools.auth import get_authorization
from agent4.tools.Http2BackendTools.http import backend_url, get, post

# 购票必需字段（按询问顺序）
REQUIRED_ORDER = ("movieId", "cinemaId", "date", "showId", "count", "seatIds")

_CONFIRM_RE = re.compile(r"确认|确定|下单|支付|付款|可以|好的|没问题|就按|就这样")

# timeWindow → (开始小时, 结束小时)；后端 /shows/movies 用具体时间戳，由 agent 侧翻译
TIME_WINDOW_RANGE: dict[str, tuple[int, int]] = {
    "morning": (6, 12),
    "afternoon": (12, 18),
    "evening": (18, 24),
}
TW_LABEL: dict[str, str] = {"morning": "上午", "afternoon": "下午", "evening": "晚上"}


def _iso_ts(date: str, hour: int) -> str:
    """把 date + hour 拼成 ISO-8601 时间戳（东八区）。"""
    return f"{date}T{hour:02d}:00:00+08:00"


def _iso_ts_end(date_str: str) -> str:
    """date 当天结束时间戳（23:59:59 东八区）。"""
    return f"{date_str}T23:59:59+08:00"


def _resolve_range(msg: str, draft: dict[str, Any]) -> dict[str, Any]:
    """检测消息中的区间时间词，确定性计算日期区间（不依赖 LLM 提取）。

    - "这周/本周" → 本周一 ~ 本周日
    - "下周"     → 下周一 ~ 下周日
    - "周末"     → 本周六 ~ 本周日
    - "最近/这几天" → 今天 ~ 今天+6
    """
    try:
        from datetime import date as _date, timedelta
        today = _date.today()
        if re.search(r"这周|本周", msg):
            monday = today - timedelta(days=today.weekday())
            draft["startDate"] = monday.isoformat()
            draft["endDate"] = (monday + timedelta(days=6)).isoformat()
        elif re.search(r"下周", msg):
            monday = today - timedelta(days=today.weekday()) + timedelta(days=7)
            draft["startDate"] = monday.isoformat()
            draft["endDate"] = (monday + timedelta(days=6)).isoformat()
        elif re.search(r"周末", msg):
            saturday = today - timedelta(days=today.weekday()) + timedelta(days=5)
            if saturday < today:
                saturday += timedelta(days=7)
            draft["startDate"] = saturday.isoformat()
            draft["endDate"] = (saturday + timedelta(days=1)).isoformat()
        elif re.search(r"最近|这几天", msg):
            draft["startDate"] = today.isoformat()
            draft["endDate"] = (today + timedelta(days=6)).isoformat()
    except Exception:
        pass
    return draft


# ---------- LLM 结构化输出 ----------


class BookingIntent(BaseModel):
    intent: Literal["booking", "chat"]
    reason: str = ""


class BookingDraftInfo(BaseModel):
    """从用户消息中提取的购票信息。"""

    movieId: str | None = None
    filmTitle: str | None = None
    cinemaId: str | None = None
    cinemaName: str | None = None
    date: str | None = None
    startDate: str | None = None  # 区间查询开始日期（YYYY-MM-DD，"这周"等）
    endDate: str | None = None    # 区间查询结束日期
    timeWindow: str | None = None  # morning/afternoon/evening
    genre: str | None = None  # 影片类型（如"喜剧"）
    showId: str | None = None
    count: int | None = None
    seatIds: list[str] = Field(default_factory=list)
    preferRow: str | None = None
    preferSide: str | None = None
    together: bool | None = None


# ---------- 辅助 ----------


def _missing_fields(draft: dict[str, Any]) -> list[str]:
    """返回购票草稿缺失的字段（按必需顺序）。"""
    miss = []
    for k in REQUIRED_ORDER:
        v = draft.get(k)
        if v in (None, "", [], {}):
            miss.append(k)
    return miss


def _is_confirm(message: str) -> bool:
    return bool(_CONFIRM_RE.search(message or ""))


def _norm_seat_ids(value: Any) -> list[str]:
    if isinstance(value, list):
        return [str(s).strip() for s in value if str(s).strip()]
    if isinstance(value, str):
        return [s.strip() for s in value.split(",") if s.strip()]
    return []


# 查询性提问关键词：命中且未明确选片时，清空旧草稿选择，回到选片阶段。
# 只在用户查询「电影」时才清空 movieId；查影院/场次（"有哪些影院"）不影响已选影片。
_QUERY_RE = re.compile(
    r"(?:有什么|哪些|推荐|看什么|上映|片单|能看).{0,6}(?:电影|影片|片子|片)"
    r"|(?:有什么|哪些).{0,6}(?:看|可以看|能看|热映|上映)"
)


def _day_label(date_str: str) -> str:
    """把日期转成'今天/明天/后天'友好标签；非近三天返回原日期。"""
    try:
        from datetime import date as _date, timedelta
        target = _date.fromisoformat(date_str)
        delta = (target - _date.today()).days
        if delta == 0:
            return "今天"
        if delta == 1:
            return "明天"
        if delta == 2:
            return "后天"
    except Exception:
        pass
    return date_str


def _safe_parse(raw: Any) -> Any:
    """健壮解析工具返回值：兼容 dict / str(dict) / JSON 字符串 / 嵌套字符串。

    langchain tool 返回 ``str(data)``（Python dict 的 repr，单引号），
    也可能被包装成 JSON 字符串或 dict；统一解析为 dict，失败返回 None。
    """
    if isinstance(raw, dict):
        return raw
    if not isinstance(raw, str):
        return None
    text = raw.strip()
    if not text:
        return None
    # 兼容嵌套字符串：{{...}} 或 "{...}" 再解析一层
    for _ in range(3):
        try:
            parsed = ast.literal_eval(text)
        except Exception:
            try:
                parsed = json.loads(text)
            except Exception:
                return None
        if isinstance(parsed, dict):
            return parsed
        if isinstance(parsed, str):
            text = parsed.strip()
            continue
        return None
    return None


def _filter_by_next_show_date(raw: str, start: str, end: str) -> str:
    """从 /movies 原始 JSON 中过滤 nextShowDate 落在 [start, end] 的电影，返回新 JSON 字符串。

    兜底方案：后端 /shows/movies 存在 404 bug（有场次但按时间段查不到）时，
    用 /movies 全量热映 + nextShowDate（最近排片日期）在区间内过滤。
    """
    try:
        data = ast.literal_eval(raw)
    except Exception:
        return raw
    if not isinstance(data, dict):
        return raw
    inner = data.get("data") if isinstance(data.get("data"), dict) else data
    items = inner.get("items") or []
    kept = [
        m for m in items
        if isinstance(m, dict) and start <= str(m.get("nextShowDate") or "") <= end
    ]
    inner["items"] = kept
    return str(data)


async def _range_movie_cards(
    draft: dict[str, Any],
    start_ts: str,
    end_ts: str,
    start_date: str,
    end_date: str,
) -> list[dict[str, Any]]:
    """按时间区间查询电影卡片；/shows/movies 不可用（404 bug）时用 /movies+nextShowDate 兜底。"""
    data = await search_movies_by_time_range.ainvoke({
        "start_time": start_ts,
        "end_time": end_ts,
    })
    cards = build_cards([{"name": "search_movies", "content": data}], draft)
    if cards:
        return cards
    # 兜底必须拉大 size：默认 size=10 只取前 10 部热映，目标影片（如《封神二》）排位靠后会被漏掉
    all_data = await search_movies.ainvoke({"size": 50})
    fallback = _filter_by_next_show_date(all_data, start_date, end_date)
    return build_cards([{"name": "search_movies", "content": fallback}], draft)


def _filter_cards_by_genre(cards: list[dict[str, Any]], genre: str) -> list[dict[str, Any]]:
    """按类型过滤电影卡片（payload.movies 的 genres 字段包含匹配）。"""
    out: list[dict[str, Any]] = []
    for c in cards:
        if c.get("type") != "movie_list":
            out.append(c)
            continue
        movies = (c.get("payload") or {}).get("movies") or []
        filtered = [
            m for m in movies
            if isinstance(m, dict) and genre in (m.get("genres") or [])
        ]
        if not filtered:
            continue
        c["payload"]["movies"] = filtered
        ids = {m.get("movieId") for m in filtered}
        c["actions"] = [a for a in (c.get("actions") or []) if a.get("itemId") in ids]
        out.append(c)
    return out


def _reply(state: Agent4State, text: str, cards: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    return {
        "reply_parts": (state.get("reply_parts") or []) + [text],
        "cards": (state.get("cards") or []) + (cards or []),
    }


# ---------- 节点 ----------


async def optimize_node(state: Agent4State) -> dict[str, Any]:
    """LLM 优化用户提示词，使后续意图识别/提取更准确。"""
    llm = get_llm()
    msg = state.get("message") or ""
    prompt = (
        "你是购票助手的提示词优化器。把下面的用户请求改写得更清晰完整（保留原意、补充省略主语），"
        "只输出改写后的内容，不要解释。\n用户：{msg}".format(msg=msg)
    )
    try:
        resp = await llm.ainvoke(prompt)
        optimized = getattr(resp, "content", None)
        optimized = optimized if isinstance(optimized, str) and optimized.strip() else msg
    except Exception:
        optimized = msg
    return {"optimized_message": optimized, "stage": "intent"}


# 购票意图信号词：命中才可能判为 booking；完全不命中时一律 chat，
# 避免 LLM 失败被兜底成 booking 后，把"软件测试基础包括哪些"这类普通问答
# 误走购票提取流程（答非所问 + 旧草稿残留）
_BOOKING_SIGNAL_RE = re.compile(
    r"电影|影片|片子|片|热映|上映|排片|场次|影院|影城|"
    r"买票|订票|购票|选座|锁座|座位|票|"
    r"看.{0,4}(电影|片|球|侠|战|记|传|神|鬼|爱|情)|"
    r"万达|CGV|金逸|横店|星美|UME|保利|中影|华谊|IMAX"
)


def _rule_intent(message: str) -> str:
    """确定性意图规则：含购票信号词 → booking；否则 → chat。"""
    return "booking" if _BOOKING_SIGNAL_RE.search(message or "") else "chat"


async def intent_node(state: Agent4State) -> dict[str, Any]:
    """LLM 意图识别：购票 or 聊天。"""
    llm = get_llm()
    msg = state.get("optimized_message") or state.get("message") or ""
    prompt = (
        "判断用户意图是否是购票或有购票倾向（看片/选片/买票/选影院/选场次/选座/下单等）。\n"
        "用户消息：{msg}".format(msg=msg)
    )
    try:
        # DeepSeek 不支持 json_schema response_format，需显式走 function_calling，
        # 否则每次结构化输出都 400、被 except 兜底成 booking（意图识别失效）
        structured = llm.with_structured_output(BookingIntent, method="function_calling")
        decision = await structured.ainvoke(prompt)
        intent: str = decision.intent
        # LLM 结果与确定性规则冲突时以规则为准（如明显非购票问题被 LLM 误判）
        rule = _rule_intent(msg)
        if rule == "chat":
            intent = "chat"
    except Exception:
        intent = _rule_intent(msg)  # LLM 失败：按信号词规则，不默认 booking
    if intent == "chat":
        return {"intent": "chat", "stage": "chat"}
    return {"intent": "booking"}


async def chat_node(state: Agent4State) -> dict[str, Any]:
    """聊天回复节点（普通问答，可检索知识库 / 查电影 / 影院数据）。"""
    llm = get_llm()
    hist = state.get("history") or []
    msgs: list[Any] = [
        SystemMessage(
            content=(
                "你是电影购票助手，负责闲聊与普通问答。回答尽量简洁友好。"
                "当用户询问退票政策、改签规则、退款到账、使用方法、操作指南、常见问题，"
                "或某影院的位置、活动等运营信息时，请先调用 search_knowledge_base 检索知识库，"
                "再依据检索结果回答；知识库没有相关内容时如实说明，不要编造。"
            )
        )
    ]
    for h in hist[-4:]:
        role = "human" if h.get("role") == "user" else "assistant"
        msgs.append((role, h.get("content", "")))
    msgs.append(("human", state.get("message") or ""))
    try:
        # 绑定知识库检索工具：命中政策/运营类问题时先检索再作答
        agent = llm.bind_tools([search_knowledge_base])
        resp = await agent.ainvoke(msgs)
        tool_calls = getattr(resp, "tool_calls", None) or []
        if tool_calls:
            msgs.append(resp)
            for tc in tool_calls:
                if (tc.get("name") or "") == "search_knowledge_base":
                    query = (tc.get("args") or {}).get("query") or ""
                    result = await search_knowledge_base.ainvoke({"query": query})
                    msgs.append(
                        ToolMessage(content=str(result), tool_call_id=tc.get("id") or "")
                    )
            final = await llm.ainvoke(msgs)
            reply = getattr(final, "content", None)
        else:
            reply = getattr(resp, "content", None)
        reply = reply if isinstance(reply, str) and reply.strip() else "好的，我在听。"
    except Exception:
        reply = "抱歉，我暂时无法处理这个请求。"
    return _reply(state, reply)


async def extract_node(state: Agent4State) -> dict[str, Any]:
    """LLM 提取 bookingdraft 字段，合并到草稿，计算缺失字段。"""
    llm = get_llm()
    msg = state.get("optimized_message") or state.get("message") or ""
    existing = state.get("bookingdraft") or {}
    prompt = (
        "从用户消息中提取购票信息（影片/影院/日期/场次/数量/座位等）。\n"
        "用户说'这周/下周/这几天/最近/周末'等**区间**时，把 startDate/endDate 填为具体日期（YYYY-MM-DD，"
        "如'这周'=本周一到本周日，'周末'=本周六到本周日）。\n"
        "已有草稿：{draft}\n用户消息：{msg}\n"
        "只输出结构化结果，未提到的字段留空。".format(
            draft=json.dumps(existing, ensure_ascii=False), msg=msg
        )
    )
    try:
        structured = llm.with_structured_output(BookingDraftInfo, method="function_calling")
        info = await structured.ainvoke(prompt)
        data = info.model_dump()
    except Exception:
        data = {}

    draft = {**existing}
    for k, v in data.items():
        if v not in (None, "", [], {}):
            draft[k] = v
    if data.get("seatIds"):
        draft["seatIds"] = _norm_seat_ids(data["seatIds"])
    # 确定性解析区间时间词（这周/下周/周末等），优先于 LLM 提取的单日 date。
    # 必须用「原始消息」而非 optimize 改写后的消息：LLM 改写可能把"这周"误改写成
    # "明天"等单日词，导致区间匹配失败、误走单日分支（回复"明天暂时没有电影"）。
    raw_msg = state.get("message") or ""
    draft = _resolve_range(raw_msg, draft)
    if draft.get("startDate") and draft.get("endDate"):
        draft.pop("date", None)  # 区间查询时移除单日 date，避免误走单日分支
    # 查询性提问（"有什么电影可看"）→ 无论草稿有无旧选择，都清空并回到选片阶段，
    # 避免残留的 movieId（如从影片详情页进入助手）导致跳过电影选择直接问影院
    if _QUERY_RE.search(msg):
        for k in ("movieId", "filmTitle", "cinemaId", "cinemaName", "showId", "seatIds"):
            draft.pop(k, None)
    # 消息不含购票信号词（如"软件测试基础包括哪些"被 LLM 误提取）→ 清空旧草稿购票字段，
    # 避免上一轮残留的 startDate/endDate/genre/movieId 等污染本轮（答非所问）
    if not _BOOKING_SIGNAL_RE.search(state.get("message") or ""):
        for k in (
            "movieId", "filmTitle", "cinemaId", "cinemaName", "showId",
            "date", "startDate", "endDate", "timeWindow", "genre", "count",
            "seatIds", "preferRow", "preferSide", "together",
        ):
            draft.pop(k, None)
    missing = _missing_fields(draft)
    return {"bookingdraft": draft, "missing": missing, "stage": "collect" if missing else "confirm"}


async def collect_node(state: Agent4State) -> dict[str, Any]:
    """按缺失字段生成追问 + 动态卡片（图形化，不用纯文字）。"""
    draft = state.get("bookingdraft") or {}
    missing = state.get("missing") or []
    first = missing[0] if missing else None
    cards: list[dict[str, Any]] = []
    reply = ""

    if first == "movieId":
        date = draft.get("date") or ""
        tw = draft.get("timeWindow") or ""
        genre = draft.get("genre") or ""
        start_date = draft.get("startDate") or ""
        end_date = draft.get("endDate") or ""
        # 「即将上映」：查 coming_soon，返回全量（前端 movie_list 卡片本地分页，每页 5 部）
        if re.search(r"即将上映|coming ?soon|待映|即将", state.get("message") or ""):
            data = await search_movies.ainvoke({"status": "coming_soon", "size": 100})
            cards = build_cards([{"name": "search_movies", "content": data}], draft)
            reply = "即将上映的电影如下，请选择：" if cards else "亲，暂时没有即将上映的电影"
        elif start_date and end_date:
            # 日期区间（如"这周"）：startDate 00:00 ~ endDate 23:59
            cards = await _range_movie_cards(
                draft, _iso_ts(start_date, 0), _iso_ts_end(end_date), start_date, end_date
            )
            if genre:
                cards = _filter_cards_by_genre(cards, genre)
            if cards:
                reply = f"{start_date}~{end_date}有排片的{genre}电影如下，请选择："
            else:
                reply = f"亲，{start_date}~{end_date}暂时没有{genre}电影可以查看"
        elif date and tw in TIME_WINDOW_RANGE:
            start_h, end_h = TIME_WINDOW_RANGE[tw]
            cards = await _range_movie_cards(
                draft, _iso_ts(date, start_h), _iso_ts(date, end_h), date, date
            )
            if genre:
                cards = _filter_cards_by_genre(cards, genre)
            day = _day_label(date)
            if cards:
                reply = f"{day}{TW_LABEL[tw]}有排片的{genre}电影如下，请选择："
            else:
                reply = f"亲，{day}{TW_LABEL[tw]}暂时没有{genre}电影可以查看"
        elif date:
            # 单日全天：date 00:00 ~ 23:59
            cards = await _range_movie_cards(
                draft, _iso_ts(date, 0), _iso_ts_end(date), date, date
            )
            if genre:
                cards = _filter_cards_by_genre(cards, genre)
            day = _day_label(date)
            if cards:
                reply = f"{day}有排片的{genre}电影如下，请选择："
            else:
                reply = f"亲，{day}暂时没有{genre}电影可以查看"
        elif genre:
            data = await search_movies.ainvoke({"genre": genre})
            cards = build_cards([{"name": "search_movies", "content": data}], draft)
            reply = f"为您找到这些{genre}电影，请选择：" if cards else f"亲，暂时没有{genre}电影可以查看"
        else:
            data = await search_movies.ainvoke({})
            cards = build_cards([{"name": "search_movies", "content": data}], draft)
            reply = "您想看哪部电影？请选择上面的影片。"
    elif first == "cinemaId":
        # 必须传 movie_id：只返回有该电影排片的影院，而不是附近所有影院。
        # 无位置时也直接查中台（/cinemas 的 lat/lng/movieId 均可选，按 movieId 过滤全国有排片影院）
        try:
            from agent4.tools.Http2BackendTools.http import backend_url, get as http_get
            payload = await http_get(
                backend_url("/cinemas"),
                params={"movieId": draft.get("movieId") or "", "page": 1, "size": 20},
                timeout=3.0,
            )
            data = str(payload) if payload is not None else '{"code": 0, "data": {"items": []}}'
        except Exception:
            data = '{"code": 0, "data": {"items": []}}'
        cards = build_cards([{"name": "searchCinemas", "content": data}], draft)
        reply = "您想选择哪家影院？请选择上面的影院。"
    elif first == "date":
        # 已选影院 → 主动查未来三天场次，生成日期 tab 卡片，让用户直接选有排片的天
        try:
            from datetime import date as _date, timedelta
            from agent4.tools.Http2BackendTools.http import backend_url, get as http_get
            cinema_id = draft.get("cinemaId") or ""
            movie_id = draft.get("movieId") or ""
            days_data: list[dict[str, Any]] = []
            today = _date.today()
            labels = ["今天", "明天", "后天"]
            for i in range(3):
                d = today + timedelta(days=i)
                date_str = d.isoformat()
                try:
                    resp = await http_get(
                        backend_url("/shows"),
                        params={"cinemaId": cinema_id, "movieId": movie_id, "date": date_str},
                        timeout=2.0,
                    )
                    items = []
                    if isinstance(resp, dict):
                        inner = resp.get("data") if isinstance(resp.get("data"), dict) else resp
                        items = inner.get("items") or []
                    days_data.append({
                        "date": date_str,
                        "label": labels[i],
                        "shows": [s for s in items if isinstance(s, dict)],
                    })
                except Exception:
                    days_data.append({"date": date_str, "label": labels[i], "shows": []})
            has_shows = [d for d in days_data if d["shows"]]
            if has_shows:
                import uuid as _uuid_d
                actions = []
                for d in days_data:
                    for s in d["shows"]:
                        if s.get("showId"):
                            actions.append({
                                "actionId": "select",
                                "label": "选这场",
                                "itemId": s["showId"],
                                "draftPatch": {"showId": s["showId"], "date": d["date"]},
                            })
                cards.append({
                    "cardId": f"date_shows_{_uuid_d.uuid4().hex[:8]}",
                    "type": "date_show_list",
                    "title": "未来三天场次",
                    "payload": {"days": days_data},
                    "actions": actions,
                })
                date_hint = "、".join(f"{d['label']}({d['date'][5:]})" for d in has_shows)
                reply = f"以下三天有排片：{date_hint}。请选择场次："
            else:
                reply = "亲，未来三天暂无排片，请换个日期或影院试试。"
        except Exception:
            reply = "您想哪天看电影？"
    elif first == "showId":
        data = await list_shows.ainvoke({
            "cinema_id": draft.get("cinemaId", ""),
            "movie_id": draft.get("movieId", ""),
            "date": draft.get("date") or "",
            "time_window": draft.get("timeWindow") or "",
        })
        cards = build_cards([{"name": "list_shows", "content": data}], draft)
        reply = "您想看哪个场次？请选择上面的场次。"
    elif first == "count":
        # 票数确认：生成 ask 卡片（前端最多 4 张），用户点击后 draftPatch 写 count
        import uuid as _uuid_cnt
        cards.append({
            "cardId": f"ask_count_{_uuid_cnt.uuid4().hex[:8]}",
            "type": "ask",
            "title": "请问需要购买几张票呢？",
            "payload": {
                "prompt": "请问需要购买几张票呢？",
                "suggestions": ["1张", "2张", "3张", "4张"],
            },
            "actions": [
                {"actionId": "fill_slot", "label": "1张", "itemId": "1张", "draftPatch": {"count": 1}},
                {"actionId": "fill_slot", "label": "2张", "itemId": "2张", "draftPatch": {"count": 2}},
                {"actionId": "fill_slot", "label": "3张", "itemId": "3张", "draftPatch": {"count": 3}},
                {"actionId": "fill_slot", "label": "4张", "itemId": "4张", "draftPatch": {"count": 4}},
            ],
        })
        reply = "请问需要买几张票呢？"
    elif first == "seatIds":
        # 前端 clientDraft 默认 count=2 会被合并进来，导致跳过问票数。
        # 若用户尚未明确确认票数（无 _count_set 标记），先问票数再选座。
        if not draft.get("_count_set"):
            import uuid as _uuid_cnt2
            cards.append({
                "cardId": f"ask_count_{_uuid_cnt2.uuid4().hex[:8]}",
                "type": "ask",
                "title": "请问需要购买几张票呢？",
                "payload": {
                    "prompt": "请问需要购买几张票呢？",
                    "suggestions": ["1张", "2张", "3张", "4张"],
                },
                "actions": [
                    {"actionId": "fill_slot", "label": "1张", "itemId": "1张", "draftPatch": {"count": 1}},
                    {"actionId": "fill_slot", "label": "2张", "itemId": "2张", "draftPatch": {"count": 2}},
                    {"actionId": "fill_slot", "label": "3张", "itemId": "3张", "draftPatch": {"count": 3}},
                    {"actionId": "fill_slot", "label": "4张", "itemId": "4张", "draftPatch": {"count": 4}},
                ],
            })
            reply = "请问需要买几张票呢？"
        else:
            # 同时拉座位图 + 推荐方案，避免前端"座位图加载中…"卡死
            try:
                from agent4.tools.AgentTools import get_seat_map
                show_id = draft.get("showId", "")
                seat_map_resp = await get_seat_map.ainvoke({"show_id": show_id})
                reco_resp = await recommend_seats.ainvoke({
                    "show_id": show_id,
                    "count": int(draft.get("count") or 2),
                })
                # 手动合并成一张 seat_plans 卡片（seatMap + plans 都有）
                sm_raw = _safe_parse(seat_map_resp)
                sm_data = sm_raw.get("data") if isinstance(sm_raw, dict) and isinstance(sm_raw.get("data"), dict) else sm_raw
                reco_raw = _safe_parse(reco_resp)
                reco_data = reco_raw.get("data") if isinstance(reco_raw, dict) and isinstance(reco_raw.get("data"), dict) else reco_raw
                seat_map = sm_data if isinstance(sm_data, dict) and sm_data.get("seats") else None
                plans = reco_data.get("plans") if isinstance(reco_data, dict) and isinstance(reco_data.get("plans"), list) else []
                compromise = reco_data.get("compromise") if isinstance(reco_data, dict) else None
                if seat_map:
                    import uuid as _uuid_seat
                    cards.append({
                        "cardId": f"seats_{_uuid_seat.uuid4().hex[:8]}",
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
                                "draftPatch": {"showId": show_id, "seatIds": []},
                            }
                        ],
                    })
                    reply = "请选择座位："
                else:
                    # 座位图拉取失败，退回推荐方案卡片
                    data = await recommend_seats.ainvoke({
                        "show_id": draft.get("showId", ""),
                        "count": int(draft.get("count") or 2),
                    })
                    cards = build_cards([{"name": "recommendSeats", "content": data}], draft)
                    reply = "请选择座位："
            except Exception:
                data = await recommend_seats.ainvoke({
                    "show_id": draft.get("showId", ""),
                    "count": int(draft.get("count") or 2),
                })
                cards = build_cards([{"name": "recommendSeats", "content": data}], draft)
                reply = "请选择座位："
    else:
        reply = "请补充购票信息。"

    out = _reply(state, reply, cards)
    out["stage"] = "collect"
    return out


# 点卡合成消息（"我选择了座位 X，请帮我锁座并确认下单"）不算用户主动确认，
# 否则点「确认选座」卡片会跳过确认环节直接进支付
_CARD_MSG_RE = re.compile(r"我选择了座位|点卡操作|请帮我锁座")


async def _format_seat_names(show_id: str, seat_ids: list[str]) -> dict[str, str]:
    """拉取座位图，把 seatId 映射为可读座位名（如 3排4座）。"""
    try:
        from agent4.tools.AgentTools import get_seat_map
        resp = await get_seat_map.ainvoke({"show_id": show_id})
        parsed = _safe_parse(resp)
        data = parsed.get("data") if isinstance(parsed, dict) and isinstance(parsed.get("data"), dict) else parsed
        seats = (data or {}).get("seats") or []
        m = {}
        for s in seats:
            if isinstance(s, dict) and s.get("seatId") and s.get("seatName"):
                m[s["seatId"]] = s["seatName"]
        return m
    except Exception:
        return {}


async def confirm_node(state: Agent4State) -> dict[str, Any]:
    """展示草稿摘要确认；用户主动确认（非点卡消息）才进入支付。"""
    draft = state.get("bookingdraft") or {}
    msg = state.get("message") or ""
    # 点卡合成消息不算用户主动确认；只有用户文字回复「确认/可以/下单」等才算
    if _is_confirm(msg) and not _CARD_MSG_RE.search(msg):
        return {"confirmed": True, "stage": "pay"}

    seat_ids = _norm_seat_ids(draft.get("seatIds"))
    show_id = draft.get("showId") or ""

    # 拉取场次可读信息（时间/影厅/单价）与座位名
    show_time = draft.get("date") or ""
    hall_name = ""
    unit_price = None
    if show_id:
        try:
            from agent4.tools.AgentTools import get_show
            show_resp = await get_show.ainvoke({"show_id": show_id})
            show_parsed = _safe_parse(show_resp)
            show_data = show_parsed.get("data") if isinstance(show_parsed, dict) and isinstance(show_parsed.get("data"), dict) else show_parsed
            if isinstance(show_data, dict):
                st = show_data.get("startTime") or ""
                if st:
                    show_time = st.replace("T", " ").replace("Z", "").replace("+08:00", "")[:16]
                hall_name = str(show_data.get("hallName") or "")
                p = show_data.get("price")
                if p is not None:
                    unit_price = float(p)
        except Exception:
            pass

    seat_name_map = await _format_seat_names(show_id, seat_ids) if show_id and seat_ids else {}
    seat_labels = [seat_name_map.get(sid, sid) for sid in seat_ids]
    seat_text = "、".join(seat_labels) if seat_labels else "待选"

    count = int(draft.get("count") or len(seat_ids) or 1)
    total = (unit_price * count) if unit_price is not None else None
    amount_line = f"\n💰 金额：¥{total:.2f}" if total is not None else ""

    summary = (
        "请确认以下购票信息：\n"
        f"🎬 影片：{draft.get('filmTitle') or draft.get('movieId')}\n"
        f"🏢 影院：{draft.get('cinemaName') or draft.get('cinemaId')}\n"
        f"🕐 场次：{show_time or draft.get('date') or ''}{f'（{hall_name}）' if hall_name else ''}\n"
        f"🎫 数量：{count} 张\n"
        f"💺 座位：{seat_text}{amount_line}\n\n"
        "回复「确认」即可下单，或告诉我需要修改的地方。"
    )
    out = _reply(state, summary)
    out["stage"] = "confirm"
    return out


async def pay_node(state: Agent4State) -> dict[str, Any]:
    """锁座 + 创建订单 + 生成支付二维码卡片。"""
    # 统一鉴权：锁座/下单前必须已登录（JWT 存在），与 lockSeats 工具内检查形成双保险
    if not get_authorization():
        return _reply(state, "请先登录后再购票。")
    draft = state.get("bookingdraft") or {}
    sid = state.get("sessionId") or ""
    show_id = draft.get("showId") or ""
    seat_ids = _norm_seat_ids(draft.get("seatIds"))

    reply = "座位锁定中，请稍候…"
    cards: list[dict[str, Any]] = []
    if show_id and seat_ids:
        lock_resp = await lock_seats.ainvoke({
            "show_id": show_id,
            "seat_ids": ",".join(seat_ids),
            "session_id": sid,
        })
        cards += build_cards([{"name": "lockSeats", "content": lock_resp}], draft)
        pay_cards = await _build_pay_card(draft, lock_resp, sid)
        cards += pay_cards
        if pay_cards:
            reply = "座位已锁定，请扫码支付："
        else:
            # 锁座失败时不暴露内部 JSON（lockId 等），只给友好提示
            parsed = _safe_parse(lock_resp)
            if isinstance(parsed, dict) and parsed.get("code") not in (0, 200, None):
                reply = "锁座失败，请稍后重试或重新选择座位。"
            else:
                reply = "锁座失败，请稍后重试。"
    else:
        reply = "购票信息不完整，无法锁座，请重新确认。"

    out = _reply(state, reply, cards)
    out["stage"] = "pay"
    return out


# ---------- 支付卡片辅助 ----------


async def _build_pay_card(draft: dict[str, Any], lock_resp: str, sid: str) -> list[dict[str, Any]]:
    """锁座成功后创建订单并拉取支付二维码，生成 pay_mock 卡片。"""
    cards: list[dict[str, Any]] = []
    if not get_authorization():
        return cards
    try:
        raw = ast.literal_eval(lock_resp)
    except Exception:
        return cards
    if not isinstance(raw, dict):
        return cards
    data = raw.get("data") if isinstance(raw.get("data"), dict) else raw
    if not isinstance(data, dict) or not data.get("lockId"):
        return cards
    lock_id = data["lockId"]
    try:
        order_resp = await post(backend_url("/orders"), json={"lockId": lock_id, "sessionId": sid}, timeout=2.0)
        order_data = {}
        if isinstance(order_resp, dict) and order_resp.get("code") in (0, 200, None):
            order_data = order_resp.get("data") or {}
        if not order_data.get("orderId"):
            return cards
        order_id = order_data["orderId"]
        pay_qr: dict[str, Any] = {}
        try:
            pay_resp = await get(backend_url(f"/orders/{order_id}/pay-qrcode"), timeout=2.0)
            if isinstance(pay_resp, dict) and pay_resp.get("code") in (0, 200, None):
                pay_qr = pay_resp.get("data") or {}
        except Exception:
            pass
        # 座位 ID → 可读座位名（如 sm_msk0v0yn:3:4 → 3排4座）
        seat_ids = _norm_seat_ids(draft.get("seatIds"))
        seat_name_map = await _format_seat_names(draft.get("showId") or "", seat_ids) if seat_ids else {}
        seat_labels = [seat_name_map.get(sid, sid) for sid in seat_ids]
        cards.append({
            "cardId": f"pay_{uuid.uuid4().hex[:8]}",
            "type": "pay_mock",
            "title": "扫码支付",
            "payload": {
                "orderId": order_id,
                "amount": order_data.get("amount") or pay_qr.get("amount") or 0,
                "payUrl": pay_qr.get("payUrl") or "",
                "movieTitle": draft.get("filmTitle") or "",
                "cinemaName": draft.get("cinemaName") or "",
                "showId": draft.get("showId") or "",
                "seatIds": seat_ids,
                "seatLabels": seat_labels,
                "count": int(draft.get("count") or len(seat_ids) or 0),
            },
            "actions": [{"actionId": "payment_done", "label": "已完成支付", "itemId": order_id}],
        })
    except Exception:
        pass
    return cards


__all__ = [
    "REQUIRED_ORDER",
    "BookingIntent",
    "BookingDraftInfo",
    "optimize_node",
    "intent_node",
    "chat_node",
    "extract_node",
    "collect_node",
    "confirm_node",
    "pay_node",
]
