"""
Scanner CRUD endpoints.
"""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import MstScanner, MstZoneScanner
from schemas import ScannerIn
from events import notify_zone_map_changed

router = APIRouter(prefix="/api/scanners", tags=["Scanners"])


@router.get("")
async def list_scanners(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(MstScanner).order_by(MstScanner.id))
    scanners = result.scalars().all()
    return [
        {
            "id": s.id,
            "mac_id": s.mac_id,
            "name": s.name,
            "type": s.type,
        }
        for s in scanners
    ]


@router.post("")
async def register_scanner(payload: ScannerIn, db: AsyncSession = Depends(get_db)):
    mac = payload.mac_id.upper()
    existing = await db.execute(
        select(MstScanner).where(MstScanner.mac_id == mac)
    )
    if existing.scalars().first():
        raise HTTPException(status_code=409, detail="Scanner already registered")
    scanner = MstScanner(mac_id=mac, name=payload.name, type=payload.type)
    db.add(scanner)
    await db.commit()
    await db.refresh(scanner)
    return {"ok": True, "id": scanner.id, "mac_id": scanner.mac_id}


@router.delete("/{scanner_id}")
async def delete_scanner(scanner_id: int, db: AsyncSession = Depends(get_db)):
    await db.execute(
        delete(MstZoneScanner).where(MstZoneScanner.mst_scanner_id == scanner_id)
    )
    result = await db.execute(select(MstScanner).where(MstScanner.id == scanner_id))
    scanner = result.scalars().first()
    if not scanner:
        raise HTTPException(status_code=404, detail="Scanner not found")
    await db.delete(scanner)
    await db.commit()
    await notify_zone_map_changed()
    return {"ok": True}


@router.put("/by-mac/{mac}")
async def upsert_scanner(mac: str, payload: ScannerIn, db: AsyncSession = Depends(get_db)):
    """Register or update a scanner by MAC address (upsert behavior)."""
    normalized_mac = mac.upper()
    result = await db.execute(
        select(MstScanner).where(MstScanner.mac_id == normalized_mac)
    )
    existing = result.scalars().first()
    if existing:
        # Update existing entry
        if payload.name is not None:
            existing.name = payload.name
        if payload.type is not None:
            existing.type = payload.type
        await db.commit()
        await db.refresh(existing)
        return {"ok": True, "id": existing.id, "mac_id": existing.mac_id, "updated": True}
    else:
        # Create new entry
        scanner = MstScanner(mac_id=normalized_mac, name=payload.name, type=payload.type)
        db.add(scanner)
        await db.commit()
        await db.refresh(scanner)
        return {"ok": True, "id": scanner.id, "mac_id": scanner.mac_id, "updated": False}
