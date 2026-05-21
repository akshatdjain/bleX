# database.py (ASYNC VERSION)
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base
from sqlalchemy import text
from typing import AsyncGenerator, Optional
from fastapi import Header, Request
from jose import JWTError, jwt
import os

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql+asyncpg://postgres:LYzxeJ2xrSKfzM2f@db/asset_tracking")

if DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+asyncpg://")

engine = create_async_engine(DATABASE_URL, echo=False, future=True)

AsyncSessionLocal = async_sessionmaker(engine, autoflush=False, expire_on_commit=False)

Base = declarative_base()

SECRET_KEY = os.getenv("SECRET_KEY", "blex-dev-secret-change-in-prod-please")
ALGORITHM = "HS256"


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session


async def get_tenant_db(
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-ID"),
) -> AsyncGenerator:
    """
    DB session scoped to tenant schema via X-Tenant-ID header.
    Used by Android app and Pi scripts.
    Schema: t_{tenant_id.lower()}  e.g. t_ry3ddd
    """
    schema = "public" if (not x_tenant_id or x_tenant_id == "default") else f"t_{x_tenant_id.lower()}"
    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            await session.execute(text("SET search_path TO public"))


async def get_dashboard_db(request: Request) -> AsyncGenerator:
    """
    DB session scoped to tenant schema via blex_token httpOnly cookie.
    Used by the web dashboard. Falls back to public schema if no cookie.
    """
    token = request.cookies.get("blex_token")
    schema = "public"
    if token:
        try:
            payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
            tenant_id = payload.get("tenant_id", "")
            if tenant_id:
                schema = f"t_{tenant_id.lower()}"
        except JWTError:
            pass
    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            await session.execute(text("SET search_path TO public"))
