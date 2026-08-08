from langchain.chat_models import init_chat_model

from .config import get_settings


def _build_llm():
    """构建 LLM 实例。通过 get_settings() 读取 key，以规避 shell 中占位符环境变量覆盖问题。"""
    settings = get_settings()
    return init_chat_model(
        "deepseek-chat",
        model_provider="deepseek",
        api_key=settings.deepseek_api_key,
        temperature=0.7,
    )


llm = _build_llm()


def get_llm():
    """获取全局 LLM 实例。"""
    return llm
