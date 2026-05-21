# routers/auth.py — register, login, and profile endpoints
from fastapi import APIRouter, Depends, HTTPException, status, Header
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from pydantic import BaseModel, EmailStr
from typing import Optional
import random
import string

from database import get_db
from auth import hash_password, verify_password, create_access_token, decode_token

router = APIRouter(prefix="/api/auth", tags=["Auth"])

_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

def _gen_tenant_id(length: int = 6) -> str:
    return "".join(random.choices(_CHARS, k=length))


# ── Schemas ──────────────────────────────────────────────────────

class RegisterIn(BaseModel):
    name: str
    email: str
    password: str
    org_name: str           # becomes the tenant name, e.g. "City Hospital"

class LoginIn(BaseModel):
    email: str
    password: str

class AuthOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    tenant_id: str
    user_id: int
    name: str
    email: str
    org_name: str
    mqtt_prefix: str

class ProfileOut(BaseModel):
    user_id: int
    name: str
    email: str
    tenant_id: str
    org_name: str
    mqtt_prefix: str
    role: str


# ── Register ─────────────────────────────────────────────────────

@router.post("/register", response_model=AuthOut, status_code=201)
async def register(payload: RegisterIn, db: AsyncSession = Depends(get_db)):
    """
    Creates a new tenant + first admin user.
    Returns a JWT token so the app is immediately logged in.
    """
    # Check email not already registered
    existing = await db.execute(
        text("SELECT id FROM shared.users WHERE email = :email"),
        {"email": payload.email.lower().strip()}
    )
    if existing.fetchone():
        raise HTTPException(status_code=400, detail="Email already registered")

    # Generate unique tenant_id
    tenant_id = None
    for _ in range(5):
        candidate = _gen_tenant_id()
        row = await db.execute(
            text("SELECT tenant_id FROM shared.tenants WHERE tenant_id = :tid"),
            {"tid": candidate}
        )
        if not row.fetchone():
            tenant_id = candidate
            break
    if not tenant_id:
        raise HTTPException(status_code=500, detail="Could not generate tenant ID, try again")

    schema = f"t_{tenant_id.lower()}"
    mqtt_prefix = f"ble/{tenant_id}"
    org_name = payload.org_name.strip()

    # Create tenant schema + all 6 tables
    # asyncpg does not allow multiple statements in one execute() call — run each separately
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

    # Register tenant
    await db.execute(text("""
        INSERT INTO shared.tenants (tenant_id, name, mqtt_prefix, tier, plan)
        VALUES (:tid, :name, :prefix, 'pooled', 'demo')
        ON CONFLICT (tenant_id) DO NOTHING
    """), {"tid": tenant_id, "name": org_name, "prefix": mqtt_prefix})

    # Create user
    hashed = hash_password(payload.password)
    result = await db.execute(text("""
        INSERT INTO shared.users (tenant_id, name, email, password_hash, role)
        VALUES (:tid, :name, :email, :hash, 'admin')
        RETURNING id
    """), {
        "tid": tenant_id,
        "name": payload.name.strip(),
        "email": payload.email.lower().strip(),
        "hash": hashed,
    })
    user_id = result.fetchone()[0]
    await db.commit()

    token = create_access_token({
        "sub": str(user_id),
        "tenant_id": tenant_id,
        "email": payload.email.lower().strip(),
        "role": "admin",
    })

    return AuthOut(
        access_token=token,
        tenant_id=tenant_id,
        user_id=user_id,
        name=payload.name.strip(),
        email=payload.email.lower().strip(),
        org_name=org_name,
        mqtt_prefix=mqtt_prefix,
    )


# ── Login ─────────────────────────────────────────────────────────

@router.post("/login", response_model=AuthOut)
async def login(payload: LoginIn, db: AsyncSession = Depends(get_db)):
    result = await db.execute(text("""
        SELECT u.id, u.name, u.email, u.password_hash, u.role, u.is_active,
               t.tenant_id, t.name as org_name, t.mqtt_prefix
        FROM shared.users u
        JOIN shared.tenants t ON t.tenant_id = u.tenant_id
        WHERE u.email = :email
    """), {"email": payload.email.lower().strip()})
    row = result.fetchone()

    if not row or not verify_password(payload.password, row.password_hash):
        raise HTTPException(status_code=401, detail="Invalid email or password")
    if not row.is_active:
        raise HTTPException(status_code=403, detail="Account disabled")

    # Update last_login
    await db.execute(text("""
        UPDATE shared.users SET last_login = NOW() WHERE id = :uid
    """), {"uid": row.id})
    await db.commit()

    token = create_access_token({
        "sub": str(row.id),
        "tenant_id": row.tenant_id,
        "email": row.email,
        "role": row.role,
    })

    return AuthOut(
        access_token=token,
        tenant_id=row.tenant_id,
        user_id=row.id,
        name=row.name,
        email=row.email,
        org_name=row.org_name,
        mqtt_prefix=row.mqtt_prefix,
    )


# ── Profile (validate token) ─────────────────────────────────────

@router.get("/profile", response_model=ProfileOut)
async def profile(
    authorization: str = Header(...),
    db: AsyncSession = Depends(get_db)
):
    """Called on app launch to validate stored token and reload user info."""
    try:
        scheme, token = authorization.split(" ", 1)
        if scheme.lower() != "bearer":
            raise ValueError
        claims = decode_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    result = await db.execute(text("""
        SELECT u.id, u.name, u.email, u.role,
               t.tenant_id, t.name as org_name, t.mqtt_prefix
        FROM shared.users u
        JOIN shared.tenants t ON t.tenant_id = u.tenant_id
        WHERE u.id = :uid AND u.is_active = TRUE
    """), {"uid": int(claims["sub"])})
    row = result.fetchone()
    if not row:
        raise HTTPException(status_code=401, detail="User not found")

    return ProfileOut(
        user_id=row.id,
        name=row.name,
        email=row.email,
        tenant_id=row.tenant_id,
        org_name=row.org_name,
        mqtt_prefix=row.mqtt_prefix,
        role=row.role,
    )
