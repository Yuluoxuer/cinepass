"""Agent Service — FastAPI 入口。

HTTP 包名必须是 ``fapi``，不能叫 ``fastapi``（会遮蔽 PyPI）。
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from agent.langgraph.checkpoint import checkpoint_lifespan
from agent.langgraph.runner import reset_graph_cache
from agent4.api.memory import ensure_messages_table
from agent4.graph import reset_graph_cache as reset_agent4_graph_cache
from agent4.graph.checkpoint import checkpoint_lifespan as agent4_checkpoint_lifespan
from fapi.api import api_router
from fapi.config import get_settings

settings = get_settings()


@asynccontextmanager
async def lifespan(_app: FastAPI):
    async with checkpoint_lifespan(), agent4_checkpoint_lifespan():
        reset_graph_cache()
        reset_agent4_graph_cache()
        from fapi.api.booking import reset_booking_graph_cache
        reset_booking_graph_cache()
        await ensure_messages_table()
        from agent4.tools.AgentTools.booking_draft import ensure_table as ensure_booking_draft_table
        await ensure_booking_draft_table()
        yield
    reset_graph_cache()
    reset_agent4_graph_cache()
    reset_booking_graph_cache()
    from agent2.agent import close_agent
    await close_agent()


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.app_name,
        version=settings.app_version,
        docs_url="/docs",
        redoc_url="/redoc",
        lifespan=lifespan,
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
        from agent.langgraph.checkpoint import get_checkpointer

        return {
            "status": "ok",
            "version": settings.app_version,
            "checkpoint": "postgres" if get_checkpointer() is not None else "off",
        }

    app.include_router(api_router)
    return app


app = create_app()
