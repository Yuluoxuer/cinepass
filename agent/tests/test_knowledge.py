"""知识库管理功能单测：作用域映射 / 文件名校验 / 角色解析 / JWT 验签 / 检索合并。

不依赖真实 Chroma / embedding 模型：检索合并用 monkeypatch 替换 retriever 内部依赖。
"""
from __future__ import annotations

from types import SimpleNamespace

import jwt as pyjwt
import pytest

from agent4.api.knowledge import _resolve_scope
from agent4.rag.config import (
    COLLECTION_NAME,
    collection_name,
    is_valid_knowledge_filename,
)

TEST_SECRET = "test-jwt-secret-0123456789abcdef"


class _FakeVector(list):
    """带 .tolist() 的假向量，模拟 fastembed 的返回对象。"""

    def tolist(self):
        return list(self)


@pytest.fixture(autouse=True)
def _fake_jwt_secret(monkeypatch: pytest.MonkeyPatch) -> None:
    """decode_jwt_claims 使用固定测试密钥，避免依赖 .env 中的真实配置。"""

    class _FakeSettings:
        jwt_secret = TEST_SECRET

    monkeypatch.setattr("agent4.config.get_settings", lambda: _FakeSettings())


# ---------- 作用域 → collection 名 ----------


def test_collection_name_system_default() -> None:
    assert collection_name("system") == COLLECTION_NAME
    assert collection_name("system", "c12") == COLLECTION_NAME


def test_collection_name_cinema() -> None:
    assert collection_name("cinema", "c12") == "cinema_knowledge_cinema_c12"


@pytest.mark.parametrize(
    "bad",
    ["", None, "../x", "a/b", "a\\b", "含中文", "x" * 65],
)
def test_collection_name_rejects_bad_cinema_id(bad) -> None:
    with pytest.raises(ValueError):
        collection_name("cinema", bad)


def test_collection_name_rejects_unknown_scope() -> None:
    with pytest.raises(ValueError):
        collection_name("other")


# ---------- 文件名校验 ----------


@pytest.mark.parametrize(
    ("name", "ok"),
    [
        ("faq.md", True),
        ("refund_policy.markdown", True),
        ("影院活动-2026.md", True),  # 中文文件名合法
        ("../evil.md", False),  # 路径穿越
        ("a/b.md", False),
        ("a\\b.md", False),
        ("faq.txt", False),
        ("", False),
        (None, False),
        (".md", False),
        ("..", False),
        ("a\nb.md", False),
        ("x" * 129 + ".md", False),  # 超长
    ],
)
def test_is_valid_knowledge_filename(name, ok) -> None:
    assert is_valid_knowledge_filename(name) is ok


# ---------- 角色作用域解析 ----------


def test_resolve_scope_admin() -> None:
    assert _resolve_scope({"role": "admin"}) == ("system", None)


def test_resolve_scope_staff_with_cinema() -> None:
    assert _resolve_scope({"role": "staff", "cinemaId": "c12"}) == ("cinema", "c12")


def test_resolve_scope_staff_without_cinema() -> None:
    assert _resolve_scope({"role": "staff"}) is None


@pytest.mark.parametrize(
    "claims",
    [{"role": "user"}, {}, {"roles": ["user"]}, {"role": "guest"}],
)
def test_resolve_scope_denies_non_staff_admin(claims) -> None:
    assert _resolve_scope(claims) is None


# ---------- JWT 验签 ----------


def test_decode_jwt_claims_ok() -> None:
    from agent4.api import contract

    token = pyjwt.encode(
        {"sub": "u1", "role": "staff", "cinemaId": "c12"},
        TEST_SECRET,
        algorithm="HS256",
    )
    claims = contract.decode_jwt_claims(f"Bearer {token}")
    assert claims is not None
    assert claims["sub"] == "u1"
    assert claims["role"] == "staff"
    assert claims["cinemaId"] == "c12"


def test_decode_jwt_claims_rejects_wrong_secret() -> None:
    from agent4.api import contract

    token = pyjwt.encode(
        {"sub": "u1", "role": "admin"}, "another-secret-0123456789-abcdefgh", algorithm="HS256"
    )
    assert contract.decode_jwt_claims(f"Bearer {token}") is None


def test_decode_jwt_claims_none_on_missing_or_garbage() -> None:
    from agent4.api import contract

    assert contract.decode_jwt_claims(None) is None
    assert contract.decode_jwt_claims("not-a-token") is None
    assert contract.decode_jwt_claims("Bearer not.a.jwt") is None


# ---------- 检索合并（mock 底层，不依赖真实 Chroma） ----------


def test_retrieve_merges_system_and_cinema(monkeypatch) -> None:
    pytest.importorskip("chromadb")
    from agent4.rag import retriever

    def fake_count(name: str) -> int:
        return 1 if name in (COLLECTION_NAME, "cinema_knowledge_cinema_c12") else 0

    def fake_collection(name: str):
        class FakeCollection:
            def query(self, query_embeddings=None, n_results=None):
                if name == COLLECTION_NAME:
                    return {
                        "documents": [["系统知识：购票流程说明"]],
                        "metadatas": [[{"source": "guide.md", "section": "购票流程"}]],
                    }
                return {
                    "documents": [["影院活动：本周会员日"]],
                    "metadatas": [[{"source": "activity.md", "section": "活动"}]],
                }

        return FakeCollection()

    monkeypatch.setattr(retriever, "_collection_count", fake_count)
    monkeypatch.setattr(retriever, "_get_collection", fake_collection)
    monkeypatch.setattr(
        retriever,
        "_get_embedder",
        lambda: SimpleNamespace(embed=lambda texts: [_FakeVector([0.1, 0.2])]),
    )

    out = retriever.retrieve("购票流程", cinema_id="c12")
    assert "【知识库检索结果】" in out
    assert "[系统] guide.md" in out
    assert "[影院] activity.md" in out
    assert "系统知识：购票流程说明" in out
    assert "影院活动：本周会员日" in out


def test_retrieve_empty_knowledge(monkeypatch) -> None:
    pytest.importorskip("chromadb")
    from agent4.rag import retriever

    monkeypatch.setattr(retriever, "_collection_count", lambda name: 0)
    out = retriever.retrieve("购票", cinema_id="c12")
    assert "知识库当前为空" in out
