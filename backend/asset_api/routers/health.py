"""
health.py — Bulk health write endpoints called by master.py every 5 minutes.

POST /api/health/scanners/bulk  — update scanner last_heartbeat in mst_scanner
POST /api/health/beacons/bulk   — update beacon battery + last_seen in mst_asset

These use raw SQL so they work even if the SQLAlchemy models haven't been updated
with the newer columns (last_heartbeat, extra).
"""

from __future__ import annotations

import json
from datetime import datetime, timezone, timedelta
from typing import Optional, List

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db

router = APIRouter(prefix="/api/health", tags=["Health Write"])


# ─────────────────────────────── Schemas ──────────────────────────────────────

class ScannerHealthItem(BaseModel):
    scanner_mac: str
    zone_id: Optional[int] = None
    is_online: bool
    last_seen_ago_sec: Optional[int] = None   # seconds since last MQTT message


class BeaconHealthItem(BaseModel):
    asset_mac: str
    battery: Optional[int] = None   # 0-100, or 101 = USB power, None = unknown
    last_seen_ago_sec: int          # seconds since last MQTT sighting
    is_alive: bool


# ───────────────────────────── POST /health/scanners/bulk ─────────────────────

@router.post("/scanners/bulk")
async def bulk_update_scanner_health(
    items: List[ScannerHealthItem],
    db: AsyncSession = Depends(get_db),
):
    """
    Called by master.py every 5 min.
    Updates mst_scanner.last_heartbeat for each scanner MAC.
    Uses raw SQL to safely handle the column regardless of ORM model state.
    """
    now = datetime.now(timezone.utc)
    updated = 0

    for item in items:
        mac = item.scanner_mac.upper()

        # Compute last_heartbeat from ago seconds (avoids clock skew between master and server)
        if item.last_seen_ago_sec is not None:
            last_hb = now - timedelta(seconds=item.last_seen_ago_sec)
        elif item.is_online:
            last_hb = now
        else:
            last_hb = None

        # Check column exists first (graceful degradation)
        try:
            if last_hb is not None:
                await db.execute(
                    text(
                        "UPDATE mst_scanner SET last_heartbeat = :ts "
                        "WHERE UPPER(mac_id) = :mac"
                    ),
                    {"ts": last_hb, "mac": mac},
                )
                updated += 1
        except Exception:
            # Column doesn't exist yet — skip silently
            pass

    await db.commit()
    return {"ok": True, "updated": updated, "total": len(items)}


# ───────────────────────────── POST /health/beacons/bulk ──────────────────────

@router.post("/beacons/bulk")
async def bulk_update_beacon_health(
    items: List[BeaconHealthItem],
    db: AsyncSession = Depends(get_db),
):
    """
    Called by master.py every 5 min.
    Updates mst_asset.extra JSON with battery and last_seen fields.
    Merges with existing extra data — does NOT overwrite other fields.
    """
    now = datetime.now(timezone.utc)
    updated = 0

    for item in items:
        mac = item.asset_mac.upper()

        # Calculate actual last_seen timestamp
        last_seen_ts = now - timedelta(seconds=item.last_seen_ago_sec)

        try:
            # Fetch current extra JSON to merge into it
            result = await db.execute(
                text(
                    "SELECT id, extra FROM mst_asset WHERE UPPER(bluetooth_id) = :mac"
                ),
                {"mac": mac},
            )
            row = result.fetchone()
            if not row:
                continue  # Unknown beacon — skip

            asset_id = row[0]
            existing_extra = row[1] or {}
            if isinstance(existing_extra, str):
                try:
                    existing_extra = json.loads(existing_extra)
                except Exception:
                    existing_extra = {}

            # Merge: only update battery if we actually have a value
            if item.battery is not None:
                existing_extra["battery"] = item.battery

            existing_extra["last_seen"] = last_seen_ts.isoformat()
            existing_extra["is_alive"] = item.is_alive

            await db.execute(
                text(
                    "UPDATE mst_asset SET extra = :extra WHERE id = :id"
                ),
                {"extra": json.dumps(existing_extra), "id": asset_id},
            )
            updated += 1

        except Exception as e:
            print(f"[HEALTH-API] Beacon update failed for {mac}: {e}", flush=True)
            continue

    await db.commit()
    return {"ok": True, "updated": updated, "total": len(items)}
