"""草稿乐观锁 resolve_draft_merge 单测。

覆盖 docs/agent-draft-optimistic-lock.md §7 的 6 个用例：
1. 页面改影院（cv==sv）→ 合并生效，锁座保留
2. Agent 已锁座后页面旧快照（cv<sv）→ 服务端优先，锁座不回退
3. 页面本地改片未同步（cv>sv）→ 客户端新影片生效，且级联清空旧影院/场次/座位
4. 点卡明确选择 → 无论 version 都优先（端点层 patch 覆盖，resolve 不干扰）
5. 无 version 的旧请求 → 走固定优先级兼容逻辑
6. 依赖字段级联：改 movieId 清 cinemaId/showId/seatIds，改 count 清 seatIds
"""
from __future__ import annotations

from fapi.api._draft_merge import resolve_draft_merge


def _server(**overrides) -> dict:
    base = {
        "movieId": "M1",
        "cinemaId": "C1",
        "showId": "S1",
        "seatIds": ["A1"],
        "version": 3,
    }
    base.update(overrides)
    return base


def test_1_page_changes_cinema_sync_keeps_lock() -> None:
    """用例1：cv == sv 且决策未变 → 合并生效，锁座保留，版本不变。"""
    server = _server(lockId="L1", expireAt="2026-08-09T12:00:00+08:00")
    client = {"movieId": "M1", "cinemaId": "C1", "showId": "S1", "version": 3}
    merged, v = resolve_draft_merge(server, client, 3)
    assert merged["lockId"] == "L1"
    assert v == 3
    assert merged["version"] == 3


def test_1b_page_changes_cinema_really() -> None:
    """用例1b：cv == sv 且页面真改影院 → 决策生效，§4.3 级联清锁/座位。"""
    server = _server(lockId="L1")
    client = {"movieId": "M1", "cinemaId": "C2", "version": 3}
    merged, _ = resolve_draft_merge(server, client, 3)
    assert merged["cinemaId"] == "C2"
    assert merged["lockId"] is None
    assert merged["seatIds"] == []


def test_2_server_newer_keeps_lock() -> None:
    """用例2：cv < sv（Agent 已锁座，页面旧快照）→ 服务端优先，锁座不回退。"""
    server = _server(lockId="L1", expireAt="2026-08-09T12:00:00+08:00", version=5)
    client = {"movieId": "M1", "cinemaId": "C1", "showId": "S1", "seatIds": [], "version": 3}
    merged, v = resolve_draft_merge(server, client, 3)
    assert merged["movieId"] == "M1"
    assert merged["lockId"] == "L1"
    assert v == 5


def test_3_client_newer_new_movie_cascades() -> None:
    """用例3：cv > sv（页面本地改片未同步）→ 客户端新影片生效，级联清空，version=max+1。"""
    server = _server()
    client = {"movieId": "M2", "filmTitle": "新片", "version": 5}
    merged, v = resolve_draft_merge(server, client, 5)
    assert merged["movieId"] == "M2"
    assert merged["cinemaId"] is None
    assert merged["showId"] is None
    assert merged["seatIds"] == []
    assert v == 6


def test_3b_client_newer_provides_dependents() -> None:
    """用例3b：cv > sv 且客户端提供新影院/场次 → 保留客户端新依赖，仅清旧座位。"""
    server = _server()
    client = {"movieId": "M2", "cinemaId": "C9", "showId": "S9", "version": 5}
    merged, _ = resolve_draft_merge(server, client, 5)
    assert merged["movieId"] == "M2"
    assert merged["cinemaId"] == "C9"
    assert merged["showId"] == "S9"
    assert merged["seatIds"] == []


def test_4_card_action_overrides_after_resolve() -> None:
    """用例4：点卡明确选择优先——端点层在 resolve 之后 patch 覆盖，resolve 不干扰成果。"""
    server = _server(lockId="L1")
    client = {"movieId": "M1", "cinemaId": "C1", "version": 4}
    merged, _ = resolve_draft_merge(server, client, 4)
    patched = {**merged, "showId": "S9"}  # 模拟点卡 draftPatch 覆盖
    assert patched["showId"] == "S9"
    assert patched["lockId"] == "L1"


def test_5_legacy_no_version() -> None:
    """用例5：无 version 的旧请求 → 固定优先级兼容，锁座成果保留，版本不变。"""
    server = _server(lockId="L1")
    client = {"movieId": "M1", "cinemaId": "C1", "preferRow": "front"}
    merged, v = resolve_draft_merge(server, client, None)
    assert merged["preferRow"] == "front"
    assert merged["lockId"] == "L1"
    assert v == 3


def test_5b_legacy_changes_movie_cascades() -> None:
    """用例5b：旧客户端改片 → §4.3 级联清锁（旧锁失效）。"""
    server = _server(lockId="L1")
    client = {"movieId": "M9", "filmTitle": "老快照改片"}
    merged, _ = resolve_draft_merge(server, client, None)
    assert merged["movieId"] == "M9"
    assert merged["lockId"] is None


def test_6_count_cascades_seats() -> None:
    """用例6：改 count 清 seatIds。"""
    server = _server(count=2, seatIds=["A1", "A2"])
    client = {"movieId": "M1", "cinemaId": "C1", "showId": "S1", "count": 4, "version": 3}
    merged, _ = resolve_draft_merge(server, client, 3)
    assert merged["count"] == 4
    assert merged["seatIds"] == []


def test_6_movie_cascades_all() -> None:
    """用例6：改 movieId 清 cinemaId/showId/seatIds/lockId。"""
    server = _server(lockId="L1")
    client = {"movieId": "M5", "version": 3}
    merged, _ = resolve_draft_merge(server, client, 3)
    assert merged["cinemaId"] is None
    assert merged["showId"] is None
    assert merged["seatIds"] == []
    assert merged["lockId"] is None
