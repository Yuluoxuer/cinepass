"""项目配置文件"""
import os
from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

# 以配置文件所在目录为基准，确保从任意工作目录运行都能加载 .env
_BASE_DIR = Path(__file__).resolve().parent


class Settings(BaseSettings):
    """应用配置"""

    model_config = SettingsConfigDict(
        env_file=str(_BASE_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # LLM配置
    deepseek_api_key: str = ""
    deepseek_base_url: str = "https://api.deepseek.com"
    llm_temperature: float = 0.7
    llm_max_tokens: int = 4000

    # PostgreSQL配置
    postgres_host: str = "localhost"
    postgres_port: int = 5432
    postgres_db: str = "agent_db"
    postgres_user: str = "postgres"
    postgres_password: str = ""

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
    def postgres_uri(self) -> str:
        """PostgreSQL连接URI"""
        return f"postgresql://{self.postgres_user}:{self.postgres_password}@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"

    @property
    def redis_uri(self) -> str:
        """Redis连接URI"""
        if self.redis_password:
            return f"redis://:{self.redis_password}@{self.redis_host}:{self.redis_port}/{self.redis_db}"
        return f"redis://{self.redis_host}:{self.redis_port}/{self.redis_db}"


_settings: Optional[Settings] = None


def _fix_env_placeholder() -> None:
    """若环境变量中的 DEEPSEEK_API_KEY 是占位符，则用 .env 文件中的真实值覆盖。

    优先级说明：pydantic-settings 的优先级为 环境变量 > .env 文件。
    当 shell 中 export 过占位符 key（如 sk-your-api-key-here）时会误覆盖真实 key，
    这里检测到占位符时强制改用 .env 文件中的值。
    """
    env_key = os.environ.get("DEEPSEEK_API_KEY", "")
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
        if line.startswith("DEEPSEEK_API_KEY="):
            real = line.split("=", 1)[1].strip().strip("'\"").strip()
            if real:
                os.environ["DEEPSEEK_API_KEY"] = real
            return


def get_settings() -> Settings:
    """获取全局配置实例"""
    global _settings
    if _settings is None:
        _fix_env_placeholder()
        _settings = Settings()
    return _settings
