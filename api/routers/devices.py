"""
routers/devices.py — admin-issued per-Pi API tokens.

Tokens are random 32-byte url-safe strings. Server stores sha256(token);
the plaintext is returned ONCE at issuance time, never again.

Endpoints:
  POST   /api/devices              — issue (admin only)
  GET    /api/devices?tenant_id=X  — list (admin only)
  DELETE /api/devices/{id}         — revoke (admin only)
"""
import secrets
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from auth import sha256_hex
from database import get_db
from routers.auth import require_admin


router = APIRouter(prefix="/api/devices", tags=["Devices"])


# ── Schemas ─────────────────────────────────────────────────────────────────

class DeviceIssueIn(BaseModel):
    tenant_id: str
    mac: str
    role: str = "scanner"  # "scanner" or "master"


class DeviceOut(BaseModel):
    id: int
    device_id: str
    mac: str
    tenant_id: str
    role: str
    is_active: bool
    last_seen: Optional[str]
    created_at: Optional[str]


class DeviceIssueOut(DeviceOut):
    api_token: str  # plaintext, returned ONCE


def _device_id_for(mac: str) -> str:
    return f"pi-{mac.replace(':', '').lower()}"


# ── Issue ───────────────────────────────────────────────────────────────────

@router.post("", response_model=DeviceIssueOut)
async def issue_device(payload: DeviceIssueIn,
                       admin: dict = Depends(require_admin),
                       db: AsyncSession = Depends(get_db)):
    mac = payload.mac.upper().strip()

    # Validate tenant
    if (await db.execute(
        text("SELECT 1 FROM shared.tenants WHERE tenant_id = :t"),
        {"t": payload.tenant_id}
    )).fetchone() is None:
        raise HTTPException(404, "Tenant not found")

    # Generate plaintext + hash
    token       = secrets.token_urlsafe(32)
    token_hash  = sha256_hex(token)
    device_id   = _device_id_for(mac)

    # Upsert: revoke any prior (mac,tenant) row, then create/replace
    await db.execute(text("""
        UPDATE shared.devices SET is_active = false
         WHERE mac = :m AND tenant_id = :t
    """), {"m": mac, "t": payload.tenant_id})

    row = (await db.execute(text("""
        INSERT INTO shared.devices
            (device_id, mac, tenant_id, role, token_hash, created_by, is_active)
        VALUES (:d, :m, :t, :r, :h, :u, true)
        ON CONFLICT (mac, tenant_id) DO UPDATE SET
            token_hash = EXCLUDED.token_hash,
            role       = EXCLUDED.role,
            is_active  = true,
            created_at = now(),
            created_by = EXCLUDED.created_by
        RETURNING id, device_id, mac, tenant_id, role, is_active, last_seen, created_at
    """), {
        "d": device_id, "m": mac, "t": payload.tenant_id,
        "r": payload.role, "h": token_hash, "u": admin["id"],
    })).fetchone()
    await db.commit()

    return DeviceIssueOut(
        id=row.id, device_id=row.device_id, mac=row.mac,
        tenant_id=row.tenant_id, role=row.role, is_active=row.is_active,
        last_seen=row.last_seen.isoformat() if row.last_seen else None,
        created_at=row.created_at.isoformat() if row.created_at else None,
        api_token=token,
    )


# ── List ────────────────────────────────────────────────────────────────────

@router.get("", response_model=list[DeviceOut])
async def list_devices(tenant_id: Optional[str] = None,
                       admin: dict = Depends(require_admin),
                       db: AsyncSession = Depends(get_db)):
    if tenant_id:
        rows = (await db.execute(text("""
            SELECT id, device_id, mac, tenant_id, role, is_active, last_seen, created_at
              FROM shared.devices WHERE tenant_id = :t
             ORDER BY created_at DESC
        """), {"t": tenant_id})).fetchall()
    else:
        rows = (await db.execute(text("""
            SELECT id, device_id, mac, tenant_id, role, is_active, last_seen, created_at
              FROM shared.devices ORDER BY created_at DESC
        """))).fetchall()
    return [
        DeviceOut(
            id=r.id, device_id=r.device_id, mac=r.mac,
            tenant_id=r.tenant_id, role=r.role, is_active=r.is_active,
            last_seen=r.last_seen.isoformat() if r.last_seen else None,
            created_at=r.created_at.isoformat() if r.created_at else None,
        )
        for r in rows
    ]


# ── Revoke ──────────────────────────────────────────────────────────────────

@router.delete("/{device_id}")
async def revoke_device(device_id: int,
                        admin: dict = Depends(require_admin),
                        db: AsyncSession = Depends(get_db)):
    res = await db.execute(text("""
        UPDATE shared.devices SET is_active = false WHERE id = :i
    """), {"i": device_id})
    await db.commit()
    if res.rowcount == 0:
        raise HTTPException(404, "Device not found")
    return {"ok": True, "revoked": device_id}
