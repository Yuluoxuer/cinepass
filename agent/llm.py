"""LLM 工厂：无密钥时返回 None，SubAgent 走本地回退。"""
from __future__ import annotations

from functools import lru_cache
from typing import Any

from agent.settings import get_agent_settings


@lru_cache
def get_chat_model() -> Any | None:
    settings = get_agent_settings()
    if not settings.openai_api_key:
        return None
    try:
        from langchain.chat_models import init_chat_model
    except ImportError:
        return None

    # DeepSeek 等 OpenAI 兼容接口：强制 openai provider + base_url
    # 否则 deepseek-chat 会推断为 deepseek，需额外安装 langchain-deepseek
    kwargs: dict[str, Any] = {
        "model": settings.openai_model,
        "model_provider": "openai",
        "api_key": settings.openai_api_key,
        "timeout": settings.llm_timeout_s,
        "streaming": True,
    }
    if settings.openai_base_url:
        kwargs["base_url"] = settings.openai_base_url
    return init_chat_model(**kwargs)
