"""项目配置文件"""
import os
from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

# 以项目根目录为基准（agent3 为自包含子包，上溯一级到项目根），确保能加载根 .env
_BASE_DIR = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    """应用配置"""

    model_config = SettingsConfigDict(
        env_file=str(_BASE_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # LLM配置（OpenAI 兼容，对齐根 .env 的 OPENAI_API_KEY / OPENAI_MODEL）
    openai_api_key: str = ""
    openai_base_url: str = "https://api.deepseek.com"
    openai_model: str = "deepseek-chat"
    llm_temperature: float = 0.7
    llm_max_tokens: int = 4000
    llm_timeout_s: float = 60.0

    # PostgreSQL配置（完整连接串，对应 .env 的 POSTGRES_URI；空则禁用短期记忆）
    postgres_uri: str = ""

    # Redis配置（可选）
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0
    redis_password: Optional[str] = None
    use_redis_cache: bool = False

    # 后端API配置
    backend_base_url: str = "http://localhost:8000"

    # Agent配置
    max_iterations: int = 15
    max_execution_time: float = 60.0
    memory_window: int = 20  # 保留最近N条消息

    @property
    def redis_uri(self) -> str:
        """Redis连接URI"""
        if self.redis_password:
            return f"redis://:{self.redis_password}@{self.redis_host}:{self.redis_port}/{self.redis_db}"
        return f"redis://{self.redis_host}:{self.redis_port}/{self.redis_db}"


_settings: Optional[Settings] = None


def _fix_env_placeholder() -> None:
    """若环境变量中的 OPENAI_API_KEY 是占位符，则用 .env 文件中的真实值覆盖。

    优先级说明：pydantic-settings 的优先级为 环境变量 > .env 文件。
    当 shell 中 export 过占位符 key（如 sk-your-api-key-here）时会误覆盖真实 key，
    这里检测到占位符时强制改用 .env 文件中的值。
    """
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
    """获取全局配置实例"""
    global _settings
    if _settings is None:
        _fix_env_placeholder()
        _settings = Settings()
    return _settings
