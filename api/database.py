# database.py (ASYNC VERSION)
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base
from sqlalchemy import text
from typing import AsyncGenerator, Optional
from fastapi import Header, Request
from jose import JWTError, jwt
import os

DATABASE_URL = os.getenv("DATABASE_URL")
if not DATABASE_URL:
    raise RuntimeError("DATABASE_URL environment variable is not set")

if DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+asyncpg://")

engine = create_async_engine(
    DATABASE_URL,
    echo=False,
    future=True,
    pool_size=30,
    max_overflow=20,
    pool_timeout=30,
    pool_recycle=1800,
    pool_pre_ping=True,
)

AsyncSessionLocal = async_sessionmaker(engine, autoflush=False, expire_on_commit=False)

Base = declarative_base()

SECRET_KEY = os.getenv("SECRET_KEY")
if not SECRET_KEY:
    raise RuntimeError("SECRET_KEY environment variable is not set")
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


async def get_smart_db(
    request: Request,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-ID"),
) -> AsyncGenerator:
    """
    Smart tenant resolver: uses X-Tenant-ID header if present, falls back to JWT cookie.
    Use this on endpoints that need to serve both Android/Pi (header) and web dashboard (cookie).
    Raises 401 if neither is valid.
    """
    from fastapi import HTTPException
    schema = None

    # Priority 1: explicit header (Android app, Pi scripts)
    if x_tenant_id and x_tenant_id != "default":
        schema = f"t_{x_tenant_id.lower()}"
    else:
        # Priority 2: JWT cookie (web dashboard)
        token = request.cookies.get("blex_token")
        if token:
            try:
                payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
                tenant_id = payload.get("tenant_id", "")
                if tenant_id:
                    schema = f"t_{tenant_id.lower()}"
            except JWTError:
                pass

    if not schema:
        raise HTTPException(status_code=401, detail="Not authenticated — provide X-Tenant-ID header or login cookie")

    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            await session.execute(text("SET search_path TO public"))


async def get_dashboard_db(request: Request) -> AsyncGenerator:
    """
    DB session scoped to tenant schema via blex_token httpOnly cookie.
    Raises 401 if no valid cookie — never falls back to public schema.
    """
    from fastapi import HTTPException
    token = request.cookies.get("blex_token")
    if not token:
        raise HTTPException(status_code=401, detail="Not authenticated")
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        tenant_id = payload.get("tenant_id", "")
        if not tenant_id:
            raise HTTPException(status_code=401, detail="Invalid token")
        schema = f"t_{tenant_id.lower()}"
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid token")
    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            await session.execute(text("SET search_path TO public"))
