from typing import Any


def ok(data: Any) -> dict:
    return {"code": 200, "message": "success", "data": data}


def err(message: str, code: int = 400) -> dict:
    return {"code": code, "message": message, "data": None}
