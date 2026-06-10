"""
Movement endpoints — zone-change events from master + live view + history.
"""

from fastapi import APIRouter, HTTPException, Depends
from fastapi_limiter.depends import RateLimiter
from sqlalchemy import select, text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession
from datetime import datetime

from database import get_smart_db as get_tenant_db
from models import MovementLog, MstAsset
from schemas import MovementIn, MovementOut
from routers.auth import get_principal, require_user, require_device, require_tenant_match

router = APIRouter(prefix="/api", tags=["Movement"])


# ─── Live View ───────────────────────────────────────────────────────────────

@router.get("/assets/current")
async def get_current_status(db: AsyncSession = Depends(get_tenant_db),
                             user: dict = Depends(require_tenant_match)):
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
async def get_history(
    db: AsyncSession = Depends(get_tenant_db),
    user: dict = Depends(require_tenant_match),
    limit: int = 100,
    start_date: str | None = None,
):
    """
    Returns zone-change events enriched with asset names, zone names, and type.
    Used by the UI Logs page.
    """
    try:
        where = "WHERE 1=1"
        params: dict = {"limit": min(limit, 500)}
        if start_date:
            where += " AND ml.timestamp_movement >= :start_date::date"
            params["start_date"] = start_date

        rows = (await db.execute(
            text(f"""
                SELECT
                    ml.id,
                    ml.bluetooth_id                         AS mac,
                    ma.id                                   AS asset_id,
                    COALESCE(ma.asset_name, ml.bluetooth_id) AS asset_name,
                    ml.from_zone_id,
                    fz.zone_name                            AS from_zone,
                    ml.to_zone_id,
                    tz.zone_name                            AS to_zone,
                    ml.deciding_rssi                        AS rssi,
                    ml.timestamp_movement                   AS timestamp,
                    CASE
                        WHEN ml.from_zone_id IS NULL THEN 'enter'
                        WHEN ml.to_zone_id   IS NULL THEN 'exit'
                        ELSE 'move'
                    END                                     AS type
                FROM movement_log ml
                LEFT JOIN mst_asset ma ON ma.bluetooth_id = ml.bluetooth_id
                LEFT JOIN mst_zone   fz ON fz.id = ml.from_zone_id
                LEFT JOIN mst_zone   tz ON tz.id = ml.to_zone_id
                {where}
                ORDER BY ml.timestamp_movement DESC
                LIMIT :limit
            """),
            params,
        )).fetchall()

        return [
            {
                "id":          str(r.id),
                "mac":         r.mac,
                "asset_id":    str(r.asset_id) if r.asset_id else None,
                "asset_name":  r.asset_name,
                "from_zone_id":str(r.from_zone_id) if r.from_zone_id else None,
                "from_zone":   r.from_zone or "",
                "to_zone_id":  str(r.to_zone_id) if r.to_zone_id else None,
                "to_zone":     r.to_zone or "",
                "rssi":        float(r.rssi) if r.rssi is not None else None,
                "timestamp":   r.timestamp.isoformat(),
                "type":        r.type,
            }
            for r in rows
        ]
    except Exception as e:
        print(f"[API ERROR] History Fetch: {e}")
        return []


# ─── Zone-Change Event ──────────────────────────────────────────────────────

@router.post("/asset/movement", response_model=MovementOut,
             dependencies=[Depends(RateLimiter(times=120, seconds=60))])
async def asset_movement(
    payload: MovementIn,
    db: AsyncSession = Depends(get_tenant_db),
    principal: dict = Depends(require_device()),
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

    # Insert movement log + update asset in one transaction
    try:
        movement = MovementLog(
            bluetooth_id=asset_mac,
            from_zone_id=payload.from_zone_id,
            to_zone_id=payload.to_zone_id,
            deciding_rssi=payload.deciding_rssi,
            timestamp_movement=ts,
        )
        db.add(movement)

        if payload.state == "EXIT":
            asset.current_zone_id = None
        else:
            asset.current_zone_id = payload.to_zone_id
            asset.last_movement_dt = ts

        await db.flush()
        await db.commit()

    except SQLAlchemyError as e:
        await db.rollback()
        print("DB ERROR:", str(e))
        raise HTTPException(status_code=500, detail="movement insert failed")

    return {
        "ok": True,
        "detail": f"zone updated → {payload.to_zone_id}"
    }
