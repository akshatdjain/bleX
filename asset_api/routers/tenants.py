from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from pydantic import BaseModel
import random
import string

from database import get_db

router = APIRouter(prefix="/api/tenants", tags=["Tenants"])

# Characters for tenant ID — no ambiguous chars (0/O, 1/I/L)
_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

def _generate_tenant_id(length: int = 6) -> str:
    return "".join(random.choices(_CHARS, k=length))

class TenantRegisterIn(BaseModel):
    name: str           # e.g. "Raghu" or "City Hospital"

class TenantOut(BaseModel):
    tenant_id: str
    name: str
    mqtt_prefix: str

@router.post("/register", response_model=TenantOut)
async def register_tenant(payload: TenantRegisterIn, db: AsyncSession = Depends(get_db)):
    """
    Called by the Android app on first launch.
    Generates a 6-char tenant ID, creates the Postgres schema + all tables,
    registers in shared.tenants, and returns the tenant_id to the app.
    """
    # Try up to 5 times to get a unique ID
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

    schema = f"t_{tenant_id.lower()}"   # e.g. t_r4x9k2
    mqtt_prefix = f"ble/{tenant_id}"

    # asyncpg does not allow multiple statements per execute() — run each separately
    stmts = [
        f"CREATE SCHEMA IF NOT EXISTS {schema}",
        f"""CREATE TABLE IF NOT EXISTS {schema}.mst_zone (
            id SERIAL PRIMARY KEY, zone_name TEXT NOT NULL,
            description TEXT, dimension JSON)""",
        f"""CREATE TABLE IF NOT EXISTS {schema}.mst_scanner (
            id SERIAL PRIMARY KEY, mac_id TEXT NOT NULL, name TEXT, type TEXT,
            created_at TIMESTAMPTZ DEFAULT NOW(), last_heartbeat TIMESTAMPTZ)""",
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
        text("INSERT INTO shared.tenants (tenant_id, name, mqtt_prefix, tier, plan) "
             "VALUES (:tid, :name, :prefix, 'pooled', 'demo') ON CONFLICT (tenant_id) DO NOTHING"),
        {"tid": tenant_id, "name": payload.name, "prefix": mqtt_prefix}
    )
    await db.commit()

    return TenantOut(tenant_id=tenant_id, name=payload.name, mqtt_prefix=mqtt_prefix)


@router.get("/{tenant_id}", response_model=TenantOut)
async def get_tenant(tenant_id: str, db: AsyncSession = Depends(get_db)):
    """Returns tenant info. Android app calls this on subsequent launches to verify tenant still exists."""
    result = await db.execute(
        text("SELECT tenant_id, name, mqtt_prefix FROM shared.tenants WHERE tenant_id = :tid"),
        {"tid": tenant_id}
    )
    row = result.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Tenant not found")
    return TenantOut(tenant_id=row.tenant_id, name=row.name, mqtt_prefix=row.mqtt_prefix)
