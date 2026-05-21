from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, and_, text
from datetime import datetime, timezone

from database import get_db
from models import MstZone, MstZoneScanner, MstScanner, MstAsset, MovementLog

router = APIRouter(prefix="/zones", tags=["Zones"])

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


# ------------------------------------------------------------------ helpers --
def _asset_status(last_seen) -> str:
    if last_seen is None:
        return "offline"
    if last_seen.tzinfo is None:
        last_seen = last_seen.replace(tzinfo=timezone.utc)
    diff = (datetime.now(timezone.utc) - last_seen).total_seconds()
    if diff < 300:
        return "active"
    if diff < 1800:
        return "idle"
    return "offline"


def _today_start():
    return datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)


# --------------------------------------------------------------- GET /zones --
@router.get("")
async def get_zones(db: AsyncSession = Depends(get_db)):
    """All zones with asset count, movement count today, scanner id, is_active."""

    # asset count per zone
    asset_sub = (
        select(MstAsset.current_zone_id, func.count(MstAsset.id).label("n"))
        .where(MstAsset.current_zone_id.isnot(None))
        .group_by(MstAsset.current_zone_id)
        .subquery()
    )

    # movement count today (arrivals)
    mv_sub = (
        select(MovementLog.to_zone_id, func.count(MovementLog.id).label("n"))
        .where(
            and_(
                MovementLog.to_zone_id.isnot(None),
                MovementLog.timestamp_movement >= _today_start(),
            )
        )
        .group_by(MovementLog.to_zone_id)
        .subquery()
    )

    # first scanner assigned to each zone
    scn_sub = (
        select(
            MstZoneScanner.mst_zone_id,
            MstScanner.mac_id.label("mac"),
            MstScanner.name.label("scn_name"),
        )
        .join(MstScanner, MstScanner.id == MstZoneScanner.mst_scanner_id)
        .distinct(MstZoneScanner.mst_zone_id)
        .subquery()
    )

    stmt = (
        select(
            MstZone.id,
            MstZone.zone_name,
            MstZone.description,
            func.coalesce(asset_sub.c.n, 0).label("asset_count"),
            func.coalesce(mv_sub.c.n, 0).label("movement_count"),
            scn_sub.c.mac,
            scn_sub.c.scn_name,
        )
        .outerjoin(asset_sub, asset_sub.c.current_zone_id == MstZone.id)
        .outerjoin(mv_sub, mv_sub.c.to_zone_id == MstZone.id)
        .outerjoin(scn_sub, scn_sub.c.mst_zone_id == MstZone.id)
        .order_by(MstZone.id)
    )

    rows = (await db.execute(stmt)).all()

    return [
        {
            "id": str(r.id),
            "name": r.zone_name,
            "description": r.description,
            "scanner_id": r.scn_name or r.mac or f"SCN-{r.id:02d}",
            "asset_count": r.asset_count,
            "movement_count": r.movement_count,
            "is_active": r.asset_count > 0,
        }
        for r in rows
    ]


# -------------------------------------------------------- GET /zones/{id} --
@router.get("/{zone_id}")
async def get_zone_detail(zone_id: int, db: AsyncSession = Depends(get_db)):
    """Zone detail including embedded list of current assets."""

    zone = (await db.execute(select(MstZone).where(MstZone.id == zone_id))).scalar_one_or_none()
    if not zone:
        raise HTTPException(status_code=404, detail="Zone not found")

    # scanner for zone
    scn_row = (
        await db.execute(
            select(MstScanner.mac_id, MstScanner.name)
            .join(MstZoneScanner, MstZoneScanner.mst_scanner_id == MstScanner.id)
            .where(MstZoneScanner.mst_zone_id == zone_id)
            .limit(1)
        )
    ).first()

    # whether extra column exists
    has_extra = await _col_exists(db, "mst_asset", "extra")

    # assets in zone with last_seen + rssi
    if has_extra:
        asset_cols = [
            MstAsset.id,
            MstAsset.bluetooth_id.label("mac"),
            MstAsset.asset_name.label("name"),
            MstAsset.extra,
            func.max(MovementLog.timestamp_movement).label("last_seen"),
            func.max(MovementLog.deciding_rssi).label("rssi"),
        ]
    else:
        asset_cols = [
            MstAsset.id,
            MstAsset.bluetooth_id.label("mac"),
            MstAsset.asset_name.label("name"),
            func.max(MovementLog.timestamp_movement).label("last_seen"),
            func.max(MovementLog.deciding_rssi).label("rssi"),
        ]

    asset_rows = (
        await db.execute(
            select(*asset_cols)
            .where(MstAsset.current_zone_id == zone_id)
            .outerjoin(MovementLog, MovementLog.bluetooth_id == MstAsset.bluetooth_id)
            .group_by(MstAsset.id)
        )
    ).all()

    # movements today into this zone
    mv_count = (
        await db.execute(
            select(func.count(MovementLog.id)).where(
                and_(
                    MovementLog.to_zone_id == zone_id,
                    MovementLog.timestamp_movement >= _today_start(),
                )
            )
        )
    ).scalar_one() or 0

    assets = []
    for r in asset_rows:
        extra = (getattr(r, "extra", None) or {}) if has_extra else {}
        if not isinstance(extra, dict):
            extra = {}
        assets.append({
            "id": str(r.id),
            "mac": r.mac,
            "name": r.name or r.mac,
            "shape": extra.get("shape", "pebble"),
            "status": _asset_status(r.last_seen),
            "battery": extra.get("battery"),
            "rssi": float(r.rssi) if r.rssi is not None else None,
            "last_seen": r.last_seen.isoformat() if r.last_seen else None,
            "last_seen_relative": "—",
            "zone_id": str(zone_id),
            "zone_name": zone.zone_name,
        })

    return {
        "id": str(zone.id),
        "name": zone.zone_name,
        "description": zone.description,
        "scanner_id": (scn_row.name if scn_row else None)
            or (scn_row.mac_id if scn_row else None)
            or f"SCN-{zone_id:02d}",
        "asset_count": len(assets),
        "movement_count": mv_count,
        "is_active": len(assets) > 0,
        "assets": assets,
    }
