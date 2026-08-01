from fastapi import APIRouter

from app.api.v1.agent import router as agent_router

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(agent_router)
