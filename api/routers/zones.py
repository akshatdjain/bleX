"""
Zone CRUD + zone-scanner assignment endpoints.
"""

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
                WHERE ml.timestamp_movement >= CURRENT_DATE
            )                                                                   AS movement_count,
            BOOL_OR(s.scanner_status = 'active')                                AS has_active_scanner
        FROM mst_zone z
        LEFT JOIN mst_asset   a  ON a.current_zone_id = z.id
        LEFT JOIN movement_log ml ON ml.to_zone_id = z.id
        LEFT JOIN mst_zone_scanner zs ON zs.mst_zone_id = z.id
        LEFT JOIN mst_scanner  s  ON s.id = zs.mst_scanner_id
        GROUP BY z.id, z.zone_name, z.description
        ORDER BY z.id
    """))).fetchall()

    return [
        {
            "id":             str(r.id),
            "name":           r.name,
            "description":    r.description or "",
            "asset_count":    r.asset_count or 0,
            "movement_count": r.movement_count or 0,
            "is_active":      bool(r.has_active_scanner) or (r.asset_count or 0) > 0,
            "scanner_id":     None,
        }
        for r in rows
    ]


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
