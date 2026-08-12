"""skills：子 Agent 的角色定义与指令（markdown）。

每个子 agent 一个 ``<name>.md``；``load_skill(name)`` 读取后作为该子 agent 的
system prompt 注入 ``create_react_agent``。集中管理角色定义，便于按需调整提示词。
"""
from __future__ import annotations

from pathlib import Path

_SKILLS_DIR = Path(__file__).resolve().parent


def load_skill(name: str) -> str:
    """读取某个子 agent 的 skill 内容（作为 system prompt）。"""
    path = _SKILLS_DIR / f"{name}.md"
    if not path.exists():
        raise FileNotFoundError(f"skill 不存在: {name}")
    return path.read_text(encoding="utf-8").strip()


def list_skills() -> list[str]:
    """列出所有可用 skill 名称。"""
    return sorted(p.stem for p in _SKILLS_DIR.glob("*.md"))


__all__ = ["load_skill", "list_skills"]
