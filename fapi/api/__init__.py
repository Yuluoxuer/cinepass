from fastapi import APIRouter

from agent4.api import router as agent4_router
from fapi.api.agent import router as agent_router
from fapi.api.booking import router as booking_router
from fapi.api.chat import router as chat_router

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(agent_router)
api_router.include_router(agent4_router)
api_router.include_router(chat_router)
api_router.include_router(booking_router)
