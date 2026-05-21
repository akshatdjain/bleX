# database.py (ASYNC VERSION)
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base
from sqlalchemy import text
from typing import AsyncGenerator, Optional
from fastapi import Header
import os

DATABASE_URL = os.getenv("DATABASE_URL", "postgresql+asyncpg://postgres:localdev1234@db:5432/asset_tracking")

if DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+asyncpg://")

engine = create_async_engine(DATABASE_URL, echo=False, future=True)

AsyncSessionLocal = async_sessionmaker(engine, autoflush=False, expire_on_commit=False)

Base = declarative_base()

async def get_db():
    async with AsyncSessionLocal() as session:
        yield session


async def get_tenant_db(
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-ID"),
) -> AsyncGenerator:
    """
    DB session scoped to the tenant's schema.
    Reads X-Tenant-ID header. Falls back to 'public' for legacy requests.
    Schema: t_{tenant_id.lower()}  e.g. t_ry3ddd
    """
    schema = "public" if (not x_tenant_id or x_tenant_id == "default") else f"t_{x_tenant_id.lower()}"
    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            await session.execute(text("SET search_path TO public"))
