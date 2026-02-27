from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from database import get_db
from models import MstAsset, MovementLog

router = APIRouter(prefix="/assets", tags=["Assets"])

@router.get("/current")
async def get_current_assets(db: AsyncSession = Depends(get_db)):
    """
    Returns ALL assets (inventory view).
    UI decides live/sleep based on last_seen.
    """
    stmt = (
        select(
            MstAsset.id,
            MstAsset.bluetooth_id.label("mac"),
            MstAsset.asset_name.label("name"),
            MstAsset.current_zone_id.label("zone"),
            func.max(MovementLog.timestamp_movement).label("last_seen"),
            func.max(MovementLog.deciding_rssi).label("rssi"),
        )
        .outerjoin(
            MovementLog,
            MovementLog.bluetooth_id == MstAsset.bluetooth_id
        )
        .group_by(MstAsset.id)
        .order_by(MstAsset.id)
    )

    result = await db.execute(stmt)

    return [
        {
            "id": r.id,
            "mac": r.mac,
            "name": r.name,
            "zone": r.zone,
            "rssi": r.rssi,
            "last_seen": r.last_seen,
        }
        for r in result
    ]

