"""agent4 配置：环境变量 + 项目根 .env 加载。"""
import os
from pathlib import Path
from typing import Optional

from pydantic_settings import BaseSettings, SettingsConfigDict

# 项目根目录（agent4 为子包，上溯两级到项目根）
_BASE_DIR = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    """应用配置（OpenAI 兼容 DeepSeek / 票务中台 / 短期记忆）。"""

    model_config = SettingsConfigDict(
        env_file=str(_BASE_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # LLM 配置（OpenAI 兼容，对齐根 .env 的 OPENAI_API_KEY / OPENAI_MODEL）
    openai_api_key: str = ""
    openai_base_url: str = "https://api.deepseek.com"
    openai_model: str = "deepseek-chat"
    llm_temperature: float = 0.7
    llm_max_tokens: int = 4000
    llm_timeout_s: float = 60.0

    # 票务中台（Java 后端）——Tools 拼接相对路径
    backend_base_url: str = "http://127.0.0.1:8080/api/v1"
    http_timeout_s: float = 15.0

    # 中台 JWT 签名密钥（与后端 application.yml 的 jwt.secret 保持一致），
    # 用于本地解析/校验前端 Authorization 头中的 userId；未配置则无法验签（视为匿名）
    jwt_secret: str = ""

    # LangGraph 短期记忆（PostgresSaver）；空则禁用
    postgres_uri: str = ""

    # Redis 配置（可选）
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0
    redis_password: Optional[str] = None
    use_redis_cache: bool = False

    # Agent 配置
    max_iterations: int = 15
    max_execution_time: float = 60.0
    memory_window: int = 20  # 保留最近 N 条消息

    @property
    def redis_uri(self) -> str:
        """Redis 连接 URI。"""
        if self.redis_password:
            return f"redis://:{self.redis_password}@{self.redis_host}:{self.redis_port}/{self.redis_db}"
        return f"redis://{self.redis_host}:{self.redis_port}/{self.redis_db}"


_settings: Optional[Settings] = None


def _fix_env_placeholder() -> None:
    """若环境变量中的 OPENAI_API_KEY 是占位符，则用 .env 文件中的真实值覆盖。"""
    env_key = os.environ.get("OPENAI_API_KEY", "")
    is_placeholder = (
        env_key.startswith("sk-your")
        or "your-api-key" in env_key
        or "your_api_key" in env_key
    )
    if not is_placeholder:
        return
    dotenv_file = _BASE_DIR / ".env"
    if not dotenv_file.exists():
        return
    for line in dotenv_file.read_text().splitlines():
        if line.startswith("OPENAI_API_KEY="):
            real = line.split("=", 1)[1].strip().strip("'\"").strip()
            if real:
                os.environ["OPENAI_API_KEY"] = real
            return


def get_settings() -> Settings:
    """获取全局配置实例（懒加载）。"""
    global _settings
    if _settings is None:
        _fix_env_placeholder()
        _settings = Settings()
    return _settings
