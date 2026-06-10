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
    """Returns all registered assets enriched with zone name, status, and health."""
    try:
        from datetime import timezone as _tz
        rows = (await db.execute(text("""
            SELECT
                a.id,
                a.bluetooth_id    AS mac,
                COALESCE(a.asset_name, a.bluetooth_id) AS name,
                a.current_zone_id AS zone_id,
                z.zone_name,
                a.last_movement_dt,
                a.extra
            FROM mst_asset a
            LEFT JOIN mst_zone z ON z.id = a.current_zone_id
            ORDER BY a.id
        """))).fetchall()

        now = datetime.now(_tz.utc)
        out = []
        for r in rows:
            extra = r.extra or {}
            if isinstance(extra, str):
                try:
                    import json as _json; extra = _json.loads(extra)
                except Exception:
                    extra = {}

            is_alive = extra.get("is_alive", True)
            battery  = extra.get("battery")

            # Derive status from last_movement_dt age
            if r.last_movement_dt:
                age_sec = (now - r.last_movement_dt.replace(tzinfo=_tz.utc)
                           if r.last_movement_dt.tzinfo is None
                           else (now - r.last_movement_dt)).total_seconds()
                if not is_alive or age_sec > 300:
                    status = "offline"
                elif age_sec > 60:
                    status = "idle"
                else:
                    status = "active"
                # Human-readable relative time
                if age_sec < 60:
                    rel = f"{int(age_sec)}s ago"
                elif age_sec < 3600:
                    rel = f"{int(age_sec//60)}m ago"
                else:
                    rel = f"{int(age_sec//3600)}h ago"
            else:
                status = "offline"
                rel    = "never"

            out.append({
                "id":               str(r.id),
                "mac":              r.mac,
                "name":             r.name,
                "shape":            "oval",
                "status":           status,
                "battery":          battery,
                "rssi":             extra.get("deciding_rssi") or extra.get("rssi"),
                "last_seen":        r.last_movement_dt.isoformat() if r.last_movement_dt else None,
                "last_seen_relative": rel,
                "zone_id":          str(r.zone_id) if r.zone_id else None,
                "zone_name":        r.zone_name or "Unknown",
            })
        return out
    except Exception as e:
        print(f"[API ERROR] Current Status Fetch: {e}")
        return []


@router.get("/health/summary")
async def health_summary(db: AsyncSession = Depends(get_tenant_db),
                         user: dict = Depends(require_tenant_match)):
    """Quick counts for the dashboard header."""
    try:
        sc = (await db.execute(text("""
            SELECT
                COUNT(*)                                          AS total,
                COUNT(*) FILTER (WHERE scanner_status = 'active') AS online,
                COUNT(*) FILTER (WHERE scanner_status = 'offline') AS offline
            FROM mst_scanner
        """))).fetchone()

        bc = (await db.execute(text("""
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE (extra->>'is_alive')::boolean IS NOT FALSE) AS alive,
                COUNT(*) FILTER (WHERE (extra->>'is_alive')::boolean = FALSE)     AS dead,
                COUNT(*) FILTER (WHERE (extra->>'battery') IS NOT NULL
                                  AND (extra->>'battery')::int < 20
                                  AND (extra->>'battery')::int >= 0) AS low_battery
            FROM mst_asset
        """))).fetchone()

        return {
            "scanners": {"total": sc.total, "online": sc.online, "offline": sc.offline},
            "beacons":  {"total": bc.total, "alive": bc.alive, "dead": bc.dead, "low_battery": bc.low_battery},
        }
    except Exception as e:
        print(f"[API ERROR] Health Summary: {e}")
        return {"scanners": {"total": 0, "online": 0, "offline": 0},
                "beacons":  {"total": 0, "alive": 0, "dead": 0, "low_battery": 0}}


@router.get("/notifications")
async def get_notifications(user: dict = Depends(require_tenant_match)):
    """Stub — returns empty notifications list."""
    return {"total": 0, "unread": 0, "notifications": []}


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
