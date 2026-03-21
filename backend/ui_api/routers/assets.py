from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, text
from datetime import date, datetime, timezone

from database import get_db
from models import MstAsset, MovementLog, MstZone

router = APIRouter(prefix="/assets", tags=["Assets"])


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


def _fmt_relative(last_seen) -> str:
    if last_seen is None:
        return "never"
    if last_seen.tzinfo is None:
        last_seen = last_seen.replace(tzinfo=timezone.utc)
    diff = int((datetime.now(timezone.utc) - last_seen).total_seconds())
    if diff < 60:
        return f"{diff}s ago"
    if diff < 3600:
        return f"{diff // 60}m ago"
    if diff < 86400:
        return f"{diff // 3600}h ago"
    return f"{diff // 86400}d ago"


def _extra(row) -> dict:
    """Safely return the extra JSON dict regardless of whether the column exists."""
    try:
        v = row.extra
        return v if isinstance(v, dict) else {}
    except Exception:
        return {}


# --------------------------------------------------- GET /assets/current --
# NOTE: /current and /history must be declared BEFORE /{asset_id}
# so FastAPI doesn't swallow them as integer path params.

@router.get("/current")
async def get_current_assets(db: AsyncSession = Depends(get_db)):
    """All assets (inventory view) with live status, battery, shape, zone name."""

    # Check whether mst_asset.extra exists — graceful fallback if not
    has_extra = await _col_exists(db, "mst_asset", "extra")

    if has_extra:
        stmt = (
            select(
                MstAsset.id,
                MstAsset.bluetooth_id.label("mac"),
                MstAsset.asset_name.label("name"),
                MstAsset.current_zone_id.label("zone_id"),
                MstAsset.extra,
                MstZone.zone_name,
                func.max(MovementLog.timestamp_movement).label("last_seen"),
                func.max(MovementLog.deciding_rssi).label("rssi"),
            )
            .outerjoin(MovementLog, MovementLog.bluetooth_id == MstAsset.bluetooth_id)
            .outerjoin(MstZone, MstZone.id == MstAsset.current_zone_id)
            .group_by(MstAsset.id, MstZone.zone_name)
            .order_by(MstAsset.id)
        )
    else:
        stmt = (
            select(
                MstAsset.id,
                MstAsset.bluetooth_id.label("mac"),
                MstAsset.asset_name.label("name"),
                MstAsset.current_zone_id.label("zone_id"),
                MstZone.zone_name,
                func.max(MovementLog.timestamp_movement).label("last_seen"),
                func.max(MovementLog.deciding_rssi).label("rssi"),
            )
            .outerjoin(MovementLog, MovementLog.bluetooth_id == MstAsset.bluetooth_id)
            .outerjoin(MstZone, MstZone.id == MstAsset.current_zone_id)
            .group_by(MstAsset.id, MstZone.zone_name)
            .order_by(MstAsset.id)
        )

    rows = (await db.execute(stmt)).all()
    extra_data = {r.id: _extra(r) for r in rows} if has_extra else {}

    return [
        {
            "id": str(r.id),
            "mac": r.mac,
            "name": r.name or r.mac,
            "shape": extra_data.get(r.id, {}).get("shape", "pebble"),
            "status": _asset_status(r.last_seen),
            "battery": extra_data.get(r.id, {}).get("battery"),
            "rssi": float(r.rssi) if r.rssi is not None else None,
            "last_seen": r.last_seen.isoformat() if r.last_seen else None,
            "last_seen_relative": _fmt_relative(r.last_seen),
            "zone_id": str(r.zone_id) if r.zone_id else None,
            "zone_name": r.zone_name or "Unknown",
        }
        for r in rows
    ]


# ----------------------------------------------- GET /assets/history --
@router.get("/history")
async def get_history(
    start_date: date | None = Query(default=None),
    asset_id: int | None = Query(default=None),
    limit: int = Query(default=200, le=1000),
    db: AsyncSession = Depends(get_db),
):
    """Global movement history feed. Optional filters: start_date, asset_id, limit."""
    stmt = (
        select(
            MovementLog.id,
            MovementLog.bluetooth_id,
            MovementLog.timestamp_movement.label("timestamp"),
            MovementLog.from_zone_id,
            MovementLog.to_zone_id,
            MovementLog.deciding_rssi.label("rssi"),
            MstAsset.id.label("asset_db_id"),
            MstAsset.asset_name.label("asset_name"),
        )
        .outerjoin(MstAsset, MstAsset.bluetooth_id == MovementLog.bluetooth_id)
        .order_by(MovementLog.timestamp_movement.desc())
        .limit(limit)
    )

    if start_date:
        stmt = stmt.where(
            MovementLog.timestamp_movement >= datetime.combine(start_date, datetime.min.time())
        )

    if asset_id:
        asset_row = (
            await db.execute(select(MstAsset.bluetooth_id).where(MstAsset.id == asset_id))
        ).scalar_one_or_none()
        if asset_row:
            stmt = stmt.where(MovementLog.bluetooth_id == asset_row)

    logs = (await db.execute(stmt)).all()

    zone_ids = {l.from_zone_id for l in logs if l.from_zone_id} | \
               {l.to_zone_id for l in logs if l.to_zone_id}
    zone_map: dict[int, str] = {}
    if zone_ids:
        zone_rows = (
            await db.execute(select(MstZone.id, MstZone.zone_name).where(MstZone.id.in_(zone_ids)))
        ).all()
        zone_map = {r.id: r.zone_name for r in zone_rows}

    def log_type(from_z, to_z):
        if not from_z:
            return "enter"
        if not to_z:
            return "exit"
        return "move"

    return [
        {
            "id": str(l.id),
            "asset_id": str(l.asset_db_id) if l.asset_db_id else None,
            "asset_name": l.asset_name or l.bluetooth_id,
            "mac": l.bluetooth_id,
            "timestamp": l.timestamp.isoformat() if l.timestamp else None,
            "from_zone_id": str(l.from_zone_id) if l.from_zone_id else None,
            "from_zone": zone_map.get(l.from_zone_id, "—") if l.from_zone_id else "—",
            "to_zone_id": str(l.to_zone_id) if l.to_zone_id else None,
            "to_zone": zone_map.get(l.to_zone_id, "—") if l.to_zone_id else "—",
            "rssi": float(l.rssi) if l.rssi is not None else None,
            "type": log_type(l.from_zone_id, l.to_zone_id),
        }
        for l in logs
    ]


# --------------------------------------------------------- GET /assets/{id} --
@router.get("/{asset_id}")
async def get_asset(asset_id: int, db: AsyncSession = Depends(get_db)):
    """Single asset detail."""
    has_extra = await _col_exists(db, "mst_asset", "extra")

    if has_extra:
        cols = [
            MstAsset.id, MstAsset.bluetooth_id.label("mac"),
            MstAsset.asset_name.label("name"), MstAsset.current_zone_id.label("zone_id"),
            MstAsset.extra, MstZone.zone_name,
            func.max(MovementLog.timestamp_movement).label("last_seen"),
            func.max(MovementLog.deciding_rssi).label("rssi"),
        ]
    else:
        cols = [
            MstAsset.id, MstAsset.bluetooth_id.label("mac"),
            MstAsset.asset_name.label("name"), MstAsset.current_zone_id.label("zone_id"),
            MstZone.zone_name,
            func.max(MovementLog.timestamp_movement).label("last_seen"),
            func.max(MovementLog.deciding_rssi).label("rssi"),
        ]

    row = (
        await db.execute(
            select(*cols)
            .where(MstAsset.id == asset_id)
            .outerjoin(MovementLog, MovementLog.bluetooth_id == MstAsset.bluetooth_id)
            .outerjoin(MstZone, MstZone.id == MstAsset.current_zone_id)
            .group_by(MstAsset.id, MstZone.zone_name)
        )
    ).first()

    if not row:
        raise HTTPException(status_code=404, detail="Asset not found")

    extra = _extra(row) if has_extra else {}
    return {
        "id": str(row.id),
        "mac": row.mac,
        "name": row.name or row.mac,
        "shape": extra.get("shape", "pebble"),
        "status": _asset_status(row.last_seen),
        "battery": extra.get("battery"),
        "rssi": float(row.rssi) if row.rssi is not None else None,
        "last_seen": row.last_seen.isoformat() if row.last_seen else None,
        "last_seen_relative": _fmt_relative(row.last_seen),
        "zone_id": str(row.zone_id) if row.zone_id else None,
        "zone_name": row.zone_name or "Unknown",
    }


# ------------------------------------------------ GET /assets/{id}/history --
@router.get("/{asset_id}/history")
async def get_asset_history(asset_id: int, db: AsyncSession = Depends(get_db)):
    """Movement history for a single asset."""
    asset = (
        await db.execute(select(MstAsset).where(MstAsset.id == asset_id))
    ).scalar_one_or_none()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")

    stmt = (
        select(
            MovementLog.id,
            MovementLog.timestamp_movement.label("timestamp"),
            MovementLog.from_zone_id,
            MovementLog.to_zone_id,
            MovementLog.deciding_rssi.label("rssi"),
        )
        .where(MovementLog.bluetooth_id == asset.bluetooth_id)
        .order_by(MovementLog.timestamp_movement.desc())
        .limit(100)
    )
    logs = (await db.execute(stmt)).all()

    zone_ids = set()
    for l in logs:
        if l.from_zone_id:
            zone_ids.add(l.from_zone_id)
        if l.to_zone_id:
            zone_ids.add(l.to_zone_id)

    zone_map: dict[int, str] = {}
    if zone_ids:
        zone_rows = (
            await db.execute(select(MstZone.id, MstZone.zone_name).where(MstZone.id.in_(zone_ids)))
        ).all()
        zone_map = {r.id: r.zone_name for r in zone_rows}

    def log_type(from_z, to_z):
        if not from_z:
            return "enter"
        if not to_z:
            return "exit"
        return "move"

    return [
        {
            "id": str(l.id),
            "timestamp": l.timestamp.isoformat() if l.timestamp else None,
            "from_zone_id": str(l.from_zone_id) if l.from_zone_id else None,
            "from_zone": zone_map.get(l.from_zone_id, "—") if l.from_zone_id else "—",
            "to_zone_id": str(l.to_zone_id) if l.to_zone_id else None,
            "to_zone": zone_map.get(l.to_zone_id, "—") if l.to_zone_id else "—",
            "rssi": float(l.rssi) if l.rssi is not None else None,
            "type": log_type(l.from_zone_id, l.to_zone_id),
        }
        for l in logs
    ]


# -------------------------------------------------------- internal helpers --
_col_cache: dict[str, bool] = {}

async def _col_exists(db: AsyncSession, table: str, column: str) -> bool:
    """Check if a column exists in the DB. Result is cached per process lifetime."""
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
