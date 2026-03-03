from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlalchemy.orm import declarative_base
import os


'''
DB_NAME=asset_tracking
DB_USER=postgres
DB_PASS=LYzxeJ2xrSKfzM2f
'''
DATABASE_URL = "postgresql+asyncpg://"
# DATABASE_URL = "postgresql+asyncpg://user:pass@localhost/dbname"


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
