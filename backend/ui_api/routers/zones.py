from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from database import get_db
from models import MstZone, MstScanner, MstZoneScanner
from schemas import ZoneOut, ScannerOut

router = APIRouter(prefix="/zones", tags=["zones"])


@router.get("/")
async def list_zones(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(MstZone))
    return result.scalars().all()


@router.get("/{zone_id}/scanners")
async def scanners_in_zone(zone_id: int, db: AsyncSession = Depends(get_db)):
    stmt = (
        select(
            MstScanner.id,
            MstScanner.mac_id,
            MstScanner.name,
            MstScanner.type
        )
        .join(MstZoneScanner, MstZoneScanner.mst_scanner_id == MstScanner.id)
        .where(MstZoneScanner.mst_zone_id == zone_id)
    )

    result = await db.execute(stmt)
    return [
        {
            "id": r.id,
            "mac": r.mac_id,
            "name": r.name,
            "type": r.type
        }
        for r in result
    ]
