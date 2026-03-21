"""
health.py — Health-check endpoints for scanners and beacons.

Scanner health: based on last_heartbeat timestamp in mst_scanner (if column exists).
Beacon health:  based on last movement log timestamp + battery from extra JSON (if column exists).
"""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, text
from datetime import datetime, timezone

from database import get_db
from models import MstScanner, MstZoneScanner, MstZone, MstAsset, MovementLog

router = APIRouter(prefix="/health", tags=["Health"])

_SCANNER_ONLINE_SEC = 300    # 5 min heartbeat threshold
_BEACON_ALIVE_SEC   = 600    # 10 min — beacon considered dead if not seen
_LOW_BATTERY_PCT    = 20


def _scanner_status(last_heartbeat) -> str:
    if last_heartbeat is None:
        return "unknown"
    if last_heartbeat.tzinfo is None:
        last_heartbeat = last_heartbeat.replace(tzinfo=timezone.utc)
    diff = (datetime.now(timezone.utc) - last_heartbeat).total_seconds()
    return "online" if diff <= _SCANNER_ONLINE_SEC else "offline"


def _beacon_status(last_seen) -> str:
    if last_seen is None:
        return "dead"
    if last_seen.tzinfo is None:
        last_seen = last_seen.replace(tzinfo=timezone.utc)
    diff = (datetime.now(timezone.utc) - last_seen).total_seconds()
    if diff < 300:
        return "active"
    if diff < _BEACON_ALIVE_SEC:
        return "idle"
    return "dead"


# ------------------------------------------------------------ column cache --
_col_cache: dict[str, bool] = {}

async def _col_exists(db: AsyncSession, table: str, column: str) -> bool:
    key = f"{table}.{column}"
    if key in _col_cache:
        return _col_cache[key]
    result = await db.execute(
        text(
            "SELECT COUNT(*) FROM information_schema.columns "
            "WHERE table_name = :t AND column_name = :c"
        ),
        {"t": table, "c": column},
    )
    exists = result.scalar() > 0
    _col_cache[key] = exists
    return exists


# ----------------------------------------- GET /health/scanners --
@router.get("/scanners")
async def get_scanner_health(db: AsyncSession = Depends(get_db)):
    """Per-scanner health: online/offline, last heartbeat, zone."""

    has_heartbeat = await _col_exists(db, "mst_scanner", "last_heartbeat")

    if has_heartbeat:
        cols = [
            MstScanner.id,
            MstScanner.mac_id.label("mac"),
            MstScanner.name.label("name"),
            MstScanner.type,
            MstScanner.last_heartbeat,
            MstZoneScanner.mst_zone_id.label("zone_id"),
            MstZone.zone_name,
        ]
    else:
        cols = [
            MstScanner.id,
            MstScanner.mac_id.label("mac"),
            MstScanner.name.label("name"),
            MstScanner.type,
            MstZoneScanner.mst_zone_id.label("zone_id"),
            MstZone.zone_name,
        ]

    stmt = (
        select(*cols)
        .outerjoin(MstZoneScanner, MstZoneScanner.mst_scanner_id == MstScanner.id)
        .outerjoin(MstZone, MstZone.id == MstZoneScanner.mst_zone_id)
        .order_by(MstScanner.id)
    )
    rows = (await db.execute(stmt)).all()

    result = []
    for r in rows:
        hb = getattr(r, "last_heartbeat", None) if has_heartbeat else None
        status = _scanner_status(hb)
        result.append({
            "id": str(r.id),
            "mac": r.mac,
            "name": r.name or r.mac,
            "type": r.type or "unknown",
            "zone_id": str(r.zone_id) if r.zone_id else None,
            "zone_name": r.zone_name or "Unassigned",
            "last_heartbeat": hb.isoformat() if hb else None,
            "status": status,
            "is_online": status == "online",
        })
    return result


# ------------------------------------------ GET /health/beacons --
@router.get("/beacons")
async def get_beacon_health(db: AsyncSession = Depends(get_db)):
    """Per-beacon health: alive/dead, battery %, last_seen."""
    has_extra = await _col_exists(db, "mst_asset", "extra")

    if has_extra:
        cols = [
            MstAsset.id,
            MstAsset.bluetooth_id.label("mac"),
            MstAsset.asset_name.label("name"),
            MstAsset.current_zone_id.label("zone_id"),
            MstAsset.extra,
            MstZone.zone_name,
            func.max(MovementLog.timestamp_movement).label("last_seen"),
        ]
    else:
        cols = [
            MstAsset.id,
            MstAsset.bluetooth_id.label("mac"),
            MstAsset.asset_name.label("name"),
            MstAsset.current_zone_id.label("zone_id"),
            MstZone.zone_name,
            func.max(MovementLog.timestamp_movement).label("last_seen"),
        ]

    stmt = (
        select(*cols)
        .outerjoin(MovementLog, MovementLog.bluetooth_id == MstAsset.bluetooth_id)
        .outerjoin(MstZone, MstZone.id == MstAsset.current_zone_id)
        .group_by(MstAsset.id, MstZone.zone_name)
        .order_by(MstAsset.id)
    )
    rows = (await db.execute(stmt)).all()

    result = []
    for r in rows:
        extra = (getattr(r, "extra", None) or {}) if has_extra else {}
        if not isinstance(extra, dict):
            extra = {}
        battery = extra.get("battery")
        status = _beacon_status(r.last_seen)
        result.append({
            "id": str(r.id),
            "mac": r.mac,
            "name": r.name or r.mac,
            "zone_id": str(r.zone_id) if r.zone_id else None,
            "zone_name": r.zone_name or "Unknown",
            "battery": battery,
            "low_battery": (battery is not None and battery < _LOW_BATTERY_PCT),
            "last_seen": r.last_seen.isoformat() if r.last_seen else None,
            "status": status,
            "is_alive": status != "dead",
        })
    return result


# ---------------------------------- GET /health/summary (dashboard widget) --
@router.get("/summary")
async def get_health_summary(db: AsyncSession = Depends(get_db)):
    """Quick counts for dashboard: total/online scanners, alive/dead beacons."""
    scanners = await get_scanner_health(db)
    beacons  = await get_beacon_health(db)

    return {
        "scanners": {
            "total": len(scanners),
            "online": sum(1 for s in scanners if s["is_online"]),
            "offline": sum(1 for s in scanners if not s["is_online"]),
        },
        "beacons": {
            "total": len(beacons),
            "alive": sum(1 for b in beacons if b["is_alive"]),
            "dead": sum(1 for b in beacons if not b["is_alive"]),
            "low_battery": sum(1 for b in beacons if b["low_battery"]),
        },
    }
