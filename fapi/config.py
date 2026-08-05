"""应用配置。"""
from __future__ import annotations

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "agent-service"
    app_version: str = "0.2.0"
    debug: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings()
