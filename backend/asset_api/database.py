# database.py (ASYNC VERSION)
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base
import os

DATABASE_URL = "postgresql+asyncpg://postgres:LYzxeJ2xrSKfzM2f@localhost/asset_tracking"
# DATABASE_URL = "postgresql+asyncpg://user:pass@localhost/dbname"


print(f'DATABASE_URL={DATABASE_URL}')

# MUST be async URL → postgresql+asyncpg
if DATABASE_URL.startswith("postgresql://"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+asyncpg://")

engine = create_async_engine(
    DATABASE_URL,
    echo=False,
    future=True
)

AsyncSessionLocal = async_sessionmaker(
    engine,
    autoflush=False,
    expire_on_commit=False,
)

Base = declarative_base()

# Dependency
async def get_db():
    async with AsyncSessionLocal() as session:
        yield session
