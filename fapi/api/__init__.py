from fastapi import APIRouter

from fapi.api.chat import router as chat_router

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(chat_router)
