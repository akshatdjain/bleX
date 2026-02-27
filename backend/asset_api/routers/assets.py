"""
Asset (beacon) CRUD endpoints.
"""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import MstAsset
from schemas import AssetIn

router = APIRouter(prefix="/api/assets", tags=["Assets"])


@router.get("")
async def list_assets(db: AsyncSession = Depends(get_db)):
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


@router.post("")
async def register_asset(payload: AssetIn, db: AsyncSession = Depends(get_db)):
    mac = payload.bluetooth_id.upper()
    existing = await db.execute(
        select(MstAsset).where(MstAsset.bluetooth_id == mac)
    )
    if existing.scalars().first():
        raise HTTPException(status_code=409, detail="Asset already registered")
    asset = MstAsset(bluetooth_id=mac, asset_name=payload.asset_name)
    db.add(asset)
    await db.commit()
    await db.refresh(asset)
    return {"ok": True, "id": asset.id, "bluetooth_id": asset.bluetooth_id}


@router.put("/{asset_id}")
async def update_asset(asset_id: int, payload: AssetIn, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(MstAsset).where(MstAsset.id == asset_id))
    asset = result.scalars().first()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    asset.asset_name = payload.asset_name
    asset.bluetooth_id = payload.bluetooth_id.upper()
    await db.commit()
    return {"ok": True, "id": asset.id, "asset_name": asset.asset_name}


@router.delete("/{asset_id}")
async def delete_asset(asset_id: int, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(MstAsset).where(MstAsset.id == asset_id))
    asset = result.scalars().first()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    await db.delete(asset)
    await db.commit()
    return {"ok": True}
