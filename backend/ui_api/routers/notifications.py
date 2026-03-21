"""
notifications.py — Dynamic notification feed.

Notifications are derived on-the-fly from:
  1. Offline scanners  (last_heartbeat > 5 min ago)
  2. Dead beacons      (last_seen > 10 min ago)
  3. Low-battery beacons (battery < 20%)

Read receipts are stored in an in-memory dict (survives for the lifetime
of the server process). A simple JSON file persists them across restarts.
"""
import json
import hashlib
from pathlib import Path
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from datetime import datetime, timezone

from database import get_db
from routers.health import get_scanner_health, get_beacon_health

router = APIRouter(prefix="/notifications", tags=["Notifications"])

_READ_RECEIPTS_FILE = Path(__file__).parent.parent / "logs" / "read_receipts.json"
_SCANNER_ONLINE_SEC = 300
_BEACON_ALIVE_SEC   = 600
_LOW_BATTERY_PCT    = 20


def _load_receipts() -> set:
    try:
        return set(json.loads(_READ_RECEIPTS_FILE.read_text()))
    except Exception:
        return set()


def _save_receipts(receipts: set):
    try:
        _READ_RECEIPTS_FILE.parent.mkdir(parents=True, exist_ok=True)
        _READ_RECEIPTS_FILE.write_text(json.dumps(list(receipts)))
    except Exception:
        pass


def _stable_id(*parts) -> str:
    """Deterministic notification ID so the frontend can remember read state."""
    return hashlib.md5("-".join(str(p) for p in parts).encode()).hexdigest()[:12]


# ---------------------------------------------- GET /notifications --
@router.get("")
async def get_notifications(db: AsyncSession = Depends(get_db)):
    """
    Returns a dynamically generated list of alerts sorted by priority.
    Each notification has a stable id so the UI can mark it as read.
    """
    read = _load_receipts()
    now = datetime.now(timezone.utc)
    items = []

    # ── Scanner offline alerts ──────────────────────────────────────
    scanners = await get_scanner_health(db)
    for s in scanners:
        if s["status"] == "online":
            continue

        nid = _stable_id("scanner_offline", s["id"])
        last_hb = s["last_heartbeat"]
        if last_hb:
            dt = datetime.fromisoformat(last_hb)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            diff_min = int((now - dt).total_seconds() // 60)
            msg = f"{s['name']} in {s['zone_name']} has been offline for {diff_min} min"
            priority = "high" if diff_min > 60 else "medium"
        else:
            msg = f"{s['name']} in {s['zone_name']} — no heartbeat recorded yet"
            priority = "medium"

        items.append({
            "id": nid,
            "title": "Scanner Offline",
            "message": msg,
            "type": "alert",
            "priority": priority,
            "read": nid in read,
            "timestamp": last_hb or now.isoformat(),
            "meta": {"scanner_id": s["id"]},
        })

    # ── Dead beacon alerts ──────────────────────────────────────────
    beacons = await get_beacon_health(db)
    for b in beacons:
        if b["is_alive"]:
            continue

        nid = _stable_id("beacon_dead", b["id"])
        last_seen = b["last_seen"]
        if last_seen:
            dt = datetime.fromisoformat(last_seen)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            diff_min = int((now - dt).total_seconds() // 60)
            msg = f"{b['name']} last seen {diff_min} min ago — may be offline or out of range"
            priority = "high" if diff_min > 60 else "medium"
        else:
            msg = f"{b['name']} — never detected by any scanner"
            priority = "medium"

        items.append({
            "id": nid,
            "title": "Beacon Not Detected",
            "message": msg,
            "type": "alert",
            "priority": priority,
            "read": nid in read,
            "timestamp": last_seen or now.isoformat(),
            "meta": {"asset_id": b["id"]},
        })

    # ── Low battery beacon alerts ───────────────────────────────────
    for b in beacons:
        if not b["low_battery"]:
            continue

        nid = _stable_id("low_battery", b["id"])
        bat = b["battery"]
        items.append({
            "id": nid,
            "title": "Low Battery",
            "message": f"{b['name']} battery at {bat}% — replace soon",
            "type": "alert",
            "priority": "high" if bat < 10 else "medium",
            "read": nid in read,
            "timestamp": now.isoformat(),
            "meta": {"asset_id": b["id"]},
        })

    # Sort: unread first, then by priority (high > medium > low)
    priority_order = {"high": 0, "medium": 1, "low": 2}
    items.sort(key=lambda n: (n["read"], priority_order.get(n["priority"], 3)))

    return {
        "total": len(items),
        "unread": sum(1 for n in items if not n["read"]),
        "notifications": items,
    }


# --------------------------------- POST /notifications/{id}/read --
@router.post("/{notification_id}/read")
async def mark_as_read(notification_id: str, db: AsyncSession = Depends(get_db)):
    """Mark a notification as read (persisted to disk)."""
    read = _load_receipts()
    read.add(notification_id)
    _save_receipts(read)
    return {"ok": True, "id": notification_id}


# --------------------------------- POST /notifications/read-all --
@router.post("/read-all")
async def mark_all_read(db: AsyncSession = Depends(get_db)):
    """Mark all current notifications as read."""
    notifications_resp = await get_notifications(db)
    read = _load_receipts()
    for n in notifications_resp["notifications"]:
        read.add(n["id"])
    _save_receipts(read)
    return {"ok": True, "marked": len(notifications_resp["notifications"])}
