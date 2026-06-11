"""
Runtime registration — master APIs and long-polling.
"""

from fastapi import APIRouter, Depends, HTTPException, Header
from pydantic import BaseModel
from typing import Optional
from sqlalchemy import select, update, text
from sqlalchemy.ext.asyncio import AsyncSession
import asyncio

from database import get_tenant_db, AsyncSessionLocal
from models import MstScanner, MstZoneScanner, MstZone, MstMaster
from events import master_ip_event, notify_master_ip_changed, zone_map_event, ZONE_MAP_VERSION
from routers.auth import get_principal, get_principal_db

router = APIRouter(prefix="/api/runtime", tags=["Runtime"])


def _check_device_tenant(principal: dict, requested: Optional[str]):
    """Defense in depth: device tokens can only access their own tenant_id.
    Service-role tokens are exempt — they may read any tenant's data."""
    if (
        principal.get("type") == "device"
        and principal.get("role") != "service"
        and requested
        and requested != principal.get("tenant_id")
    ):
        raise HTTPException(403, "Device tenant mismatch")


# ─── Schemas ─────────────────────────────────────────────────────────────────

class MasterRegisterIn(BaseModel):
    role: str
    mac: str
    ip: str
    timestamp: str
    tenant_id: Optional[str] = None   # new — sent by master_register.py
    mode: Optional[str] = None        # "local" or "cloud"

class ScannerRegisterIn(BaseModel):
    role: str
    mac: str
    ip: str
    scanner_type: str
    timestamp: str

# ─── Scanner → Zone Map (for master engine) ──────────────────────────────────

async def _fetch_scanner_zone_map(db: AsyncSession):
    stmt = (
        select(MstScanner.mac_id, MstZone.id)
        .join(MstZoneScanner, MstZoneScanner.mst_scanner_id == MstScanner.id)
        .join(MstZone, MstZone.id == MstZoneScanner.mst_zone_id)
    )
    result = await db.execute(stmt)
    mapping = {row.mac_id.upper(): row.id for row in result}
    return {"scanner_zone_map": mapping, "version": ZONE_MAP_VERSION}

@router.get("/scanner-zone-map")
async def get_scanner_zone_map(
    db: AsyncSession = Depends(get_principal_db),
    principal: dict = Depends(get_principal),
):
    """
    Returns {scanner_mac: zone_id} mapping.
    The master engine calls this to instantly load mappings.
    Schema is derived from the authenticated principal's tenant_id — no header trust.
    """
    return await _fetch_scanner_zone_map(db)

@router.get("/scanner-zone-map/watch")
async def watch_scanner_zone_map(
    version: int = 0,
    principal: dict = Depends(get_principal),
):
    """
    Long-polling endpoint for the Master script.
    Hangs until an atomic commit to zones happens, then returns the new map.
    If the requested version != current ZONE_MAP_VERSION, it returns immediately.
    DB session is released before the wait — no connection held during the 60s poll.
    Schema is derived from the authenticated principal's tenant_id — no header trust.
    """
    tid = principal.get("tenant_id")
    schema = f"t_{tid.lower()}" if tid else "public"

    async def fetch():
        async with AsyncSessionLocal() as db:
            await db.execute(text(f"SET search_path TO {schema}, public"))
            try:
                return await _fetch_scanner_zone_map(db)
            finally:
                try:
                    await db.execute(text("SET search_path TO public"))
                except Exception:
                    pass

    if version != ZONE_MAP_VERSION:
        return await fetch()

    try:
        await asyncio.wait_for(zone_map_event.wait(), timeout=60.0)
    except asyncio.TimeoutError:
        pass

    return await fetch()


# ─── Master ──────────────────────────────────────────────────────────────────

@router.post("/master")
async def register_master(payload: MasterRegisterIn,
                          db: AsyncSession = Depends(get_principal_db),
                          principal: dict = Depends(get_principal)):
    """
    Master Node registers its IP and MAC here upon boot or IP change.
    Now also accepts tenant_id and mode for multi-tenant local master setups.
    """
    _check_device_tenant(principal, payload.tenant_id)
    mac = payload.mac.upper()

    result = await db.execute(select(MstMaster).where(MstMaster.mac == mac))
    existing = result.scalars().first()

    ip_changed = False

    if existing:
        if existing.ip != payload.ip:
            ip_changed = True
            existing.ip = payload.ip
        # Update tenant_id and mode if provided
        if payload.tenant_id:
            existing.name = f"Master Pi ({payload.tenant_id})"
    else:
        ip_changed = True
        name = f"Master Pi ({payload.tenant_id})" if payload.tenant_id else "Master Pi"
        new_master = MstMaster(mac=mac, ip=payload.ip, name=name)
        db.add(new_master)

    await db.flush()
    await db.commit()

    if ip_changed:
        await notify_master_ip_changed()

    return {
        "ok": True,
        "master_ip": payload.ip,
        "tenant_id": payload.tenant_id or "",
    }

@router.get("/master")
async def get_master(
    db: AsyncSession = Depends(get_principal_db),
    principal: dict = Depends(get_principal),
):
    """
    Returns the current Master IP and tenant_id.
    Android app calls this after login to auto-fill remoteHost.
    Schema is derived from the authenticated principal's tenant_id — no header trust.
    """
    result = await db.execute(select(MstMaster).order_by(MstMaster.id.desc()).limit(1))
    master = result.scalars().first()
    if not master:
        raise HTTPException(status_code=404, detail="Master not registered yet")
    # Extract tenant_id from name field (format: "Master Pi (HQTJAC)")
    tenant_id = ""
    if master.name and "(" in master.name:
        tenant_id = master.name.split("(")[-1].rstrip(")")
    return {"ok": True, "master_ip": master.ip, "tenant_id": tenant_id}


@router.get("/master/watch")
async def watch_master_ip(
    current_ip: str,
    principal: dict = Depends(get_principal),
):
    """
    Long-polling endpoint for Scanner scripts and Android app.
    Returns instantly if the IP in DB differs from current_ip.
    Otherwise hangs until the Master IP changes in the DB.
    DB session is released before the wait — no connection held during the 60s poll.
    Schema is derived from the authenticated principal's tenant_id — no header trust.
    """
    tid = principal.get("tenant_id")
    schema = f"t_{tid.lower()}" if tid else "public"

    async def fetch_master():
        async with AsyncSessionLocal() as db:
            await db.execute(text(f"SET search_path TO {schema}, public"))
            try:
                result = await db.execute(select(MstMaster).order_by(MstMaster.id.desc()).limit(1))
                return result.scalars().first()
            finally:
                try:
                    await db.execute(text("SET search_path TO public"))
                except Exception:
                    pass

    master = await fetch_master()
    if master and master.ip != current_ip:
        return {"ok": True, "master_ip": master.ip}

    try:
        await asyncio.wait_for(master_ip_event.wait(), timeout=60.0)
    except asyncio.TimeoutError:
        pass

    master = await fetch_master()
    if not master:
        raise HTTPException(status_code=404, detail="Master disconnected")

    return {"ok": True, "master_ip": master.ip}

# ─── Scanner ─────────────────────────────────────────────────────────────────

@router.post("/scanner")
async def register_scanner(payload: ScannerRegisterIn,
                           db: AsyncSession = Depends(get_principal_db),
                           principal: dict = Depends(get_principal)):
    """
    Scanners can hit this to log their runtime boot.
    It returns the master IP immediately to serve as a bootstrap.
    Schema is derived from the authenticated principal's tenant_id — no header trust.
    """
    return await get_master(db, principal)
