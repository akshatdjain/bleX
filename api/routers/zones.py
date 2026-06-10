"""
Zone CRUD + zone-scanner assignment endpoints.
"""

from datetime import datetime
from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import select, delete, update, text
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_smart_db as get_tenant_db
from models import MstZone, MstZoneScanner, MstScanner, MstAsset, MovementLog
from schemas import ZoneIn, ZoneScannerIn
from events import notify_zone_map_changed
from routers.auth import require_tenant_match

router = APIRouter(prefix="/api/zones", tags=["Zones"])


@router.get("")
async def list_zones(current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    rows = (await db.execute(text("""
        SELECT
            z.id,
            z.zone_name                                                         AS name,
            z.description,
            COUNT(DISTINCT a.id)                                                AS asset_count,
            COUNT(DISTINCT ml.id) FILTER (
                WHERE ml.timestamp_movement >= NOW() - INTERVAL '24 hours'
            )                                                                   AS movement_count,
            BOOL_OR(s.scanner_status = 'active')                                AS has_active_scanner,
            COALESCE(
                json_agg(json_build_object(
                    'id',   s.id,
                    'mac',  s.mac_id,
                    'name', s.name,
                    'type', s.type
                )) FILTER (WHERE s.id IS NOT NULL),
                '[]'
            )                                                                   AS scanners
        FROM mst_zone z
        LEFT JOIN mst_asset    a  ON a.current_zone_id = z.id
        LEFT JOIN movement_log ml ON ml.to_zone_id = z.id
        LEFT JOIN mst_zone_scanner zs ON zs.mst_zone_id = z.id
        LEFT JOIN mst_scanner  s  ON s.id = zs.mst_scanner_id
        GROUP BY z.id, z.zone_name, z.description
        ORDER BY z.id
    """))).fetchall()

    import json as _json
    return [
        {
            "id":             str(r.id),
            "name":           r.name,
            "description":    r.description or "",
            "asset_count":    r.asset_count or 0,
            "movement_count": r.movement_count or 0,
            "is_active":      (r.movement_count or 0) > 0 or (r.asset_count or 0) > 0,
            "scanner_id":     None,
            "scanners":       _json.loads(r.scanners) if isinstance(r.scanners, str) else (r.scanners or []),
        }
        for r in rows
    ]


@router.get("/{zone_id}")
async def get_zone(zone_id: int, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    """Zone detail: zone info + assets currently in zone + assigned scanners."""
    row = (await db.execute(text("""
        SELECT
            z.id, z.zone_name AS name, z.description,
            COUNT(DISTINCT ml.id) FILTER (
                WHERE ml.timestamp_movement >= NOW() - INTERVAL '24 hours'
            ) AS movement_count
        FROM mst_zone z
        LEFT JOIN movement_log ml ON ml.to_zone_id = z.id
        WHERE z.id = :zid
        GROUP BY z.id, z.zone_name, z.description
    """), {"zid": zone_id})).fetchone()

    if not row:
        raise HTTPException(status_code=404, detail="Zone not found")

    from datetime import timezone as _tz
    now = datetime.now(_tz.utc)

    asset_rows = (await db.execute(text("""
        SELECT a.id, a.bluetooth_id AS mac,
               COALESCE(a.asset_name, a.bluetooth_id) AS name,
               a.last_movement_dt, a.extra
        FROM mst_asset a
        WHERE a.current_zone_id = :zid
    """), {"zid": zone_id})).fetchall()

    assets = []
    for a in asset_rows:
        extra = a.extra or {}
        if isinstance(extra, str):
            try:
                import json as _j; extra = _j.loads(extra)
            except Exception:
                extra = {}
        is_alive = extra.get("is_alive", True)
        if a.last_movement_dt:
            age = (now - a.last_movement_dt.replace(tzinfo=_tz.utc)
                   if a.last_movement_dt.tzinfo is None
                   else (now - a.last_movement_dt)).total_seconds()
            status = "offline" if not is_alive or age > 300 else "idle" if age > 60 else "active"
            rel = f"{int(age)}s ago" if age < 60 else f"{int(age//60)}m ago" if age < 3600 else f"{int(age//3600)}h ago"
        else:
            status, rel = "offline", "never"
        assets.append({
            "id": str(a.id), "mac": a.mac, "name": a.name,
            "shape": "oval", "status": status,
            "battery": extra.get("battery"),
            "rssi": extra.get("deciding_rssi") or extra.get("rssi"),
            "last_seen": a.last_movement_dt.isoformat() if a.last_movement_dt else None,
            "last_seen_relative": rel,
        })

    scanner_rows = (await db.execute(text("""
        SELECT s.id, s.mac_id AS mac, s.name, s.type, s.scanner_status AS status,
               s.last_heartbeat
        FROM mst_scanner s
        JOIN mst_zone_scanner zs ON zs.mst_scanner_id = s.id
        WHERE zs.mst_zone_id = :zid
    """), {"zid": zone_id})).fetchall()

    scanners = [{"id": s.id, "mac": s.mac, "name": s.name or s.mac,
                 "type": s.type, "status": s.status or "offline"} for s in scanner_rows]

    return {
        "id": str(row.id),
        "name": row.name,
        "description": row.description or "",
        "movement_count": row.movement_count or 0,
        "asset_count": len(assets),
        "is_active": len(assets) > 0 or (row.movement_count or 0) > 0,
        "scanner_id": None,
        "assets": assets,
        "scanners": scanners,
    }


@router.post("")
async def create_zone(payload: ZoneIn, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    zone = MstZone(zone_name=payload.zone_name, description=payload.description)
    db.add(zone)
    await db.flush()
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True, "id": zone.id, "zone_name": zone.zone_name}


@router.put("/{zone_id}")
async def update_zone(zone_id: int, payload: ZoneIn, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    result = await db.execute(select(MstZone).where(MstZone.id == zone_id))
    zone = result.scalars().first()
    if not zone:
        raise HTTPException(status_code=404, detail="Zone not found")
    zone.zone_name = payload.zone_name
    zone.description = payload.description
    await db.flush()
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True, "id": zone.id, "zone_name": zone.zone_name}


@router.delete("/{zone_id}")
async def delete_zone(zone_id: int, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)):
    # 1. Clear associations in MstZoneScanner
    await db.execute(
        delete(MstZoneScanner).where(MstZoneScanner.mst_zone_id == zone_id)
    )

    # 2. Nullify current_zone_id in MstAsset
    await db.execute(
        update(MstAsset)
        .where(MstAsset.current_zone_id == zone_id)
        .values(current_zone_id=None)
    )

    # 3. Nullify from_zone_id and to_zone_id in MovementLog
    await db.execute(
        update(MovementLog)
        .where(MovementLog.from_zone_id == zone_id)
        .values(from_zone_id=None)
    )
    await db.execute(
        update(MovementLog)
        .where(MovementLog.to_zone_id == zone_id)
        .values(to_zone_id=None)
    )

    result = await db.execute(select(MstZone).where(MstZone.id == zone_id))
    zone = result.scalars().first()
    if not zone:
        raise HTTPException(status_code=404, detail="Zone not found")
    await db.delete(zone)
    await db.flush()
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True}


# ─── Zone ↔ Scanner Assignment ───────────────────────────────────────────────

@router.post("/{zone_id}/scanners")
async def assign_scanner_to_zone(
    zone_id: int, payload: ZoneScannerIn, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)
):
    z = await db.execute(select(MstZone).where(MstZone.id == zone_id))
    if not z.scalars().first():
        raise HTTPException(status_code=404, detail="Zone not found")

    s = await db.execute(select(MstScanner).where(MstScanner.id == payload.scanner_id))
    if not s.scalars().first():
        raise HTTPException(status_code=404, detail="Scanner not found")

    existing = await db.execute(
        select(MstZoneScanner)
        .where(MstZoneScanner.mst_zone_id == zone_id)
        .where(MstZoneScanner.mst_scanner_id == payload.scanner_id)
    )
    if existing.scalars().first():
        return {"ok": True, "detail": "already assigned"}

    mapping = MstZoneScanner(mst_zone_id=zone_id, mst_scanner_id=payload.scanner_id)
    db.add(mapping)
    await db.flush()
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True}


@router.delete("/{zone_id}/scanners/{scanner_id}")
async def unassign_scanner_from_zone(
    zone_id: int, scanner_id: int, current_user: dict = Depends(require_tenant_match), db: AsyncSession = Depends(get_tenant_db)
):
    await db.execute(
        delete(MstZoneScanner)
        .where(MstZoneScanner.mst_zone_id == zone_id)
        .where(MstZoneScanner.mst_scanner_id == scanner_id)
    )
    await db.flush()
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True}
