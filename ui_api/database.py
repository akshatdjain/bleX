from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base
from sqlalchemy import text
from fastapi import Request
from jose import JWTError, jwt
import os
from typing import AsyncGenerator


'''
DB_NAME=asset_tracking
DB_USER=postgres
DB_PASS=LYzxeJ2xrSKfzM2f
'''
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql+asyncpg://postgres:LYzxeJ2xrSKfzM2f@db:5432/asset_tracking")

# Ensure it uses asyncpg
if DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+asyncpg://")

print(f"UI_API DATABASE_URL: {DATABASE_URL}")


engine = create_async_engine(
    DATABASE_URL,
    echo=False,
    pool_pre_ping=True,
)

AsyncSessionLocal = async_sessionmaker(
    engine,
    autoflush=False,
    expire_on_commit=False,
)

Base = declarative_base()

async def get_db():
    async with AsyncSessionLocal() as session:
        yield session


# ── Cookie-based tenant routing ──────────────────────────────────

SECRET_KEY = os.getenv("SECRET_KEY", "blex-dev-secret-change-in-prod-please")
ALGORITHM = "HS256"

async def get_tenant_db(request: Request) -> AsyncGenerator:
    """
    DB session scoped to the tenant's schema via blex_token cookie.
    
    Reads the blex_token httpOnly cookie from the request.
    Decodes the JWT to extract tenant_id.
    Sets search_path to t_{tenant_id.lower()}, public.
    Falls back to public schema if no cookie or invalid token.
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
            # Invalid or expired token, fall back to public
            pass
    
    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            await session.execute(text("SET search_path TO public"))
