"""
Asset (beacon) CRUD endpoints.
"""

from datetime import datetime, timezone as _tz
from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_smart_db as get_tenant_db
from models import MstAsset
from schemas import AssetIn
from routers.auth import require_tenant_match

router = APIRouter(prefix="/api/assets", tags=["Assets"])


def _asset_shape(a_row, now=None) -> dict:
    """Build the full Asset shape from a DB row returned by the enriched query."""
    if now is None:
        now = datetime.now(_tz.utc)
    extra = a_row.extra or {}
    if isinstance(extra, str):
        try:
            import json as _j; extra = _j.loads(extra)
        except Exception:
            extra = {}
    is_alive = extra.get("is_alive", True)
    if a_row.last_movement_dt:
        lm = a_row.last_movement_dt
        lm = lm.replace(tzinfo=_tz.utc) if lm.tzinfo is None else lm
        age = (now - lm).total_seconds()
        status = "offline" if not is_alive or age > 300 else "idle" if age > 60 else "active"
        rel = f"{int(age)}s ago" if age < 60 else f"{int(age//60)}m ago" if age < 3600 else f"{int(age//3600)}h ago"
    else:
        status, rel = "offline", "never"
    return {
        "id":                 str(a_row.id),
        "mac":                a_row.mac,
        "name":               a_row.name,
        "shape":              "oval",
        "status":             status,
        "battery":            extra.get("battery"),
        "rssi":               extra.get("deciding_rssi") or extra.get("rssi"),
        "last_seen":          a_row.last_movement_dt.isoformat() if a_row.last_movement_dt else None,
        "last_seen_relative": rel,
        "zone_id":            str(a_row.zone_id) if a_row.zone_id else None,
        "zone_name":          a_row.zone_name or "Unknown",
    }


@router.get("")
async def list_assets(current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    result = await db.execute(select(MstAsset).order_by(MstAsset.id))
    assets = result.scalars().all()
    return [
        {
            "id": a.id,
            "bluetooth_id": a.bluetooth_id,
            "asset_name": a.asset_name,
            "current_zone_id": a.current_zone_id,
        }
        for a in assets
    ]


@router.get("/{asset_id}")
async def get_asset(asset_id: int, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    row = (await db.execute(text("""
        SELECT a.id, a.bluetooth_id AS mac,
               COALESCE(a.asset_name, a.bluetooth_id) AS name,
               a.current_zone_id AS zone_id, z.zone_name,
               a.last_movement_dt, a.extra
        FROM mst_asset a
        LEFT JOIN mst_zone z ON z.id = a.current_zone_id
        WHERE a.id = :aid
    """), {"aid": asset_id})).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Asset not found")
    return _asset_shape(row)


@router.get("/{asset_id}/history")
async def get_asset_history(asset_id: int, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    asset = (await db.execute(text("SELECT bluetooth_id FROM mst_asset WHERE id = :aid"), {"aid": asset_id})).fetchone()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    rows = (await db.execute(text("""
        SELECT ml.id, ml.bluetooth_id AS mac,
               fz.zone_name AS from_zone, ml.from_zone_id,
               tz.zone_name AS to_zone,   ml.to_zone_id,
               ml.deciding_rssi AS rssi,  ml.timestamp_movement AS timestamp,
               CASE WHEN ml.from_zone_id IS NULL THEN 'enter'
                    WHEN ml.to_zone_id   IS NULL THEN 'exit'
                    ELSE 'move' END AS type
        FROM movement_log ml
        LEFT JOIN mst_zone fz ON fz.id = ml.from_zone_id
        LEFT JOIN mst_zone tz ON tz.id = ml.to_zone_id
        WHERE ml.bluetooth_id = :mac
        ORDER BY ml.timestamp_movement DESC
        LIMIT 100
    """), {"mac": asset.bluetooth_id})).fetchall()
    return [
        {
            "id":          str(r.id),
            "mac":         r.mac,
            "from_zone":   r.from_zone or "",
            "from_zone_id":str(r.from_zone_id) if r.from_zone_id else None,
            "to_zone":     r.to_zone or "",
            "to_zone_id":  str(r.to_zone_id) if r.to_zone_id else None,
            "rssi":        float(r.rssi) if r.rssi else None,
            "timestamp":   r.timestamp.isoformat(),
            "type":        r.type,
        }
        for r in rows
    ]


@router.post("")
async def register_asset(payload: AssetIn, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    mac = payload.bluetooth_id.upper()
    existing = await db.execute(
        select(MstAsset).where(MstAsset.bluetooth_id == mac)
    )
    if existing.scalars().first():
        raise HTTPException(status_code=409, detail="Asset already registered")
    asset = MstAsset(bluetooth_id=mac, asset_name=payload.asset_name)
    db.add(asset)
    await db.flush()

    await db.flush()
    await db.commit()
    return {"ok": True, "id": asset.id, "bluetooth_id": asset.bluetooth_id}


@router.put("/{asset_id}")
async def update_asset(asset_id: int, payload: AssetIn, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    result = await db.execute(select(MstAsset).where(MstAsset.id == asset_id))
    asset = result.scalars().first()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    asset.asset_name = payload.asset_name
    asset.bluetooth_id = payload.bluetooth_id.upper()
    await db.flush()
    await db.commit()
    return {"ok": True, "id": asset.id, "asset_name": asset.asset_name}


@router.delete("/{asset_id}")
async def delete_asset(asset_id: int, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    result = await db.execute(select(MstAsset).where(MstAsset.id == asset_id))
    asset = result.scalars().first()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    await db.delete(asset)
    await db.flush()
    await db.commit()
    return {"ok": True}
