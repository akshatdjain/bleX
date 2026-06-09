"""
Tenant management endpoints.

Public (no auth):
  POST /api/tenants/register         — Android first-launch: create tenant + schema
  GET  /api/tenants/{tenant_id}      — Verify tenant exists

Internal (master.py):
  GET  /api/tenants/active           — List all active tenant IDs (used by master pre-load)

Tenant (user JWT, own tenant only):
  GET  /api/tenants/{tenant_id}/config — Full provisioning config for Pi/Android

Admin panel:
  GET  /api/tenants                  — List all tenants with stats
  GET  /api/tenants/{id}/stats       — Per-tenant stats (scanners, assets, zones, events)
  PATCH /api/tenants/{id}            — Update status / tier / limits / metadata
  GET  /api/tenants/{id}/events      — Audit log
  POST /api/tenants/{id}/events      — Append audit event (admin action)
"""

import os
from fastapi import APIRouter, Depends, HTTPException
from fastapi_limiter.depends import RateLimiter
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from pydantic import BaseModel
from typing import Optional, Any
import random
import string
import json

from database import get_db
from routers.auth import require_admin, require_user

CLOUD_MQTT_HOST = os.getenv("CLOUD_MQTT_HOST", "sigmatic-asc.tech")
CLOUD_MQTT_PORT = int(os.getenv("CLOUD_MQTT_PORT", "8883"))

router = APIRouter(prefix="/api/tenants", tags=["Tenants"])

_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

def _generate_tenant_id(length: int = 6) -> str:
    return "".join(random.choices(_CHARS, k=length))


# ── Schemas ───────────────────────────────────────────────────────────────────

class TenantRegisterIn(BaseModel):
    name: str
    contact_email: Optional[str] = None
    plan: Optional[str] = "demo"

class TenantOut(BaseModel):
    tenant_id: str
    name: str
    mqtt_prefix: str

class TenantDetail(BaseModel):
    tenant_id: str
    name: str
    mqtt_prefix: str
    plan: str
    tier: str
    master_tier: str
    status: str
    db_schema: Optional[str]
    scanner_limit: int
    asset_limit: int
    contact_email: Optional[str]
    created_at: str
    metadata: dict

class TenantUpdateIn(BaseModel):
    name: Optional[str] = None
    status: Optional[str] = None          # active | suspended | churned
    plan: Optional[str] = None
    tier: Optional[str] = None
    master_tier: Optional[str] = None     # shared | dedicated
    scanner_limit: Optional[int] = None
    asset_limit: Optional[int] = None
    contact_email: Optional[str] = None
    metadata: Optional[dict] = None
    # BleX deployment fields (set in admin panel; consumed by Pi provisioner)
    mode: Optional[str] = None             # local | cloud
    tablet_host: Optional[str] = None
    tablet_port: Optional[int] = None
    mqtt_username: Optional[str] = None
    mqtt_password: Optional[str] = None

class TenantEventIn(BaseModel):
    event_type: str
    actor: Optional[str] = "system"
    payload: Optional[dict] = {}

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


# ── Register (Android first launch) ──────────────────────────────────────────

@router.post("/register", response_model=TenantOut)
async def register_tenant(payload: TenantRegisterIn, db: AsyncSession = Depends(get_db)):
    for _ in range(5):
        tenant_id = _generate_tenant_id()
        result = await db.execute(
            text("SELECT tenant_id FROM shared.tenants WHERE tenant_id = :tid"),
            {"tid": tenant_id}
        )
        if result.fetchone() is None:
            break
    else:
        raise HTTPException(status_code=500, detail="Could not generate unique tenant ID")

    schema       = f"t_{tenant_id.lower()}"
    mqtt_prefix  = f"ble/{tenant_id}"

    stmts = [
        f"CREATE SCHEMA IF NOT EXISTS {schema}",
        f"""CREATE TABLE IF NOT EXISTS {schema}.mst_zone (
            id SERIAL PRIMARY KEY, zone_name TEXT NOT NULL,
            description TEXT, dimension JSON)""",
        f"""CREATE TABLE IF NOT EXISTS {schema}.mst_scanner (
            id SERIAL PRIMARY KEY, mac_id TEXT NOT NULL, name TEXT, type TEXT,
            created_at TIMESTAMPTZ DEFAULT NOW(), last_heartbeat TIMESTAMPTZ,
            scanner_status TEXT DEFAULT 'offline')""",
        f"""CREATE TABLE IF NOT EXISTS {schema}.mst_asset (
            id SERIAL PRIMARY KEY, bluetooth_id TEXT NOT NULL UNIQUE, asset_name TEXT,
            current_zone_id INTEGER, last_movement_dt TIMESTAMPTZ,
            extra JSON, created_at TIMESTAMPTZ DEFAULT NOW())""",
        f"""CREATE TABLE IF NOT EXISTS {schema}.mst_zone_scanner (
            id SERIAL PRIMARY KEY,
            mst_zone_id INTEGER NOT NULL REFERENCES {schema}.mst_zone(id),
            mst_scanner_id INTEGER NOT NULL REFERENCES {schema}.mst_scanner(id))""",
        f"""CREATE TABLE IF NOT EXISTS {schema}.movement_log (
            id BIGSERIAL PRIMARY KEY, bluetooth_id TEXT NOT NULL,
            from_zone_id INTEGER, to_zone_id INTEGER,
            deciding_rssi NUMERIC(6,2), timestamp_movement TIMESTAMPTZ NOT NULL)""",
        f"""CREATE TABLE IF NOT EXISTS {schema}.mst_master (
            id SERIAL PRIMARY KEY, name TEXT, mac TEXT NOT NULL UNIQUE,
            ip TEXT NOT NULL, created_at TIMESTAMPTZ DEFAULT NOW())""",
    ]
    for stmt in stmts:
        await db.execute(text(stmt))

    await db.execute(
        text("""INSERT INTO shared.tenants
                  (tenant_id, name, mqtt_prefix, tier, plan, status, master_tier,
                   db_schema, contact_email, metadata)
                VALUES
                  (:tid, :name, :prefix, 'pooled', :plan, 'active', 'shared',
                   :schema, :email, '{}')
                ON CONFLICT (tenant_id) DO NOTHING"""),
        {"tid": tenant_id, "name": payload.name, "prefix": mqtt_prefix,
         "plan": payload.plan or "demo", "schema": schema,
         "email": payload.contact_email},
    )

    await db.execute(
        text("INSERT INTO shared.tenant_events (tenant_id, event_type, actor, payload) "
             "VALUES (:tid, 'created', 'system', :p)"),
        {"tid": tenant_id, "p": '{"source":"register_api"}'},
    )

    await db.commit()
    return TenantOut(tenant_id=tenant_id, name=payload.name, mqtt_prefix=mqtt_prefix)


# ── Active tenants (used by master.py pre-load) ───────────────────────────────

@router.get("/active")
async def list_active_tenants(db: AsyncSession = Depends(get_db)):
    """Returns all active tenant IDs. master.py calls this at startup."""
    result = await db.execute(
        text("SELECT tenant_id FROM shared.tenants WHERE status = 'active' ORDER BY created_at")
    )
    rows = result.fetchall()
    return {"tenants": [r.tenant_id for r in rows]}


# ── Admin: list all tenants ───────────────────────────────────────────────────

@router.get("", dependencies=[Depends(require_admin)])
async def list_tenants(db: AsyncSession = Depends(get_db)):
    """Full tenant list for admin panel."""
    result = await db.execute(
        text("""
            SELECT t.tenant_id, t.name, t.mqtt_prefix, t.plan, t.tier, t.master_tier,
                   t.status, t.db_schema, t.scanner_limit, t.asset_limit,
                   t.contact_email, t.created_at, t.metadata,
                   t.mode, t.tablet_host, t.tablet_port,
                   t.mqtt_username, t.mqtt_password
            FROM shared.tenants t
            ORDER BY t.created_at
        """)
    )
    rows = result.fetchall()
    return [
        {
            "tenant_id":     r.tenant_id,
            "name":          r.name,
            "mqtt_prefix":   r.mqtt_prefix,
            "plan":          r.plan,
            "tier":          r.tier,
            "master_tier":   r.master_tier,
            "status":        r.status,
            "db_schema":     r.db_schema,
            "scanner_limit": r.scanner_limit,
            "asset_limit":   r.asset_limit,
            "contact_email": r.contact_email,
            "created_at":    r.created_at.isoformat() if r.created_at else None,
            "metadata":      r.metadata or {},
            "mode":          r.mode,
            "tablet_host":   r.tablet_host,
            "tablet_port":   r.tablet_port,
            "mqtt_username": r.mqtt_username,
            "mqtt_password": r.mqtt_password,
        }
        for r in rows
    ]


# ── Admin: per-tenant stats ───────────────────────────────────────────────────

@router.get("/{tenant_id}/stats", dependencies=[Depends(require_admin)])
async def tenant_stats(tenant_id: str, db: AsyncSession = Depends(get_db)):
    """Counts of scanners, assets, zones, movements for admin panel."""
    row = (await db.execute(
        text("SELECT db_schema FROM shared.tenants WHERE tenant_id = :tid"),
        {"tid": tenant_id}
    )).fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Tenant not found")
    schema = row.db_schema
    if not schema:
        return {"tenant_id": tenant_id, "scanners": 0, "assets": 0, "zones": 0, "movements": 0}

    counts = {}
    for table, key in [("mst_scanner","scanners"),("mst_asset","assets"),
                       ("mst_zone","zones"),("movement_log","movements")]:
        try:
            r = await db.execute(text(f"SELECT COUNT(*) FROM {schema}.{table}"))
            counts[key] = r.scalar()
        except Exception:
            counts[key] = 0

    active_scanners = 0
    try:
        r = await db.execute(text(
            f"SELECT COUNT(*) FROM {schema}.mst_scanner WHERE scanner_status='active'"
        ))
        active_scanners = r.scalar()
    except Exception:
        pass

    counts["active_scanners"] = active_scanners
    counts["tenant_id"] = tenant_id
    return counts


# ── Admin: update tenant ──────────────────────────────────────────────────────

@router.patch("/{tenant_id}")
async def update_tenant(tenant_id: str, payload: TenantUpdateIn,
                        db: AsyncSession = Depends(get_db),
                        admin: dict = Depends(require_admin)):
    """Update tenant fields — status, tier, limits, etc."""
    existing = (await db.execute(
        text("SELECT tenant_id FROM shared.tenants WHERE tenant_id = :tid"),
        {"tid": tenant_id}
    )).fetchone()
    if not existing:
        raise HTTPException(status_code=404, detail="Tenant not found")

    updates = {}
    if payload.name          is not None: updates["name"]          = payload.name
    if payload.status        is not None: updates["status"]        = payload.status
    if payload.plan          is not None: updates["plan"]          = payload.plan
    if payload.tier          is not None: updates["tier"]          = payload.tier
    if payload.master_tier   is not None: updates["master_tier"]   = payload.master_tier
    if payload.scanner_limit is not None: updates["scanner_limit"] = payload.scanner_limit
    if payload.asset_limit   is not None: updates["asset_limit"]   = payload.asset_limit
    if payload.contact_email is not None: updates["contact_email"] = payload.contact_email
    if payload.metadata      is not None: updates["metadata"]      = payload.metadata
    if payload.mode           is not None: updates["mode"]           = payload.mode
    if payload.tablet_host    is not None: updates["tablet_host"]    = payload.tablet_host
    if payload.tablet_port    is not None: updates["tablet_port"]    = payload.tablet_port
    if payload.mqtt_username  is not None: updates["mqtt_username"]  = payload.mqtt_username
    if payload.mqtt_password  is not None: updates["mqtt_password"]  = payload.mqtt_password

    if not updates:
        raise HTTPException(status_code=400, detail="No fields to update")

    set_clause = ", ".join(f"{k} = :{k}" for k in updates)
    updates["tid"] = tenant_id
    await db.execute(
        text(f"UPDATE shared.tenants SET {set_clause} WHERE tenant_id = :tid"),
        updates,
    )

    audit_payload = {k: v for k, v in updates.items() if k != "tid"}
    await db.execute(
        text("INSERT INTO shared.tenant_events (tenant_id, event_type, actor, payload) "
             "VALUES (:tid, 'admin_update', :actor, CAST(:p AS jsonb))"),
        {"tid": tenant_id, "actor": admin.get("email", "admin"),
         "p": json.dumps(audit_payload, default=str)},
    )
    await db.commit()
    return {"ok": True, "updated": [k for k in updates.keys() if k != "tid"]}


# ── Admin: audit events ───────────────────────────────────────────────────────

@router.get("/{tenant_id}/events", dependencies=[Depends(require_admin)])
async def tenant_events(tenant_id: str, limit: int = 50, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        text("""SELECT id, event_type, actor, payload, created_at
                FROM shared.tenant_events
                WHERE tenant_id = :tid
                ORDER BY created_at DESC LIMIT :lim"""),
        {"tid": tenant_id, "lim": limit},
    )
    rows = result.fetchall()
    return [
        {"id": r.id, "event_type": r.event_type, "actor": r.actor,
         "payload": r.payload, "created_at": r.created_at.isoformat()}
        for r in rows
    ]

@router.post("/{tenant_id}/events", dependencies=[Depends(require_admin)])
async def append_tenant_event(tenant_id: str, payload: TenantEventIn,
                               db: AsyncSession = Depends(get_db)):
    existing = (await db.execute(
        text("SELECT tenant_id FROM shared.tenants WHERE tenant_id = :tid"),
        {"tid": tenant_id}
    )).fetchone()
    if not existing:
        raise HTTPException(status_code=404, detail="Tenant not found")
    await db.execute(
        text("INSERT INTO shared.tenant_events (tenant_id, event_type, actor, payload) "
             "VALUES (:tid, :et, :actor, CAST(:p AS jsonb))"),
        {"tid": tenant_id, "et": payload.event_type,
         "actor": payload.actor, "p": str(payload.payload or {}).replace("'", '"')},
    )
    await db.commit()
    return {"ok": True}


# ── Tenant config (Android provisioning) ─────────────────────────────────────

@router.get("/{tenant_id}/config", response_model=TenantConfigOut,
            dependencies=[Depends(RateLimiter(times=10, seconds=60))])
async def get_tenant_config(tenant_id: str,
                             p: dict = Depends(require_user),
                             db: AsyncSession = Depends(get_db)):
    """Return provisioning config — requires the caller's JWT tenant to match."""
    if p["tenant_id"] != tenant_id:
        raise HTTPException(status_code=403, detail="Tenant mismatch")

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
    if mode == "local":
        mqtt_host = "127.0.0.1"
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


# ── Verify tenant (Android subsequent launches) ───────────────────────────────

@router.get("/{tenant_id}", response_model=TenantOut)
async def get_tenant(tenant_id: str, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        text("SELECT tenant_id, name, mqtt_prefix FROM shared.tenants WHERE tenant_id = :tid"),
        {"tid": tenant_id}
    )
    row = result.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Tenant not found")
    return TenantOut(tenant_id=row.tenant_id, name=row.name, mqtt_prefix=row.mqtt_prefix)
