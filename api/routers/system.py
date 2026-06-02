"""
system.py — System health diagnostic endpoint.

GET /api/system/health
  Full system check: DB, Redis, API version, timestamp.
  Called by SAGE on Pi nodes to verify backend is reachable and healthy.
  No auth required — Pi devices call this before tenant-scoped operations.
"""

from fastapi import APIRouter
from sqlalchemy import text
import os
import redis as redis_lib
from datetime import datetime, timezone

router = APIRouter(prefix="/api/system", tags=["System"])

APP_VERSION = os.getenv("APP_VERSION", "3.1.1")

REDIS_HOST     = os.getenv("REDIS_HOST", "redis")
REDIS_PORT     = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "")

DATABASE_URL = os.getenv("DATABASE_URL", "")


async def _check_db() -> dict:
    try:
        from database import AsyncSessionLocal
        async with AsyncSessionLocal() as session:
            await session.execute(text("SELECT 1"))
        return {"status": "ok"}
    except Exception as e:
        return {"status": "error", "detail": str(e)[:100]}


def _check_redis() -> dict:
    try:
        kwargs = {"host": REDIS_HOST, "port": REDIS_PORT, "socket_timeout": 2}
        if REDIS_PASSWORD:
            kwargs["password"] = REDIS_PASSWORD
        r = redis_lib.Redis(**kwargs)
        r.ping()
        return {"status": "ok"}
    except Exception as e:
        return {"status": "error", "detail": str(e)[:100]}


@router.get("/health")
async def system_health():
    """
    Full system health check for SAGE and monitoring.
    Returns DB status, Redis status, version, and current timestamp.
    """
    db_status    = await _check_db()
    redis_status = _check_redis()

    all_ok = db_status["status"] == "ok" and redis_status["status"] == "ok"

    return {
        "status":    "ok" if all_ok else "degraded",
        "version":   APP_VERSION,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "checks": {
            "db":    db_status,
            "redis": redis_status,
        },
    }
