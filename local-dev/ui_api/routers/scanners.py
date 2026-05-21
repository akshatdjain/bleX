from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from datetime import datetime, timezone

from database import get_db
from models import MstScanner, MstZoneScanner, MstZone

router = APIRouter(prefix="/scanners", tags=["Scanners"])

_ONLINE_THRESHOLD_SEC = 300  # 5 minutes


def _scanner_status(last_heartbeat) -> str:
    if last_heartbeat is None:
        return "unknown"
    if last_heartbeat.tzinfo is None:
        last_heartbeat = last_heartbeat.replace(tzinfo=timezone.utc)
    diff = (datetime.now(timezone.utc) - last_heartbeat).total_seconds()
    return "online" if diff <= _ONLINE_THRESHOLD_SEC else "offline"


@router.get("")
async def get_scanners(db: AsyncSession = Depends(get_db)):
    """All scanners with zone, type, last_heartbeat, and online/offline status."""
    stmt = (
        select(
            MstScanner.id,
            MstScanner.mac_id.label("mac"),
            MstScanner.name.label("name"),
            MstScanner.type,
            MstScanner.last_heartbeat,
            MstZoneScanner.mst_zone_id.label("zone_id"),
            MstZone.zone_name,
        )
        .outerjoin(MstZoneScanner, MstZoneScanner.mst_scanner_id == MstScanner.id)
        .outerjoin(MstZone, MstZone.id == MstZoneScanner.mst_zone_id)
        .order_by(MstScanner.id)
    )

    rows = (await db.execute(stmt)).all()

    return [
        {
            "id": str(r.id),
            "mac": r.mac,
            "name": r.name or r.mac,
            "type": r.type or "unknown",
            "zone_id": str(r.zone_id) if r.zone_id else None,
            "zone_name": r.zone_name or "Unassigned",
            "last_heartbeat": r.last_heartbeat.isoformat() if r.last_heartbeat else None,
            "status": _scanner_status(r.last_heartbeat),
            "is_online": _scanner_status(r.last_heartbeat) == "online",
        }
        for r in rows
    ]
