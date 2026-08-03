"""Agent Service — FastAPI 入口。

包名用 ``fastapi``，避免与 PyPI 的 ``fastapi`` 冲突。
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from fastapi.api import api_router
from fastapi.config import get_settings

settings = get_settings()


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        docs_url="/docs",
        redoc_url="/redoc",
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.get("/health", tags=["system"])
    async def health() -> dict:
        return {"status": "ok", "version": settings.app_version}

    app.include_router(api_router)
    return app


app = create_app()
