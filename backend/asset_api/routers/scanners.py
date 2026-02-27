"""
Scanner CRUD endpoints.
"""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import MstScanner, MstZoneScanner
from schemas import ScannerIn

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
    return {"ok": True}
