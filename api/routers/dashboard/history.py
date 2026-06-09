from fastapi import APIRouter, Depends, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from datetime import date

from database import get_dashboard_db as get_tenant_db
from models import MovementLog
from routers.auth import require_tenant_match

router = APIRouter(prefix="/assets", tags=["History"])

@router.get("/history")
async def get_history(
    start_date: date | None = Query(default=None),
    current_user: dict = Depends(require_tenant_match),
    db: AsyncSession = Depends(get_tenant_db),
):
    stmt = select(MovementLog).order_by(MovementLog.timestamp_movement.desc())

    if start_date:
        stmt = stmt.where(MovementLog.timestamp_movement >= start_date)

    result = await db.execute(stmt)
    logs = result.scalars().all()

    return [
        {
            "id": log.id,
            "bluetooth_id": log.bluetooth_id,
            "mac": log.bluetooth_id,
            "timestamp": log.timestamp_movement,
            "from_zone": log.from_zone_id,
            "to_zone": log.to_zone_id,
            "rssi": log.deciding_rssi,
        }
        for log in logs
    ]
