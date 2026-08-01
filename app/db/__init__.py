"""SQLAlchemy 引擎与会话（Agent 自有库，与中台 PostgreSQL 分离）。

调用方：app.main lifespan init_db；process_turn / message_repo 取 Session。
Glob：app/db 下原无同名文件。库表字段见 models（ISO8601 UTC）。
用户指令：「现在去掉X-Internal-Api-Key相关代码，我现在agent部分单独使用一个数据库并使用fastapi（sqlachemy）进行操作，请修改后端，agent，系分」
"""
from __future__ import annotations

from collections.abc import Generator

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import get_settings


class Base(DeclarativeBase):
    pass


def _build_engine():
    settings = get_settings()
    url = settings.database_url
    connect_args = {}
    if url.startswith("sqlite"):
        connect_args["check_same_thread"] = False
    return create_engine(url, pool_pre_ping=True, connect_args=connect_args)


engine = _build_engine()
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


def init_db() -> None:
    """创建表（开发骨架；生产可改 Alembic）。"""
    from app.db import models  # noqa: F401

    Base.metadata.create_all(bind=engine)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()
