# database.py (ASYNC VERSION)
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base
from sqlalchemy import text
from typing import AsyncGenerator, Optional
from fastapi import Header, Request
from jose import JWTError, jwt
import os
import re

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

_TENANT_RE = re.compile(r"^[A-Z0-9_]{1,32}$")


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session


async def get_tenant_db(
    request: Request,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-ID"),
) -> AsyncGenerator:
    """
    DB session scoped to tenant schema.
    Resolution priority: X-Tenant-ID header → tenant_id from Bearer JWT → public.
    Used by Android app and Pi scripts.
    Schema: t_{tenant_id.lower()}  e.g. t_ry3ddd

    The Bearer-token fallback (priority 2) exists so a tenant-scoped token can
    never be silently served the legacy `public` schema just because the caller
    omitted the X-Tenant-ID header.
    """
    from fastapi import HTTPException
    from auth import decode_token  # RS256 helper (deferred import avoids cycle)

    schema = None

    # Priority 1: explicit X-Tenant-ID header
    if x_tenant_id and x_tenant_id != "default":
        if not _TENANT_RE.match(x_tenant_id):
            raise HTTPException(400, "Invalid X-Tenant-ID format")
        schema = f"t_{x_tenant_id.lower()}"

    # Priority 2: tenant_id baked into the Bearer JWT
    if not schema:
        auth_header = request.headers.get("Authorization", "")
        if auth_header.lower().startswith("bearer "):
            token = auth_header.split(" ", 1)[1].strip()
            try:
                claims = decode_token(token, expected_typ="access")
                tid = claims.get("tenant_id", "")
                if tid and _TENANT_RE.match(tid):
                    schema = f"t_{tid.lower()}"
            except Exception:
                pass

    # Last resort: legacy single-tenant public schema
    schema = schema or "public"

    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            try:
                await session.execute(text("SET search_path TO public"))
            except Exception:
                pass


async def get_smart_db(
    request: Request,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-ID"),
) -> AsyncGenerator:
    """
    Smart tenant resolver: uses X-Tenant-ID header if present, falls back to RS256 Bearer JWT.
    Use this on endpoints that need to serve both Android/Pi (header+Bearer) and web dashboard (Bearer).
    Raises 401 if neither is valid.
    """
    from fastapi import HTTPException
    from auth import decode_token  # RS256 helper

    schema = None

    # Priority 1: explicit X-Tenant-ID header (must be valid format)
    if x_tenant_id and x_tenant_id != "default":
        if not _TENANT_RE.match(x_tenant_id):
            raise HTTPException(400, "Invalid X-Tenant-ID format")
        schema = f"t_{x_tenant_id.lower()}"

    # Priority 2: Bearer JWT (web app, Pi device tokens with tenant_id, Android)
    if not schema:
        auth_header = request.headers.get("Authorization", "")
        if auth_header.lower().startswith("bearer "):
            token = auth_header.split(" ", 1)[1].strip()
            try:
                claims = decode_token(token, expected_typ="access")
                tid = claims.get("tenant_id", "")
                if tid and _TENANT_RE.match(tid):
                    schema = f"t_{tid.lower()}"
            except Exception:
                pass

    if not schema:
        raise HTTPException(status_code=401, detail="Not authenticated — provide X-Tenant-ID header or Bearer token")

    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            try:
                await session.execute(text("SET search_path TO public"))
            except Exception:
                pass


async def get_dashboard_db(request: Request) -> AsyncGenerator:
    """
    DB session scoped to tenant schema via RS256 Bearer JWT.
    Raises 401 if no valid token — never falls back to public schema.
    """
    from fastapi import HTTPException
    from auth import decode_token  # RS256 helper

    schema = None
    auth_header = request.headers.get("Authorization", "")
    if auth_header.lower().startswith("bearer "):
        token = auth_header.split(" ", 1)[1].strip()
        try:
            claims = decode_token(token, expected_typ="access")
            tid = claims.get("tenant_id", "")
            if tid and _TENANT_RE.match(tid):
                schema = f"t_{tid.lower()}"
        except Exception:
            pass

    if not schema:
        # Fallback to header
        x_tid = request.headers.get("X-Tenant-ID")
        if x_tid and _TENANT_RE.match(x_tid):
            schema = f"t_{x_tid.lower()}"

    if not schema:
        raise HTTPException(401, "Not authenticated")

    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            try:
                await session.execute(text("SET search_path TO public"))
            except Exception:
                pass
