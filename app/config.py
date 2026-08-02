from functools import lru_cache

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "ticket-agent"
    app_version: str = "0.1.0"
    debug: bool = False

    # 中台 ticket-api（库存/订单/Draft；对话消息不经中台）
    ticket_api_base_url: str = "http://localhost:8080"
    ticket_api_timeout_s: float = 8.0

    # Agent 自有库（SQLAlchemy）；会话/消息落 PostgreSQL，与中台库分离
    database_url: str = "postgresql+psycopg2://agent:pass@8.134.24.207:5432/ticket_agent"

    # LLM（可选；MVP 规则 NLP 可不配）
    openai_api_key: str = ""
    openai_base_url: str = ""
    openai_model: str = "gpt-4o-mini"
    llm_timeout_s: float = 1.5

    # RAG：向量库由远端 Chroma 提供；chunk 参数供日后接入检索用
    chunk_size: int = 1000
    chunk_overlap: int = 200

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


@lru_cache
def get_settings() -> Settings:
    return Settings()
