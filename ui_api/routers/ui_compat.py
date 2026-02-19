from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from database import get_db

router = APIRouter(prefix="/api", tags=["UI Compatibility"])


@router.get("/hello")
async def hello():
    return {"message": "Hey!"}
