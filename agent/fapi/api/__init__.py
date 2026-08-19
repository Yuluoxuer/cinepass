from fastapi import APIRouter

from agent4.api import router as agent4_router
from agent4.api.knowledge import router as knowledge_router

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(agent4_router)
api_router.include_router(knowledge_router)
