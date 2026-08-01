from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings

BASE_DIR = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    app_name: str = "ticket-agent"
    app_version: str = "0.1.0"
    debug: bool = False

    # 中台 ticket-api（库存/订单/Draft；对话消息不经中台）
    ticket_api_base_url: str = "http://localhost:8080"
    ticket_api_timeout_s: float = 8.0

    # Agent 自有库（SQLAlchemy）；默认本地 SQLite，生产可改 PostgreSQL
    database_url: str = f"sqlite:///{BASE_DIR / 'storage' / 'agent.db'}"

    # LLM（可选；MVP 规则 NLP 可不配）
    openai_api_key: str = ""
    openai_base_url: str = ""
    openai_model: str = "gpt-4o-mini"
    llm_timeout_s: float = 1.5

    # RAG 本地语料 / 向量库
    rag_documents_dir: str = str(BASE_DIR / "storage" / "documents")
    rag_vectorstore_dir: str = str(BASE_DIR / "storage" / "vectorstore")
    chunk_size: int = 1000
    chunk_overlap: int = 200

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


@lru_cache
def get_settings() -> Settings:
    return Settings()
