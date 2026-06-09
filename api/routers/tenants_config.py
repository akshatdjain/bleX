"""
Tenant config endpoint — single source of truth for Pi/Android provisioning.

GET /api/tenants/{tenant_id}/config
  → 200 {
      tenant_id, mode, mqtt_host, mqtt_port, use_tls,
      mqtt_username, mqtt_password,
      tablet_fallback: { host, port } | null
    }

Returned config is what the Android app POSTs to the Pi's provisioner_service,
which writes it verbatim into /etc/blex/blex.env on the Pi.

Source of truth: shared.tenants table. Mode and broker creds are tenant-level.
"""
import os
from fastapi import APIRouter, Depends, HTTPException
from fastapi_limiter.depends import RateLimiter
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from pydantic import BaseModel
from typing import Optional

from database import get_db

router = APIRouter(prefix="/api/tenants", tags=["Tenants"])


# ── Defaults (used when tenant row has NULLs / for cloud broker host) ────────

CLOUD_MQTT_HOST = os.getenv("CLOUD_MQTT_HOST", "sigmatic-asc.tech")
CLOUD_MQTT_PORT = int(os.getenv("CLOUD_MQTT_PORT", "8883"))


# ── Schemas ──────────────────────────────────────────────────────────────────

class TabletFallback(BaseModel):
    host: str
    port: int


class TenantConfigOut(BaseModel):
    tenant_id: str
    mode: str
    mqtt_host: str
    mqtt_port: int
    use_tls: bool
    mqtt_username: Optional[str]
    mqtt_password: Optional[str]
    tablet_fallback: Optional[TabletFallback]


# ── Endpoint ─────────────────────────────────────────────────────────────────

@router.get("/{tenant_id}/config", response_model=TenantConfigOut,
            dependencies=[Depends(RateLimiter(times=10, seconds=60))])
async def get_tenant_config(tenant_id: str, db: AsyncSession = Depends(get_db)):
    """Return the full provisioning config for a tenant."""
    row = (await db.execute(
        text(
            "SELECT tenant_id, mode, mqtt_username, mqtt_password, "
            "       tablet_host, tablet_port "
            "FROM shared.tenants WHERE tenant_id = :tid"
        ),
        {"tid": tenant_id},
    )).fetchone()

    if row is None:
        raise HTTPException(status_code=404, detail="Tenant not found")

    mode = row.mode

    # Cloud mode: scanner connects directly to the cloud broker.
    # Local mode: scanner connects to the master Pi's local broker (broker IP
    #             resolved at boot via /api/runtime/master); we still return
    #             the cloud host as a logical placeholder.
    if mode == "local":
        mqtt_host = "127.0.0.1"  # placeholder; scanner_boot resolves real IP
        mqtt_port = 1883
        use_tls   = False
    else:
        mqtt_host = CLOUD_MQTT_HOST
        mqtt_port = CLOUD_MQTT_PORT
        use_tls   = True

    tablet_fallback = None
    if row.tablet_host:
        tablet_fallback = TabletFallback(
            host=row.tablet_host,
            port=int(row.tablet_port or 1883),
        )

    return TenantConfigOut(
        tenant_id=row.tenant_id,
        mode=mode,
        mqtt_host=mqtt_host,
        mqtt_port=mqtt_port,
        use_tls=use_tls,
        mqtt_username=row.mqtt_username,
        mqtt_password=row.mqtt_password,
        tablet_fallback=tablet_fallback,
    )
