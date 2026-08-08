"""用户端购票 Agent 流程（基于 BookingDraft 的多轮状态图）。

图结构（多轮对话，每轮 END，靠 checkpoint 持久化 bookingdraft）：

    每轮: message → [意图识别] ─┬─ chitchat → chat → END
                               │
                               ├─ buy_ticket/browse → main_agent ─┬─ 完整 → confirm → END
                               │                                   └─ 缺字段 → END（追问缺失）
                               │
                               ├─ modify → modify_node → END（清空字段，追问新值）
                               │
                               └─ confirm/pay ─┬─ 完整 → order → END
                                               └─ 缺字段 → END（提示缺失）

多轮示例：
    第1轮:"想看哪吒2" → main_agent 提取 movieId → 缺影院 → END("选哪家影院？")
    第2轮:"湘潭万达"  → main_agent 提取 cinemaId → 缺场次 → END("哪天？")
    第3轮:"明天下午"  → main_agent 提取 showId → 完整 → confirm → END("确认？")
    第4轮:"确认"      → order → END(出票)

核心设计：
- 每轮图执行到 END 结束，不做 intra-turn 循环
- bookingdraft 在 GraphState 中由 checkpointer 跨轮持久化
- 新一轮 ainvoke 自动加载上一轮的 bookingdraft，增量合并
"""
from __future__ import annotations

import re
from typing import Any, Literal

from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel

from agent.langgraph.checkpoint import get_checkpointer
from agent.langgraph.state import GraphState
from agent.llm import get_chat_model
from agent.subagent import get_subagents

# ---------- BookingDraft 完备度 ----------

# BookingDraft 字段：系分 §4.3 定义
DRAFT_FIELDS = [
    "movieId", "cinemaId", "showId", "count", "seatIds",
]
DRAFT_LABELS = {
    "movieId": "影片",
    "cinemaId": "影院",
    "showId": "场次",
    "count": "票数",
    "seatIds": "座位",
}


def get_missing_fields(draft: dict[str, Any]) -> list[str]:
    """返回草稿中缺失的字段名列表。"""
    missing = []
    for field in DRAFT_FIELDS:
        val = draft.get(field)
        if val is None or val == "" or val == []:
            missing.append(field)
    return missing


def is_draft_complete(draft: dict[str, Any]) -> bool:
    """判断草稿是否完备（影片+影院+场次+票数+座位）。"""
    return len(get_missing_fields(draft)) == 0


def format_draft_for_user(draft: dict[str, Any]) -> str:
    """把 BookingDraft 格式化为用户可读的确认文本。"""
    lines = ["请确认您的购票信息："]
    label_map = {
        "movieId": "影片",
        "cinemaId": "影院",
        "showId": "场次",
        "count": "票数",
        "seatIds": "座位",
        "lockId": "锁座凭证",
        "expireAt": "有效期至",
    }
    for key, label in label_map.items():
        val = draft.get(key)
        if val is not None and val != "" and val != []:
            if key == "seatIds" and isinstance(val, list):
                val = "、".join(str(s) for s in val)
            lines.append(f"  {label}：{val}")
    return "\n".join(lines)


# ---------- 意图识别 ----------

BookingIntent = Literal["buy_ticket", "browse", "chitchat", "modify", "confirm", "pay"]

_INTENT_PROMPT = """你是购票助手的意图分类器。根据用户消息和当前 BookingDraft 状态判断意图：
- buy_ticket: 用户表达购票/订票/看电影意愿，或在已有草稿的基础上补充购票信息。
  只要用户在说和"看电影、买票、选座、时间、影院、人数"相关的具体事情，无论是否出现"电影"二字，都归为此类。
  影院品牌词（万达/CGV/金逸/横店/星美/大地/保利/博纳/中影/华谊/UME 等）即使不带"影院"后缀，也算影院名。
  示例："买电影票"、"想看xx"、"xx电影"、"和女朋友明天下午看流浪地球3"、"周六晚上去看哪吒2"、"万达影城两张票"、"订明天下午的票"、"流浪地球3还有吗"、"帮我选个座"、"两个人看"
  **当草稿非空时（已选影片/影院等）**，用户补充时间、影院名、人数、座位偏好等都是 buy_ticket：
  示例（草稿已有影片时）："明天下午湘潭万达"→buy_ticket、"两张"→buy_ticket、"A5 A6"→buy_ticket、"晚上7点"→buy_ticket、"靠走道"→buy_ticket
- browse: 用户想浏览/查询电影或影院（如"最近有什么电影"、"附近影院"、"推荐几部好片"、"有什么热映"）
- modify: 用户想修改已填写的购票信息（如"换一部"、"改时间"、"不要这个"、"换成"、"换影院"、"改明天"）
- confirm: 用户确认当前草稿（如"可以"、"就这样"、"确认"、"下单"、"购买"、"对的"、"没错"）
- pay: 用户已确认，想支付（如"付款"、"支付"、"买票"、"去付钱"、"买单"）
- chitchat: 闲聊、问候、其他不相关的话（如"你好"、"你是谁"、"今天天气好"）。
  **信息性追问也归为此类**：用户就上文回复追问「这些电影都有影院上映吗」「有没有对应的排片」「这是什么意思」等，不是要购票，而是想获取信息，应判为 chitchat 交由 chat agent 用工具回答。

判断优先级：
1. 如果消息中提到了具体的影片名（如流浪地球、哪吒、复仇者等）、影院名（包括万达/CGV/金逸等品牌词，可不带"影院"后缀）、时间（明天/下午/周六等）、人数、座位相关词，优先判为 buy_ticket 而非 chitchat。
2. **当草稿非空时**，用户补充任何购票相关信息（时间/地点/人数/座位）都判为 buy_ticket，而非 chitchat。只有与购票完全无关的话题才判为 chitchat。
3. **如果用户在追问上文信息**（含"这/那"+疑问语气、问"有没有对应影院上映"、"是什么意思"），即使消息中出现"电影""影院"等词，也判为 chitchat 而非 buy_ticket。

考虑当前 BookingDraft 状态：{draft_summary}
只返回 IntentDecision，不要多余解释。"""


class IntentDecision(BaseModel):
    intent: BookingIntent
    reason: str = ""


def _intent_regex_fallback(message: str, draft: dict[str, Any]) -> BookingIntent:
    """无 LLM 时的确定性 regex 意图降级。"""
    text = message.lower()
    # 确认类
    if re.search(r"确认|就这样|可以|下单|购买|买|付|支付|ok|yes|好的|对|没错", text, re.I):
        if is_draft_complete(draft) or draft.get("lockId"):
            return "confirm"
    # 修改类
    if re.search(r"换|改|不要|换成|修改|重选|cancel|取消|换个|换掉", text, re.I):
        return "modify"
    # 支付类（在购票前判断，避免被 buy_ticket 抢先）
    if re.search(r"付|支付|付款|交钱|付钱|买单|结账", text, re.I):
        return "pay"
    # 常见影院品牌词（即使不带"影院"后缀也识别为影院名）
    cinema_brands = (
        r"万达|大地|CGV|金逸|横店|星美|UME|保利|嘉华|博纳|"
        r"幸福蓝海|中影|华谊|奥斯卡|卢米埃|SFC|百老汇|耀莱"
    )
    # 信息性追问：用户在就上文追问，不是要购票，交由 chat_agent 用工具回答
    # 如「这五个电影都有影院上映吗」「这是什么意思」「有没有排片」
    # 新增：「有什么影院」「现在有什么影院可选」等询问影院列表的疑问句
    is_info_question = (
        # 含指示代词 + 疑问语气
        (re.search(r"(这|那)(些|个|几)?(\d+|[一二三四五六七八九十])?(部|个|电影|影片|片)", text)
         and re.search(r"吗|有没有|是否|什么意思|啥意思", text))
        # 直接问有没有对应影院/排片/场次上映
        or bool(re.search(r"(有没有|是否有|都有|是有).{0,6}(影院|电影院|排片|场次|上映)", text))
        # 问「...是什么意思」
        or bool(re.search(r"什么意思|是怎么回事|啥意思", text))
        # 问「有什么影院」「现在有什么影院」「哪些影院」等（询问影院列表，而非指定影院名）
        or bool(re.search(r"(有什么|有哪些|现在有什么|什么).{0,8}(影院|电影院).{0,6}(可选|可以|能选|选择|推荐|附近)?", text))
        or bool(re.search(r"^(现在|哪些|什么).{0,8}影院", text))
    )
    if is_info_question:
        return "chitchat"
    # 购票/浏览：包含购票关键词、影院/时间/票数暗示、或「看+片名」模式
    buy_ticket_pattern = (
        r"电影|影片|想看|热映|推荐|影院|影城|附近|场次|排片|"
        r"买票|订票|订座|购票|选座|票|座位|"
        r"看.*?(球|侠|战|记|传|2|3|之|大|小|神|鬼|爱|恨|情|谜|密)"  # 看+片名常见后缀
    )
    time_pattern = r"(今天|明天|后天|大后天|周一|周二|周三|周四|周五|周六|周日|上午|下午|晚上|中午|早上|明晚|今晚)"
    count_pattern = r"(一张|两张|三张|四张|五张|六张|七张|八张|九张|十张|一个人|两个人|三个人|四个人|几人|几个人|一人|两人|三人)"
    seat_pattern = r"(座位|排|座|靠走道|连座|中间|前排|后排|[A-Z]\d)"

    if re.search(buy_ticket_pattern, text, re.I):
        return "buy_ticket"
    # 同时出现「时间 + 看/去/到」也判定为购票（如"明天下午去看"）
    if re.search(time_pattern, text, re.I) and re.search(r"看|去看|去影院|到影院", text, re.I):
        return "buy_ticket"
    # 影院品牌词出现 → buy_ticket（如"湘潭万达"、"CGV"）
    if re.search(cinema_brands, text, re.I):
        return "buy_ticket"
    # draft 非空（已有购票上下文）时，用户补充时间/人数/座位 → 继续填 draft
    draft_has_context = any(
        draft.get(k) for k in ("movieId", "filmTitle", "cinemaId", "showId", "count")
    )
    if draft_has_context:
        if (
            re.search(time_pattern, text, re.I)
            or re.search(count_pattern, text, re.I)
            or re.search(seat_pattern, text, re.I)
        ):
            return "buy_ticket"
    return "chitchat"


async def _recognize_intent(message: str, draft: dict[str, Any]) -> BookingIntent:
    """LLM 意图识别；无 API Key 或异常时降级为 regex。"""
    llm = get_chat_model()
    if llm is None:
        return _intent_regex_fallback(message, draft)

    draft_summary = ""
    if draft:
        parts = []
        for k, v in draft.items():
            if v is not None and v != "" and v != [] and k not in ("sessionId", "userId", "version"):
                parts.append(f"{DRAFT_LABELS.get(k, k)}={v}")
        draft_summary = "当前草稿：" + ", ".join(parts) if parts else "空"

    try:
        structured = llm.with_structured_output(IntentDecision)
        decision = await structured.ainvoke(
            _INTENT_PROMPT.format(draft_summary=draft_summary)
            + f"\n用户消息: {message}"
        )
        return decision.intent
    except Exception:
        return _intent_regex_fallback(message, draft)


# ---------- BookingDraft 读写 ----------

async def _load_draft(session_id: str | None) -> dict[str, Any]:
    """从后端 BookingDraft 接口加载当前草稿；失败返回空 dict。"""
    from agent.http import backend_url, get
    if not session_id:
        return {}
    try:
        payload = await get(backend_url(f"/booking-drafts/{session_id}"), timeout=1.0)
        if isinstance(payload, dict) and payload.get("code") in (0, 200, None):
            data = payload.get("data")
            if isinstance(data, dict):
                return data
    except Exception:
        pass
    return {}


async def _save_draft(session_id: str | None, draft: dict[str, Any]) -> dict[str, Any]:
    """把草稿回写到后端；失败返回原草稿。"""
    from agent.http import backend_url, post
    if not session_id:
        return draft
    try:
        payload = await post(
            backend_url(f"/booking-drafts/{session_id}/merge"),
            json={"draft": draft},
            timeout=1.0,
        )
        if isinstance(payload, dict) and payload.get("code") in (0, 200, None):
            data = payload.get("data")
            if isinstance(data, dict):
                return data
    except Exception:
        pass
    return draft


# ---------- 节点 ----------

async def intent_node(state: GraphState) -> dict[str, Any]:
    """意图识别：判断购票/浏览/修改/确认/支付/闲聊。"""
    draft = state.get("bookingdraft") or {}
    intent = await _recognize_intent(state.get("message") or "", draft)
    return {"intent": intent, "events": [f"intent:{intent}"]}


class DraftExtraction(BaseModel):
    """从用户自然语言消息中提取的购票意图字段（名称/时间，非系统ID）。"""
    film_title: str | None = None        # "哪吒2"、"流浪地球3"
    cinema_name: str | None = None       # "湘潭万达"、"万达影城"
    date: str | None = None              # "明天"、"2026-08-07"、"后天"
    time_window: str | None = None       # "下午"、"晚上"、"morning"、"afternoon"
    count: int | None = None             # 2
    prefer_row: str | None = None        # "middle"、"前排"、"后排"
    prefer_side: str | None = None       # "center"、"靠走道"
    together: bool | None = None         # 是否连座


_EXTRACT_PROMPT = """从用户消息中提取购票相关的自然语言信息。
- film_title: 用户提到的电影名（如"哪吒2"、"流浪地球"）
- cinema_name: 用户提到的影院名（如"湘潭万达"、"万达影城"）
- date: 用户提到的日期（如"明天"、"后天"、"2026-08-07"），统一转为 YYYY-MM-DD
- time_window: 用户提到的时间段（morning/afternoon/evening/night）
- count: 票数（如"两张"→2，"一个人"→1）
- prefer_row: 座位排偏好（front/middle/back）
- prefer_side: 座位侧偏好（center/aisle/edge）
- together: 是否需要连座（true/false）

用户没提到的字段留空（null）。不要编造信息。
今天是 {today}。"""


def _resolve_date(date_str: str | None) -> str | None:
    """把"明天"/"后天"等相对日期转为 YYYY-MM-DD。"""
    if not date_str:
        return None
    from datetime import datetime, timedelta
    today = datetime.now().strftime("%Y-%m-%d")
    mapping = {"今天": today, "明天": (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%d"),
               "后天": (datetime.now() + timedelta(days=2)).strftime("%Y-%m-%d")}
    return mapping.get(date_str, date_str if re.match(r"\d{4}-\d{2}-\d{2}", date_str) else None)


async def _resolve_movie_id(film_title: str) -> tuple[str | None, str | None, int | None]:
    """通过 searchMovies 工具把片名解析为 movieId。
    返回 (movieId, filmTitle, items_count)。
    - items_count > 0: 匹配成功
    - items_count == 0: 接口返回空，明确是「暂无上映」
    - items_count = None: 接口异常或格式错误
    """
    from agent.tools.movie_tools import search_movies
    try:
        result = await search_movies.ainvoke({"query": film_title, "status": "hot_showing", "page": 1, "size": 5})
        import ast
        data = ast.literal_eval(result) if isinstance(result, str) else result
        # Tool 经 safe_api_call 已解包，直接返回 {items, page, size, total}
        items = data.get("items") if isinstance(data, dict) else None
        if isinstance(items, list):
            if items:
                first = items[0]
                if isinstance(first, dict):
                    return first.get("movieId"), first.get("title"), len(items)
            return None, None, 0  # 空列表 = 明确无结果
    except Exception:
        pass
    return None, None, None  # 接口异常 = 未知


async def _resolve_cinema_id(cinema_name: str, lat: float | None = None, lng: float | None = None) -> tuple[str | None, str | None, int | None]:
    """通过 searchCinemas 工具把影院名解析为 cinemaId。
    返回 (cinemaId, cinemaName, items_count)。
    - items_count == 0 明确是「未找到影院」
    """
    from agent.tools.cinema_tools import fetch_cinemas
    try:
        # 用户未提供有效坐标时，用一个兜底坐标（湘潭市中心，radius 大一些做全国模糊匹配）
        has_valid_loc = (lat is not None and lng is not None and lat > -90 and lng > -180)
        use_lat = lat if has_valid_loc else 27.83
        use_lng = lng if has_valid_loc else 112.93
        data = await fetch_cinemas(
            lat=use_lat,
            lng=use_lng,
            radius_meters=5000 if has_valid_loc else 500000,  # 500km 全国兜底
            sort="distance" if has_valid_loc else "lowest_price",
            page=1,
            size=5,
        )
        items = data.get("items") if isinstance(data, dict) else None
        if isinstance(items, list):
            if items:
                # 如果用户传了明确的 cinema_name，对结果做关键词模糊匹配（优先返回名称最匹配的）
                if cinema_name:
                    matched = [it for it in items if isinstance(it, dict)
                               and cinema_name in str(it.get("name", ""))]
                    if matched:
                        first = matched[0]
                        return first.get("cinemaId"), first.get("name"), len(matched)
                # 否则按顺序返回第一个
                first = items[0]
                if isinstance(first, dict):
                    return first.get("cinemaId"), first.get("name"), len(items)
            return None, None, 0
    except Exception:
        pass
    return None, None, None


async def _resolve_show_id(cinema_id: str, movie_id: str, date: str, time_window: str | None = None) -> tuple[str | None, int, int]:
    """通过 listShows 工具查找场次。有 time_window 时按时间段过滤。
    返回 (showId, total_items, filtered_items)：
    - total_items == 0 → 该影院该电影当天完全无排片
    - total_items > 0 但 filtered_items == 0 → 该时段无合适场次（有其他时段的）
    - showId != None → 匹配成功
    """
    from agent.tools.show_tools import list_shows
    try:
        result = await list_shows.ainvoke({
            "cinema_id": cinema_id,
            "movie_id": movie_id,
            "date": date,
        })
        import ast
        data = ast.literal_eval(result) if isinstance(result, str) else result
        items = data.get("items") if isinstance(data, dict) else None
        if not isinstance(items, list):
            return None, 0, 0
        total = len(items)
        if total == 0:
            return None, 0, 0
        if time_window:
            time_ranges = {
                "morning": (6, 12),
                "afternoon": (12, 18),
                "evening": (18, 22),
                "night": (22, 30),
            }
            lo, hi = time_ranges.get(time_window.lower(), (0, 30))
            matched = []
            for item in items:
                if not isinstance(item, dict):
                    continue
                start = item.get("startTime", "")
                m = re.search(r"T(\d{2}):", str(start))
                if m:
                    hour = int(m.group(1))
                    if lo <= hour < hi:
                        matched.append(item)
            if matched:
                show_id = matched[0].get("showId")
                return show_id, total, len(matched)
            return None, total, 0  # 有排片但该时段无
        first = items[0]
        show_id = first.get("showId") if isinstance(first, dict) else None
        return show_id, total, total
    except Exception:
        return None, 0, 0


async def _fetch_shows(cinema_id: str, movie_id: str, date: str) -> list[dict[str, Any]]:
    """查某影院某影片某日的场次列表，返回原始 items（供前端场次卡片）。"""
    from agent.tools.show_tools import list_shows
    try:
        result = await list_shows.ainvoke({
            "cinema_id": cinema_id,
            "movie_id": movie_id,
            "date": date,
        })
        import ast
        data = ast.literal_eval(result) if isinstance(result, str) else result
        items = data.get("items") if isinstance(data, dict) else None
        return [it for it in items if isinstance(it, dict)] if isinstance(items, list) else []
    except Exception:
        return []


def _extract_fields_regex(message: str) -> dict[str, Any]:
    """无 LLM 时，用正则从用户消息中提取购票字段。"""
    from datetime import datetime, timedelta
    result: dict[str, Any] = {}
    text = message

    # 日期
    today = datetime.now()
    date_map = {
        "今天": today.strftime("%Y-%m-%d"),
        "今日": today.strftime("%Y-%m-%d"),
        "明天": (today + timedelta(days=1)).strftime("%Y-%m-%d"),
        "明日": (today + timedelta(days=1)).strftime("%Y-%m-%d"),
        "后天": (today + timedelta(days=2)).strftime("%Y-%m-%d"),
        "大后天": (today + timedelta(days=3)).strftime("%Y-%m-%d"),
    }
    # 星期映射：2026-08-06 是周四。以今天为基准求最近一个匹配的星期几。
    weekday_cn = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
    # today.weekday(): 周一=0 … 周日=6
    today_wd = today.weekday()
    for i, wd_name in enumerate(weekday_cn):
        if wd_name in text and wd_name not in date_map:
            diff = (i - today_wd) % 7
            if diff == 0:
                diff = 7  # 下一个该星期几（含今天的话改成 diff=0 也行）
            target = today + timedelta(days=diff)
            date_map[wd_name] = target.strftime("%Y-%m-%d")
    for kw, iso in date_map.items():
        if kw in text:
            result["date"] = iso
            break
    if "date" not in result:
        m = re.search(r"(\d{4})[-/](\d{1,2})[-/](\d{1,2})", text)
        if m:
            result["date"] = f"{m.group(1)}-{int(m.group(2)):02d}-{int(m.group(3)):02d}"

    # 时间段
    time_map = {
        "早上": "morning", "早晨": "morning", "上午": "morning",
        "中午": "afternoon", "下午": "afternoon", "下午场": "afternoon",
        "晚上": "evening", "晚间": "evening", "夜场": "night",
        "今晚": "evening", "明晚": "evening", "半夜": "night",
    }
    for kw, val in time_map.items():
        if kw in text:
            result["timeWindow"] = val
            break
    if "timeWindow" not in result:
        m = re.search(r"(早上|上午|下午|晚上|中午|半夜|明晚|今晚)", text)
        if m:
            kw = m.group(1)
            if kw in ("早上", "上午"):
                result["timeWindow"] = "morning"
            elif kw in ("下午", "中午"):
                result["timeWindow"] = "afternoon"
            elif kw in ("晚上", "今晚", "明晚"):
                result["timeWindow"] = "evening"
            elif kw == "半夜":
                result["timeWindow"] = "night"

    # 票数
    cn_num_map = {"一": 1, "两": 2, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}
    m = re.search(r"([一二两三四五六七八九十\d])\s*(张|个|人|位|票)", text)
    if m:
        num = m.group(1)
        if num in cn_num_map:
            result["count"] = cn_num_map[num]
        elif num.isdigit():
            result["count"] = int(num)
    if "count" not in result:
        if re.search(r"和.*?(朋友|女朋友|男朋友|老婆|老公|家人|对象|同学|同事|闺蜜|兄弟|姐妹)", text):
            result["count"] = 2
            result["together"] = True
        elif re.search(r"一个人|自己|一人|单人|1个人", text):
            result["count"] = 1
        elif re.search(r"一家三口|三口人|三个人|3个人", text):
            result["count"] = 3

    # 影院名（匹配「XX万达/万达影城/XX影院/XX影城/电影院」后缀）
    # 先把日期/时间/看等词从上下文去掉，避免污染前缀
    cinema_text = re.sub(r"(今天|明天|后天|大后天|上午|下午|晚上|中午|早上|半夜|明晚|今晚|和.*?(朋友|女朋友|男朋友|老婆|老公|家人|对象|同学|同事|闺蜜|兄弟|姐妹)|看|去看|想看|订|买票|购票|两张|三张|一张|两张票|去|到|前往|我们|我和.*?|我)", "", text)
    # 排除疑问句：避免把「有什么影院」「现在有什么影院」误提取为影院名
    is_cinema_question = bool(re.search(r"(什么|哪些|有什么|有哪些|现在有).{0,8}(影院|电影院)", cinema_text))
    is_cinema_question = is_cinema_question or bool(re.search(r"(可选|可以|能选|选择|推荐)$", cinema_text))
    if not is_cinema_question:
        m = re.search(r"([\u4e00-\u9fa5A-Za-z0-9]+?(?:万达影城|万达|影城|影院|电影院|IMAX|CINITY))", cinema_text)
        name = None
        if m:
            name = m.group(1).strip()
            # 去掉残留的日期/方位词
            name = re.sub(r"^(今天|明天|后天|大后天|明晚|今晚|去|到|在|从|和|什么|哪些|有什么|现在有)", "", name)
            # 再次检查：如果提取后的名称还包含疑问词，不要提取
        if name and len(name) >= 2 and not re.search(r"(什么|哪些|有什么|可选|能选)", name):
            result["cinemaName"] = name

    # 片名
    film_text = re.sub(r"(今天|明天|后天|大后天|上午|下午|晚上|中午|早上|半夜|明晚|今晚)", "", text)
    m = re.search(r"(?:看|去看|想看|订|买)([\u4e00-\u9fa5A-Za-z0-9]{2,20}?)(?:的|电影|影片|票|呀|吧|呢|了|啊|。|，|,|$)", film_text)
    if m:
        title = m.group(1).strip()
        # 排除纯数量词/非片名
        invalid_titles = {"一张", "两张", "三张", "四张", "五张", "六张", "七张", "八张", "九张", "十张",
                          "一个", "两个", "三个", "四个", "五个", "一人", "两人", "三人", "几人", "几个人",
                          "座位", "座", "好位置", "中间", "靠走道", "连座",
                          # 类型词不是片名
                          "喜剧", "动作", "科幻", "爱情", "恐怖", "动画", "悬疑", "犯罪", "战争",
                          "纪录", "冒险", "奇幻", "剧情", "历史", "武侠", "古装", "伦理", "惊悚"}
        if title and len(title) >= 2 and title not in invalid_titles:
            result["filmTitle"] = title
    if "filmTitle" not in result:
        m = re.search(r"([\u4e00-\u9fa5]{2,}(?:\d|之|大|小)[\u4e00-\u9fa5A-Za-z0-9]*)", text)
        if m:
            title = m.group(1).strip()
            if title and len(title) >= 2:
                if not re.search(r"(明天|后天|今天|下午|上午|晚上|电影|影片|影院|影城|朋友|女朋友|男朋友|一个|两个|三个|张票|座位)", title):
                    result["filmTitle"] = title

    return result


async def _handle_browse_request(message: str, draft: dict[str, Any] | None = None) -> dict[str, Any] | None:
    """检测用户是否在请求按类型推荐/浏览电影。

    返回 ``{"reply": str, "movies": list[dict]}`` 或 None（继续走购票流程）。
    ``movies`` 是后端原始 item 列表，用于生成前端动态卡片。

    方案2优化：如果draft已有movieId，说明用户已选定影片，跳过电影浏览。
    """
    # 方案2优化：如果用户已选定影片，不要再推荐电影
    if draft and draft.get("movieId"):
        return None

    text = message.lower()

    # 类型关键词 → 后端 genre 值（后端 genres 字段是中文）
    genre_map = {
        "喜剧": "喜剧", "搞笑": "喜剧",
        "动作": "动作",
        "科幻": "科幻",
        "爱情": "爱情", "言情": "爱情",
        "恐怖": "恐怖", "惊悚": "惊悚",
        "动画": "动画", "动漫": "动画",
        "悬疑": "悬疑",
        "犯罪": "犯罪",
        "战争": "战争",
        "纪录": "纪录", "纪录片": "纪录",
        "冒险": "冒险",
        "奇幻": "奇幻",
        "剧情": "剧情",
        "历史": "历史",
        "武侠": "武侠",
        "古装": "古装",
    }

    # 推荐意图关键词
    recommend_words = r"推荐|有什么|哪些|好看|建议|有没有|介绍|看看|排行|热门|榜单"

    # 检测：消息中包含类型词 + 推荐词，且没有具体的片名/影院/日期
    matched_genre = None
    for genre_kw, genre_val in genre_map.items():
        if genre_kw in text:
            matched_genre = genre_val
            break

    if matched_genre and re.search(recommend_words, text):
        # 从数据库搜索该类型的电影
        from agent.tools.movie_tools import search_movies
        try:
            result = await search_movies.ainvoke({
                "genre": matched_genre,
                "status": "hot_showing",
                "page": 1,
                "size": 5,
            })
            import ast
            data = ast.literal_eval(result) if isinstance(result, str) else result
            items = data.get("items") if isinstance(data, dict) else None
            # 只保留有场次的影片：既要上映也要有影院和场次（nextShowDate 非空即有排片）
            if isinstance(items, list):
                items = [it for it in items if isinstance(it, dict) and it.get("nextShowDate")]

            if isinstance(items, list) and items:
                lines = [f"为您找到以下**{matched_genre}**类热映影片：\n"]
                lines.append("| # | 片名 | 评分 | 时长 | 类型 |")
                lines.append("|---|------|------|------|------|")
                for i, item in enumerate(items, 1):
                    if not isinstance(item, dict):
                        continue
                    title = item.get("title", "未知")
                    rating = item.get("rating", "")
                    duration = item.get("durationMin", "")
                    genres = item.get("genres", [])
                    genres_str = "/".join(genres) if genres else matched_genre
                    rating_str = f"⭐{rating}" if rating else "-"
                    dur_str = f"{duration}分钟" if duration else "-"
                    lines.append(f"| {i} | {title} | {rating_str} | {dur_str} | {genres_str} |")
                lines.append(f"\n以上{len(items)}部{matched_genre}电影正在热映，您对哪部感兴趣？")
                return {"reply": "\n".join(lines), "movies": items}
            else:
                # 也查一下即将上映
                result2 = await search_movies.ainvoke({
                    "genre": matched_genre,
                    "status": "coming_soon",
                    "page": 1,
                    "size": 5,
                })
                data2 = ast.literal_eval(result2) if isinstance(result2, str) else result2
                items2 = data2.get("items") if isinstance(data2, dict) else None
                if isinstance(items2, list) and items2:
                    lines = [f"当前{matched_genre}类暂无热映影片，以下{matched_genre}类即将上映：\n"]
                    for i, item in enumerate(items2, 1):
                        if not isinstance(item, dict):
                            continue
                        title = item.get("title", "未知")
                        release = item.get("releaseDate", "")
                        lines.append(f"{i}. 《{title}》 上映日期：{release}")
                    return {"reply": "\n".join(lines), "movies": []}
                return {"reply": f"暂未查询到{matched_genre}类型的影片排片信息，请稍后再试或换一种类型。", "movies": []}
        except Exception:
            return {"reply": f"暂未查询到{matched_genre}类型的影片信息（服务可能未就绪），请稍后再试。", "movies": []}

    # ---------- 通用浏览：无类型词但问"有什么电影/热映/上映"等 ----------
    # 触发：含浏览句式（有什么电影/哪些影片/热映/上映等），且无具体购票信号
    general_browse_pattern = (
        r"(有什么|有哪些|最近|现在|目前).{0,4}(电影|影片|片子|片)"
        r"|热映|上映|在映|有什么看|有哪些看"
    )
    # 排除购票信号：具体片名（看+后缀）、影院品牌词、时间+看、人数词
    has_purchase_signal = (
        re.search(r"看.{0,6}?(球|侠|战|记|传|2|3|之|大|小|神|鬼|爱|恨|情|谜|密)", text)
        or re.search(r"万达|大地|CGV|金逸|横店|星美|UME|保利|嘉华|博纳|幸福蓝海|中影|华谊|奥斯卡|卢米埃", text)
        or (
            re.search(r"(今天|明天|后天|大后天|周一|周二|周三|周四|周五|周六|周日|上午|下午|晚上|中午|早上|明晚|今晚)", text)
            and re.search(r"看|去买|去订|订票", text)
        )
        or re.search(r"(一张|两张|三张|四张|五张|一个人|两个人|三个人|几个人|几人|一人|两人|三人)", text)
    )
    # 排除追问/上下文相关提问：用户在就刚才的回复追问，不是在浏览
    is_followup_question = (
        # 含指示代词（这/那/这些/那些/这几个/那几个 + 数量 + 电影/部/影片）
        bool(re.search(r"(这|那)(些|个|几)?(\d+|[一二三四五六七八九十])?(部|个|电影|影片|片)", text))
        # 询问「有没有对应的影院/电影院/排片/场次上映」
        or bool(re.search(r"(有没有|是否有|都有|是有).{0,6}(影院|电影院|排片|场次|上映)", text))
        # 询问「...是什么意思」「...什么意思」
        or bool(re.search(r"什么意思|是怎么回事|啥意思", text))
        # 以「吗」结尾的疑问句（在追问，不是在浏览）
        or (text.rstrip().endswith("吗") and len(text) > 6)
    )
    if re.search(general_browse_pattern, text, re.I) and not has_purchase_signal and not is_followup_question:
        from agent.tools.movie_tools import search_movies
        try:
            result = await search_movies.ainvoke({
                "status": "hot_showing",
                "page": 1,
                "size": 8,
            })
            import ast
            data = ast.literal_eval(result) if isinstance(result, str) else result
            items = data.get("items") if isinstance(data, dict) else None
            # 只保留有场次的影片：既要上映也要有影院和场次（nextShowDate 非空即有排片）
            if isinstance(items, list):
                items = [it for it in items if isinstance(it, dict) and it.get("nextShowDate")]
            if isinstance(items, list) and items:
                lines = ["🎬 **当前热映影片**\n"]
                lines.append("| # | 片名 | 评分 | 时长 | 类型 |")
                lines.append("|---|------|------|------|------|")
                for i, item in enumerate(items, 1):
                    if not isinstance(item, dict):
                        continue
                    title = item.get("title", "未知")
                    rating = item.get("rating", "")
                    duration = item.get("durationMin", "")
                    genres = item.get("genres", [])
                    genres_str = "/".join(genres) if genres else "-"
                    rating_str = f"⭐{rating}" if rating else "-"
                    dur_str = f"{duration}分钟" if duration else "-"
                    lines.append(f"| {i} | {title} | {rating_str} | {dur_str} | {genres_str} |")
                lines.append(f"\n以上{len(items)}部影片正在热映，您对哪部感兴趣？")
                return {"reply": "\n".join(lines), "movies": items}
            return {"reply": "暂无热映影片信息，请稍后再试。", "movies": []}
        except Exception:
            return {"reply": "暂未查询到热映影片信息（服务可能未就绪），请稍后再试。", "movies": []}

    return None


async def main_agent_node(state: GraphState) -> dict[str, Any]:
    """总控agent：提取自然语言 → 解析名称为ID → 合并 BookingDraft。

    支持一句话包含多个字段（如"明天下午湘潭万达看哪吒2"同时提取影院+日期+时间+片名）。
    """
    message = state.get("message") or ""
    draft = state.get("bookingdraft") or {}
    history = state.get("history") or []
    lat = state.get("latitude")
    lng = state.get("longitude")

    # ---------- 第0步：检测是否为「按类型推荐/浏览」请求 ----------
    # 方案2优化：传入draft，避免已选影片后再次推荐电影
    browse_result = await _handle_browse_request(message, draft)
    if browse_result is not None:
        browse_reply = browse_result["reply"]
        browse_movies = browse_result.get("movies", [])
        # 生成前端动态卡片（movie_list）
        cards: list[dict[str, Any]] = []
        if browse_movies:
            import uuid as _uuid
            cards.append({
                "cardId": f"browse_{_uuid.uuid4().hex[:8]}",
                "type": "movie_list",
                "title": "当前热映影片",
                "payload": {"movies": browse_movies},
                "actions": [
                    {"actionId": "select", "label": "选这部", "itemId": m.get("movieId", "")}
                    for m in browse_movies
                    if isinstance(m, dict) and m.get("movieId")
                ],
            })
        return {
            "bookingdraft": draft,
            "reply": browse_reply,
            "cards": cards,
            "missing_fields": get_missing_fields(draft),
            "draft_complete": is_draft_complete(draft),
            "events": ["browse_done"],
            "history": [
                {"role": "user", "content": message},
                {"role": "assistant", "content": browse_reply},
            ],
        }

    # ---------- 第1步：LLM 提取自然语言字段，失败则 regex 兜底 ----------
    extracted: dict[str, Any] = {}
    llm = get_chat_model()
    llm_ok = False
    if llm is not None:
        try:
            from datetime import datetime
            today = datetime.now().strftime("%Y-%m-%d")
            context = ""
            if history:
                recent = history[-4:]
                context = "\n最近对话:\n" + "\n".join(
                    f"{h['role']}: {h['content']}" for h in recent
                )
            structured = llm.with_structured_output(DraftExtraction)
            result = await structured.ainvoke(
                _EXTRACT_PROMPT.format(today=today) + context + f"\n用户消息: {message}"
            )
            if result.film_title:
                extracted["filmTitle"] = result.film_title
            if result.cinema_name:
                extracted["cinemaName"] = result.cinema_name
            if result.date:
                resolved = _resolve_date(result.date)
                if resolved:
                    extracted["date"] = resolved
            if result.time_window:
                extracted["timeWindow"] = result.time_window
            if result.count:
                extracted["count"] = result.count
            if result.prefer_row:
                extracted["preferRow"] = result.prefer_row
            if result.prefer_side:
                extracted["preferSide"] = result.prefer_side
            if result.together is not None:
                extracted["together"] = result.together
            llm_ok = True
        except Exception:
            llm_ok = False
    if not llm_ok:
        extracted = _extract_fields_regex(message)

    # ---------- 第2步：合并到草稿 ----------
    merged = dict(draft)
    for k, v in extracted.items():
        if v is not None and v != "" and v != []:
            merged[k] = v

    # 若已选座位但未填票数，按座位数推断
    if merged.get("seatIds") and not merged.get("count"):
        seat_list = merged["seatIds"]
        if isinstance(seat_list, str):
            seat_list = [s.strip() for s in seat_list.split(",") if s.strip()]
        if seat_list:
            merged["count"] = len(seat_list)

    # ---------- 第3步：名称→ID 解析（并行），同步收集解析失败的提示 ----------
    import asyncio

    warnings: list[str] = []
    cards: list[dict[str, Any]] = []
    task_keys: list[str] = []
    tasks: list = []
    if merged.get("filmTitle") and not merged.get("movieId"):
        task_keys.append("movie")
        tasks.append(_resolve_movie_id(merged["filmTitle"]))
    if merged.get("cinemaName") and not merged.get("cinemaId"):
        task_keys.append("cinema")
        tasks.append(_resolve_cinema_id(merged["cinemaName"], lat, lng))

    if tasks:
        results = await asyncio.gather(*tasks, return_exceptions=True)
        for key, res in zip(task_keys, results):
            if isinstance(res, Exception):
                continue
            if key == "movie":
                movie_id, film_title, count = res
                if movie_id:
                    merged["movieId"] = movie_id
                    if film_title:
                        merged["filmTitle"] = film_title
                elif count == 0:
                    # 明确搜到空 = 该片暂无热映
                    warnings.append(f"🎬 《{merged.get('filmTitle')}》暂无热映排片，可以试试搜索其他影片或查看即将上映。")
                else:
                    # count is None = 接口异常/未运行，也告知用户
                    warnings.append(f"🎬 暂未查询到《{merged.get('filmTitle')}》的排片信息（服务可能未就绪），请稍后再试或手动选择影片。")
            elif key == "cinema":
                cinema_id, cinema_name, count = res
                if cinema_id:
                    merged["cinemaId"] = cinema_id
                    if cinema_name:
                        merged["cinemaName"] = cinema_name
                elif count == 0:
                    warnings.append(f"🏢 未找到影院「{merged.get('cinemaName')}」，请检查名称是否正确，或尝试其他关键词（如城市+万达）。")
                else:
                    warnings.append(f"🏢 暂未查询到影院「{merged.get('cinemaName')}」的信息（服务可能未就绪），请稍后再试或手动选择影院。")

    # ---------- 第4步：有 movieId+cinemaId+date 但缺 showId → 查场次并生成场次选择卡片 ----------
    show_warn_movie = merged.get("filmTitle") or merged.get("movieId")
    show_warn_cinema = merged.get("cinemaName") or merged.get("cinemaId")
    if (merged.get("movieId") and merged.get("cinemaId")
            and merged.get("date") and not merged.get("showId")):
        shows = await _fetch_shows(merged["cinemaId"], merged["movieId"], merged["date"])
        if not shows:
            # 全天无排片
            date_cn = merged.get("date", "")
            warnings.append(
                f"📅 《{show_warn_movie}》在 {show_warn_cinema} {date_cn} 暂无排片，"
                f"请换个日期或影院再试试。"
            )
        else:
            # 生成场次选择动态卡片，让用户点选
            import uuid as _uuid3
            cards.append({
                "cardId": f"shows_{_uuid3.uuid4().hex[:8]}",
                "type": "show_list",
                "title": f"《{show_warn_movie}》在 {show_warn_cinema} 的场次",
                "payload": {"shows": shows},
                "actions": [
                    {
                        "actionId": "select",
                        "label": "选这场",
                        "itemId": s.get("showId", ""),
                        "draftPatch": {"showId": s.get("showId", "")},
                    }
                    for s in shows
                    if isinstance(s, dict) and s.get("showId")
                ],
            })
            # 仅一场时自动填入；多场时让用户从卡片选择
            if len(shows) == 1 and isinstance(shows[0], dict):
                merged["showId"] = shows[0].get("showId")

    # ---------- 第5步：回写后端 ----------
    session_id = state.get("sessionId")
    if session_id:
        merged = await _save_draft(session_id, merged)

    # ---------- 第6步：判断完备度 ----------
    missing = get_missing_fields(merged)
    complete = len(missing) == 0

    # ---------- 第7步：生成回复（warnings 优先于记录/缺失提示） ----------
    reply_parts: list[str] = []

    # 1) 先列「无上映/未找到/无排片」的警告
    if warnings:
        reply_parts.extend(warnings)

    # 2) 已记录信息
    recorded = []
    for k, label in [("filmTitle", "影片"), ("cinemaName", "影院"), ("date", "日期"),
                      ("timeWindow", "时间段"), ("count", "票数")]:
        v = merged.get(k)
        if v:
            if k == "timeWindow" and isinstance(v, str):
                tl_map = {"morning": "上午", "afternoon": "下午", "evening": "晚上", "night": "半夜"}
                v = tl_map.get(v.lower(), v)
            recorded.append(f"{label}={v}")
    recorded_str = ""
    if recorded:
        recorded_str = f"已记录：{', '.join(recorded)}。"

    if complete:
        reply = format_draft_for_user(merged) + "\n\n以上信息是否正确？确认后我将为您下单。"
        if warnings:
            reply = "\n\n".join(warnings) + "\n\n" + reply
    elif missing:
        missing_labels = [DRAFT_LABELS.get(f, f) for f in missing]
        # 过滤掉因为 warnings 导致的伪缺失
        # 例：用户说了流浪地球3但影片未上映，已经 warning 了，就不要再问「请选择影片」
        still_need: list[str] = []
        for label, field in zip(missing_labels, missing):
            already_warned = False
            if field == "movieId" and any("暂无热映" in w for w in warnings):
                already_warned = True
            if field == "cinemaId" and any("未找到影院" in w for w in warnings):
                already_warned = True
            if field == "showId" and any("暂无排片" in w or "暂无场次" in w for w in warnings):
                already_warned = True
            if not already_warned:
                still_need.append(label)

        if still_need:
            question = f"还需要您补充：{', '.join(still_need)}。请问您想选择哪部{'/'.join(still_need)}？"
        else:
            question = "请根据上方提示重新选择合适的信息。"

        if warnings:
            reply_parts.append(recorded_str + question)
            reply = "\n\n".join(reply_parts)
        else:
            reply = f"{recorded_str}{question}"
    else:
        # 兜底：没有 missing 也没有 complete 时，至少展示 warnings
        if warnings:
            reply = "\n\n".join(warnings) + (f"\n\n{recorded_str}" if recorded_str else "")
        else:
            reply = recorded_str or "已收到您的信息，请继续补充下一步。"

    events = [f"main_agent:complete={complete}"]
    if warnings:
        events.append(f"warnings:{len(warnings)}")

    # ---------- 第8步：智能卡片推荐 ----------
    # 根据购票流程状态，主动推荐下一步需要的卡片
    # （cards 已在第3步初始化；场次/影院卡片可能已加入）

    # 方案1优化：已有影片但缺影院 → 主动推荐影院列表
    if merged.get("movieId") and not merged.get("cinemaId") and lat is not None and lng is not None:
        try:
            from agent.tools.cinema_tools import fetch_cinemas
            cinema_data = await fetch_cinemas(
                movie_id=merged["movieId"],
                lat=lat, lng=lng,
                radius_meters=30000,
                sort="distance",
                page=1, size=8,
            )
            cinema_items = cinema_data.get("items") if isinstance(cinema_data, dict) else None
            if isinstance(cinema_items, list) and cinema_items:
                import uuid as _uuid2
                cinema_name_display = merged.get("filmTitle") or "该影片"
                cards.append({
                    "cardId": f"cinemas_{_uuid2.uuid4().hex[:8]}",
                    "type": "cinema_list",
                    "title": f"《{cinema_name_display}》附近影院",
                    "payload": {"cinemas": cinema_items},
                    "actions": [
                        {
                            "actionId": "select",
                            "label": "选这家",
                            "itemId": c.get("cinemaId", ""),
                            "draftPatch": {"cinemaId": c.get("cinemaId"), "cinemaName": c.get("name")},
                        }
                        for c in cinema_items
                        if isinstance(c, dict) and c.get("cinemaId")
                    ],
                })
        except Exception:
            pass  # 搜索影院失败不阻塞主流程

    # 方案2：用户已通过卡片选座（seatIds 已写入 draft）但尚未锁座 → 自动锁座
    if merged.get("showId") and merged.get("seatIds") and not merged.get("lockId"):
        try:
            from agent.http import backend_url, post
            seat_list = merged["seatIds"]
            if isinstance(seat_list, str):
                seat_list = [s.strip() for s in seat_list.split(",") if s.strip()]
            idem_key = f"idem_lock_{state.get('sessionId') or ''}_{merged['showId']}_{'_'.join(seat_list)}"
            lock_payload = await post(
                backend_url("/locks"),
                json={
                    "showId": merged["showId"],
                    "seatIds": seat_list,
                    "ttlSeconds": 900,
                    "sessionId": state.get("sessionId") or "",
                },
                headers={"Idempotency-Key": idem_key},
                timeout=1.5,
            )
            if isinstance(lock_payload, dict) and lock_payload.get("code") in (0, 200, None):
                lock_info = lock_payload.get("data") or {}
                if lock_info.get("lockId"):
                    merged["lockId"] = lock_info["lockId"]
                    if lock_info.get("expireAt"):
                        merged["expireAt"] = lock_info["expireAt"]
        except Exception:
            pass  # 锁座失败不阻塞，交由用户后续处理

    # 方案3：已有场次但缺座位 → 生成可点击座位卡片（缩小版）
    if merged.get("showId") and not merged.get("seatIds"):
        try:
            from agent.http import backend_url, get, post
            sm_payload = await get(backend_url(f"/shows/{merged['showId']}/seat-map"), timeout=3.0)
            if isinstance(sm_payload, dict) and sm_payload.get("code") in (0, 200, None):
                seat_map = sm_payload.get("data")
                if isinstance(seat_map, dict) and seat_map.get("seats"):
                    import uuid as _uuid4
                    # 附带推荐方案（可选）
                    reco_data = None
                    try:
                        reco_payload = await post(
                            backend_url("/reco/seats"),
                            json={
                                "showId": merged["showId"],
                                "count": merged.get("count") or 2,
                                "preferRow": merged.get("preferRow") or "middle",
                                "preferSide": merged.get("preferSide") or "center",
                                "together": merged.get("together", True),
                            },
                            timeout=3.0,
                        )
                        if isinstance(reco_payload, dict) and reco_payload.get("code") in (0, 200, None):
                            reco_data = reco_payload.get("data")
                    except Exception:
                        pass
                    cards.append({
                        "cardId": f"seats_{_uuid4.uuid4().hex[:8]}",
                        "type": "seat_plans",
                        "title": f"《{merged.get('filmTitle') or '该影片'}》选座",
                        "payload": {
                            "showId": merged["showId"],
                            "count": merged.get("count") or 2,
                            "seatMap": seat_map,
                            "plans": (reco_data or {}).get("plans") if isinstance(reco_data, dict) else [],
                            "compromise": (reco_data or {}).get("compromise") if isinstance(reco_data, dict) else None,
                        },
                        "actions": [
                            {
                                "actionId": "confirm",
                                "label": "确认选座",
                                "itemId": merged["showId"],
                                "draftPatch": {"showId": merged["showId"], "seatIds": []},
                            }
                        ],
                    })
        except Exception:
            pass  # 座位图失败不阻塞主流程

    return {
        "bookingdraft": merged,
        "reply": reply,
        "cards": cards,
        "missing_fields": missing,
        "draft_complete": complete,
        "events": events,
        "history": [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ],
    }


async def chat_node(state: GraphState) -> dict[str, Any]:
    """闲聊节点。"""
    agent = get_subagents()["chat"]
    message = state.get("message") or ""
    reply = await agent.run(
        message,
        history=state.get("history"),
        latitude=state.get("latitude"),
        longitude=state.get("longitude"),
    )
    return {
        "reply": reply,
        "events": ["chat_done"],
        "history": [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ],
    }


async def confirm_node(state: GraphState) -> dict[str, Any]:
    """确认展示节点：展示当前草稿给用户确认/修改。"""
    draft = state.get("bookingdraft") or {}
    reply = format_draft_for_user(draft)

    # 如果已有 lockId，提示用户可以支付
    if draft.get("lockId"):
        expire_at = draft.get("expireAt", "")
        reply += f"\n\n🔒 您的座位已锁定（凭证 {draft['lockId']}），有效期至 {expire_at}。请在有效期内完成支付。"
        reply += "\n\n请回复「确认支付」继续，或回复「修改」调整信息。"
    else:
        reply += "\n\n请确认以上信息，回复「确认」继续，或告诉我需要修改的地方。"

    message = state.get("message") or ""
    return {
        "reply": reply,
        "events": ["confirm_shown"],
        "history": [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ],
    }


async def modify_node(state: GraphState) -> dict[str, Any]:
    """修改节点：用户想修改草稿，返回可编辑格式并追问缺失。"""
    draft = state.get("bookingdraft") or {}
    message = state.get("message") or ""

    # 解析用户想修改什么
    llm = get_chat_model()
    reply = ""
    if llm is not None:
        try:
            from pydantic import BaseModel

            class ModifyIntent(BaseModel):
                wants_reset: bool = False
                wants_modify: list[str] = []

            structured = llm.with_structured_output(ModifyIntent)
            result = await structured.ainvoke(
                f"用户说「{message}」，结合当前草稿字段，判断用户想修改哪些内容。"
                f"当前草稿字段：movieId={draft.get('movieId')}, cinemaId={draft.get('cinemaId')}, "
                f"showId={draft.get('showId')}, count={draft.get('count')}, seatIds={draft.get('seatIds')}。"
                f"如果用户想重置/清空所有信息，wants_reset=true。"
                f"如果用户想修改某个字段，列出 wants_modify（如 ['movieId', 'showId']）。"
            )

            if result.wants_reset:
                draft = {}
                if state.get("sessionId"):
                    draft = await _save_draft(state["sessionId"], {})
                reply = "已为您重置所有信息。请重新开始购票。"
            elif result.wants_modify:
                # 清空被修改字段及其依赖
                for field in result.wants_modify:
                    draft.pop(field, None)
                    # 级联清空：改 movieId → 清 cinema/show/seats/lock/order
                    if field == "movieId":
                        for dep in ["cinemaId", "showId", "seatIds", "lockId", "orderId"]:
                            draft.pop(dep, None)
                    elif field == "cinemaId":
                        for dep in ["showId", "seatIds", "lockId", "orderId"]:
                            draft.pop(dep, None)
                    elif field == "showId":
                        for dep in ["seatIds", "lockId", "orderId"]:
                            draft.pop(dep, None)
                    elif field == "seatIds":
                        for dep in ["lockId", "orderId"]:
                            draft.pop(dep, None)

                if state.get("sessionId"):
                    draft = await _save_draft(state["sessionId"], draft)

                missing = get_missing_fields(draft)
                if missing:
                    missing_labels = [DRAFT_LABELS.get(f, f) for f in missing]
                    reply = f"已重置您要修改的信息。还需要补充：{', '.join(missing_labels)}。请告诉我您想选择的影片/影院/场次。"
                else:
                    reply = "已修改。请确认更新后的信息。"
        except Exception:
            reply = "已收到您的修改请求。请告诉我您想修改哪项信息（影片/影院/场次/座位/票数）。"

    return {
        "bookingdraft": draft,
        "reply": reply,
        "events": ["modify_done"],
        "history": [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ],
    }


async def order_node(state: GraphState) -> dict[str, Any]:
    """下单节点：调后端接口创建订单，返回支付信息。

    如果草稿不完备（缺锁座凭证），提示用户还需补充什么。
    """
    draft = state.get("bookingdraft") or {}
    message = state.get("message") or ""

    # 检查完备度
    lock_id = draft.get("lockId")
    if not lock_id:
        missing = get_missing_fields(draft)
        if missing:
            missing_labels = [DRAFT_LABELS.get(f, f) for f in missing]
            reply = f"还不能下单，还需要补充：{', '.join(missing_labels)}。请告诉我您想选择的{'/'.join(missing_labels)}。"
        else:
            reply = "请先选择座位并锁座，然后再下单。"
        return {
            "reply": reply,
            "events": ["order_failed:incomplete"],
            "history": [
                {"role": "user", "content": message},
                {"role": "assistant", "content": reply},
            ],
        }

    from agent.http import backend_url, post
    try:
        body: dict[str, Any] = {"lockId": lock_id}
        session_id = state.get("sessionId")
        if session_id:
            body["sessionId"] = session_id

        payload = await post(backend_url("/orders"), json=body, timeout=1.5)
        if isinstance(payload, dict) and payload.get("code") in (0, 200, None):
            data = payload.get("data") or {}
            order_id = data.get("orderId", "")
            ticket_code = data.get("ticketCode", "")
            amount = data.get("amount", "?")

            draft["orderId"] = order_id
            if session_id:
                draft = await _save_draft(session_id, draft)

            reply = (
                f"✅ 订单创建成功！\n"
                f"- 订单号：{order_id}\n"
                f"- 金额：¥{amount}\n"
                f"- 取票码：{ticket_code}\n\n"
                f"请前往支付页面完成付款，或扫码支付。"
            )
        else:
            msg = payload.get("message", "下单失败") if isinstance(payload, dict) else "下单失败"
            reply = f"下单失败：{msg}。请重试或重新选择座位。"
    except Exception as e:
        reply = f"下单时网络异常：{e}。请稍后重试。"

    return {
        "bookingdraft": draft,
        "reply": reply,
        "events": ["order_done"],
        "history": [
            {"role": "user", "content": message},
            {"role": "assistant", "content": reply},
        ],
    }


# ---------- 条件边 ----------

def _select_after_intent(state: GraphState) -> str:
    """意图路由：
    - chitchat → chat
    - modify → modify（直接清空字段）
    - confirm/pay → confirm_or_order（检查完备度后决定）
    - buy_ticket/browse → main_agent（提取信息）
    """
    intent = state.get("intent") or "chitchat"
    if intent == "chitchat":
        return "chat"
    if intent == "modify":
        return "modify"
    if intent in ("confirm", "pay"):
        return "confirm_or_order"
    # buy_ticket / browse
    return "main_agent"


def _select_after_main(state: GraphState) -> str:
    """总控agent 后的分支：
    - 草稿完备 → confirm（展示确认）
    - 草稿缺字段 → END（回复追问，等下一轮用户输入）
    """
    if state.get("draft_complete"):
        return "confirm"
    return "end"


def _select_confirm_or_order(state: GraphState) -> str:
    """confirm/pay 意图时检查完备度：
    - 完备 → order（用户确认下单）
    - 缺字段 → END（提示还需补充什么）
    """
    draft = state.get("bookingdraft") or {}
    if is_draft_complete(draft) or draft.get("lockId"):
        return "order"
    return "end"


# ---------- 图构建 ----------

def build_booking_graph(checkpointer: Any | None = None) -> Any:
    """编译购票 Agent 状态图。

    每轮流程（无 intra-turn 循环，多轮靠 checkpoint）：
    START → intent → (chat | main_agent | modify | confirm_or_order)
    main_agent → (confirm | END)
    confirm → END（展示草稿，等下一轮确认）
    modify → END（清空字段，等下一轮补充）
    confirm_or_order → (order | END)
    order → END
    chat → END
    """
    graph = StateGraph(GraphState)

    # 节点
    graph.add_node("intent", intent_node)
    graph.add_node("main_agent", main_agent_node)
    graph.add_node("chat", chat_node)
    graph.add_node("confirm", confirm_node)
    graph.add_node("modify", modify_node)
    graph.add_node("order", order_node)

    # 边
    graph.add_edge(START, "intent")

    # 意图分支
    graph.add_conditional_edges(
        "intent",
        _select_after_intent,
        {
            "chat": "chat",
            "main_agent": "main_agent",
            "modify": "modify",
            "confirm_or_order": "order",
        },
    )

    # main_agent 后：完备→confirm，缺字段→END
    graph.add_conditional_edges(
        "main_agent",
        _select_after_main,
        {"confirm": "confirm", "end": END},
    )

    # confirm 后：END（等下一轮用户确认/修改）
    graph.add_edge("confirm", END)

    # modify 后：END（等下一轮用户补充新值）
    graph.add_edge("modify", END)

    # confirm_or_order 路径（intent=confirm/pay 时）
    # 需要检查完备度：完备→order，缺字段→END
    # 用 order_node 内部检查（已有 lockId 判断）
    graph.add_edge("order", END)

    # chat → END
    graph.add_edge("chat", END)

    cp = checkpointer if checkpointer is not None else get_checkpointer()
    return graph.compile(checkpointer=cp)
