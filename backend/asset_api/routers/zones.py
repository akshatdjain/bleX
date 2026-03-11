"""
Zone CRUD + zone-scanner assignment endpoints.
"""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import MstZone, MstZoneScanner, MstScanner
from schemas import ZoneIn, ZoneScannerIn
from events import notify_zone_map_changed

router = APIRouter(prefix="/api/zones", tags=["Zones"])


@router.get("")
async def list_zones(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(MstZone).order_by(MstZone.id))
    zones = result.scalars().all()
    out = []
    for z in zones:
        stmt = (
            select(MstScanner.id, MstScanner.mac_id, MstScanner.name, MstScanner.type)
            .join(MstZoneScanner, MstZoneScanner.mst_scanner_id == MstScanner.id)
            .where(MstZoneScanner.mst_zone_id == z.id)
        )
        scanners_result = await db.execute(stmt)
        scanners = [
            {"id": s.id, "mac": s.mac_id, "name": s.name, "type": s.type}
            for s in scanners_result
        ]
        out.append({
            "id": z.id,
            "zone_name": z.zone_name,
            "description": z.description,
            "scanners": scanners
        })
    return out


@router.post("")
async def create_zone(payload: ZoneIn, db: AsyncSession = Depends(get_db)):
    zone = MstZone(zone_name=payload.zone_name, description=payload.description)
    db.add(zone)
    await db.commit()
    await notify_zone_map_changed()
    await db.refresh(zone)
    return {"ok": True, "id": zone.id, "zone_name": zone.zone_name}


@router.put("/{zone_id}")
async def update_zone(zone_id: int, payload: ZoneIn, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(MstZone).where(MstZone.id == zone_id))
    zone = result.scalars().first()
    if not zone:
        raise HTTPException(status_code=404, detail="Zone not found")
    zone.zone_name = payload.zone_name
    zone.description = payload.description
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True, "id": zone.id, "zone_name": zone.zone_name}


@router.delete("/{zone_id}")
async def delete_zone(zone_id: int, db: AsyncSession = Depends(get_db)):
    await db.execute(
        delete(MstZoneScanner).where(MstZoneScanner.mst_zone_id == zone_id)
    )
    result = await db.execute(select(MstZone).where(MstZone.id == zone_id))
    zone = result.scalars().first()
    if not zone:
        raise HTTPException(status_code=404, detail="Zone not found")
    await db.delete(zone)
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True}


# ─── Zone ↔ Scanner Assignment ───────────────────────────────────────────────

@router.post("/{zone_id}/scanners")
async def assign_scanner_to_zone(
    zone_id: int, payload: ZoneScannerIn, db: AsyncSession = Depends(get_db)
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
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True}


@router.delete("/{zone_id}/scanners/{scanner_id}")
async def unassign_scanner_from_zone(
    zone_id: int, scanner_id: int, db: AsyncSession = Depends(get_db)
):
    await db.execute(
        delete(MstZoneScanner)
        .where(MstZoneScanner.mst_zone_id == zone_id)
        .where(MstZoneScanner.mst_scanner_id == scanner_id)
    )
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True}
