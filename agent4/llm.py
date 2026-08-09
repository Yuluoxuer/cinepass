"""agent4 LLM 构建（OpenAI 兼容 DeepSeek）。懒加载，避免导入时 key 未就绪导致启动失败。"""
from __future__ import annotations

from langchain.chat_models import init_chat_model

from agent4.config import get_settings

_llm = None


def _build_llm():
    """构建 LLM 实例（懒加载）。DeepSeek 为 OpenAI 兼容接口，强制 openai provider + base_url。"""
    settings = get_settings()
    if not settings.openai_api_key:
        raise RuntimeError("未配置 OPENAI_API_KEY，请在项目根 .env 中配置后重试。")
    kwargs = {
        "model": settings.openai_model,
        "model_provider": "openai",
        "api_key": settings.openai_api_key,
        "temperature": settings.llm_temperature,
        "timeout": settings.llm_timeout_s,
        "streaming": True,
    }
    if settings.openai_base_url:
        kwargs["base_url"] = settings.openai_base_url
    return init_chat_model(**kwargs)


def get_llm():
    """获取全局 LLM 实例（懒加载并缓存）。"""
    global _llm
    if _llm is None:
        _llm = _build_llm()
    return _llm
