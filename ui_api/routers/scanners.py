from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from database import get_db
from models import MstScanner, MstZoneScanner

router = APIRouter(prefix="/scanners", tags=["Scanners"])

@router.get("")
async def get_scanners(db: AsyncSession = Depends(get_db)):
    stmt = (
        select(
            MstScanner.mac_id.label("mac"),
            MstScanner.type,
            MstZoneScanner.mst_zone_id.label("zone_id"),
        )
        .join(MstZoneScanner, MstZoneScanner.mst_scanner_id == MstScanner.id)
    )

    result = await db.execute(stmt)

    return [
        {
            "mac": r.mac,
            "type": r.type,
            "zone_id": r.zone_id,
        }
        for r in result
    ]
