"""
Runtime registration — master APIs and long-polling.
"""

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession
import asyncio

from database import get_db
from models import MstScanner, MstZoneScanner, MstZone, MstMaster
from events import master_ip_event, notify_master_ip_changed, zone_map_event, ZONE_MAP_VERSION

router = APIRouter(prefix="/api/runtime", tags=["Runtime"])

# ─── Schemas ─────────────────────────────────────────────────────────────────

class MasterRegisterIn(BaseModel):
    role: str
    mac: str
    ip: str
    timestamp: str

class ScannerRegisterIn(BaseModel):
    role: str
    mac: str
    ip: str
    scanner_type: str
    timestamp: str

# ─── Scanner → Zone Map (for master engine) ──────────────────────────────────

@router.get("/scanner-zone-map")
async def get_scanner_zone_map(db: AsyncSession = Depends(get_db)):
    """
    Returns {scanner_mac: zone_id} mapping.
    The master engine calls this to instantly load mappings.
    """
    stmt = (
        select(MstScanner.mac_id, MstZone.id)
        .join(MstZoneScanner, MstZoneScanner.mst_scanner_id == MstScanner.id)
        .join(MstZone, MstZone.id == MstZoneScanner.mst_zone_id)
    )
    result = await db.execute(stmt)
    mapping = {row.mac_id.upper(): row.id for row in result}
    return {"scanner_zone_map": mapping, "version": ZONE_MAP_VERSION}

@router.get("/scanner-zone-map/watch")
async def watch_scanner_zone_map(version: int = 0, db: AsyncSession = Depends(get_db)):
    """
    Long-polling endpoint for the Master script.
    Hangs until an atomic commit to zones happens, then returns the new map.
    If the requested version != current ZONE_MAP_VERSION, it returns immediately.
    """
    if version != ZONE_MAP_VERSION:
        return await get_scanner_zone_map(db)

    try:
        await asyncio.wait_for(zone_map_event.wait(), timeout=60.0)
    except asyncio.TimeoutError:
        pass # If no event triggered in 60s, just return the current map safely to keep connection alive
        
    return await get_scanner_zone_map(db)


# ─── Master ──────────────────────────────────────────────────────────────────

@router.post("/master")
async def register_master(payload: MasterRegisterIn, db: AsyncSession = Depends(get_db)):
    """
    Master Node registers its IP and MAC here upon boot or IP change.
    """
    mac = payload.mac.upper()
    
    result = await db.execute(select(MstMaster).where(MstMaster.mac == mac))
    existing = result.scalars().first()
    
    ip_changed = False
    
    if existing:
        if existing.ip != payload.ip:
            ip_changed = True
            existing.ip = payload.ip
    else:
        ip_changed = True
        new_master = MstMaster(mac=mac, ip=payload.ip, name="Master Pi")
        db.add(new_master)
        
    await db.commit()
    
    if ip_changed:
        await notify_master_ip_changed()

    return {
        "ok": True,
        "master_ip": payload.ip
    }

@router.get("/master")
async def get_master(db: AsyncSession = Depends(get_db)):
    """
    Returns the current Master IP.
    """
    result = await db.execute(select(MstMaster).order_by(MstMaster.id.desc()).limit(1))
    master = result.scalars().first()
    if not master:
        raise HTTPException(status_code=404, detail="Master not registered yet")
    return {"ok": True, "master_ip": master.ip}


@router.get("/master/watch")
async def watch_master_ip(current_ip: str, db: AsyncSession = Depends(get_db)):
    """
    Long-polling endpoint for Scanner scripts.
    Returns instantly if the IP in DB differs from current_ip.
    Otherwise hangs until the Master IP changes in the DB.
    """
    # Check immediately
    result = await db.execute(select(MstMaster).order_by(MstMaster.id.desc()).limit(1))
    master = result.scalars().first()
    if master and master.ip != current_ip:
        return {"ok": True, "master_ip": master.ip}
        
    # Wait for change event
    try:
        await asyncio.wait_for(master_ip_event.wait(), timeout=60.0)
    except asyncio.TimeoutError:
        pass
        
    # Fetch latest
    result = await db.execute(select(MstMaster).order_by(MstMaster.id.desc()).limit(1))
    master = result.scalars().first()
    if not master:
        raise HTTPException(status_code=404, detail="Master disconnected")
        
    return {"ok": True, "master_ip": master.ip}

# ─── Scanner ─────────────────────────────────────────────────────────────────

@router.post("/scanner")
async def register_scanner(payload: ScannerRegisterIn, db: AsyncSession = Depends(get_db)):
    """
    Scanners can hit this to log their runtime boot. 
    It returns the master IP immediately to serve as a bootstrap.
    """
    # Simply return the master IP
    return await get_master(db)
