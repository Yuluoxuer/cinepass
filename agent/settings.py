"""Agent 侧配置（LLM）；与 FastAPI 进程配置分离，避免包循环依赖。"""
from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class AgentSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # OpenAI 兼容：DeepSeek 填自己的 key；默认指向 DeepSeek 官方 API
    openai_api_key: str = ""
    openai_base_url: str = "https://api.deepseek.com"
    openai_model: str = "deepseek-chat"
    llm_timeout_s: float = 60.0

    # SubAgent / Tools 出站 HTTP
    http_timeout_s: float = 15.0
    # 票务中台 Base URL（Tools 拼接相对路径）
    backend_base_url: str = "http://127.0.0.1:8080"

    # LangGraph 短期记忆（PostgresSaver）；空则禁用
    postgres_uri: str = ""

    # 票务中台后端地址
    backend_base_url: str = "http://localhost:8080/api/v1"


@lru_cache
def get_agent_settings() -> AgentSettings:
    return AgentSettings()
