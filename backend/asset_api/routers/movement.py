"""
Movement endpoints — zone-change events from master + live view + history.
"""

from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession
from datetime import datetime

from database import get_db
from models import MovementLog, MstAsset
from schemas import MovementIn, MovementOut

router = APIRouter(prefix="/api", tags=["Movement"])


# ─── Live View ───────────────────────────────────────────────────────────────

@router.get("/assets/current")
async def get_current_status(db: AsyncSession = Depends(get_db)):
    """
    Returns the most recent location of every registered beacon.
    Used by UI Live View.
    """
    try:
        stmt = select(MstAsset).where(MstAsset.current_zone_id.is_not(None))
        result = await db.execute(stmt)
        assets = result.scalars().all()

        return [
            {
                "mac": a.bluetooth_id,
                "zone": a.current_zone_id,
                "last_seen": a.last_movement_dt.isoformat() if a.last_movement_dt else None,
                "rssi": a.extra.get("deciding_rssi", -99)
                        if isinstance(a.extra, dict) else -99
            }
            for a in assets
        ]
    except Exception as e:
        print(f"[API ERROR] Current Status Fetch: {e}")
        return []


# ─── History ─────────────────────────────────────────────────────────────────

@router.get("/assets/history")
async def get_history(db: AsyncSession = Depends(get_db)):
    """
    Returns the last 50 zone-change events.
    Used by UI Ledger.
    """
    try:
        stmt = (
            select(MovementLog)
            .order_by(MovementLog.timestamp_movement.desc())
            .limit(50)
        )
        result = await db.execute(stmt)
        logs = result.scalars().all()

        return [
            {
                "id": l.id,
                "mac": l.bluetooth_id,
                "from_zone": l.from_zone_id,
                "to_zone": l.to_zone_id,
                "timestamp": l.timestamp_movement.isoformat(),
            }
            for l in logs
        ]
    except Exception as e:
        print(f"[API ERROR] History Fetch: {e}")
        return []


# ─── Zone-Change Event ──────────────────────────────────────────────────────

@router.post("/asset/movement", response_model=MovementOut)
async def asset_movement(
    payload: MovementIn,
    db: AsyncSession = Depends(get_db)
):
    """
    Receives CONFIRMED zone-change events from master.
    Asset filtering + DB persistence happens here.
    """

    # Parse timestamp
    try:
        ts = datetime.fromisoformat(payload.timestamp)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid timestamp format")

    asset_mac = payload.asset_mac.upper()

    # Asset filtering (API level)
    stmt = select(MstAsset).where(MstAsset.bluetooth_id == asset_mac)
    result = await db.execute(stmt)
    asset = result.scalars().first()

    if not asset:
        # Unknown beacon → ignore silently
        return {"ok": True, "detail": "asset not registered, ignored"}

    # Insert movement log
    try:
        movement = MovementLog(
            bluetooth_id=asset_mac,
            from_zone_id=payload.from_zone_id,
            to_zone_id=payload.to_zone_id,
            deciding_rssi=payload.deciding_rssi,
            timestamp_movement=ts,
        )
        db.add(movement)
        await db.commit()
        await db.refresh(movement)

    except SQLAlchemyError as e:
        await db.rollback()
        print("DB ERROR:", str(e))
        raise HTTPException(status_code=500, detail="movement_log insert failed")

    # Update mst_asset (current zone)
    try:
        if payload.state == "EXIT":
            asset.current_zone_id = None
        else:
            asset.current_zone_id = payload.to_zone_id
            asset.last_movement_dt = ts

        await db.commit()

    except SQLAlchemyError as e:
        await db.rollback()
        print("DB ERROR:", str(e))
        raise HTTPException(status_code=500, detail="mst_asset update failed")

    return {
        "ok": True,
        "detail": f"zone updated → {payload.to_zone_id}"
    }
