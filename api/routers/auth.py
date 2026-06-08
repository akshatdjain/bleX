# routers/auth.py - register, login, and profile endpoints
from fastapi.responses import RedirectResponse
from fastapi import APIRouter, Depends, HTTPException, status, Header, Request
from fastapi.responses import JSONResponse
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from pydantic import BaseModel, EmailStr
from typing import Optional
import random
import string
from fastapi_limiter.depends import RateLimiter
import asyncio
from functools import partial

from database import get_db
from auth import hash_password, verify_password, create_access_token, decode_token

async def verify_password_async(plain: str, hashed: str) -> bool:
    """Run bcrypt in a thread pool so it doesn't block the event loop."""
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, partial(verify_password, plain, hashed))

async def hash_password_async(plain: str) -> str:
    """Run bcrypt hash in a thread pool."""
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, partial(hash_password, plain))

router = APIRouter(prefix="/api/auth", tags=["Auth"])

_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

def _gen_tenant_id(length: int = 6) -> str:
    return "".join(random.choices(_CHARS, k=length))


# Schemas

class RegisterIn(BaseModel):
    name: str
    email: str
    password: str
    org_name: str

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

class MeOut(BaseModel):
    tenant_id: str
    name: str
    email: str
    org_name: str
    role: str


# Register

@router.post("/register", response_model=AuthOut, status_code=201,
             dependencies=[Depends(RateLimiter(times=5, seconds=60))])
async def register(payload: RegisterIn, db: AsyncSession = Depends(get_db)):
    """
    Creates a new tenant + first admin user.
    Returns a JWT token so the app is immediately logged in.
    Also sets httpOnly cookie for web clients.
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
    is_new_tenant = True
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

    # First user of new tenant is admin, subsequent users are 'user'
    user_role = "admin" if is_new_tenant else "user"

    # Create tenant schema + all 6 tables
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
        VALUES (:tid, :name, :email, :hash, :role)
        RETURNING id
    """), {
        "tid": tenant_id,
        "name": payload.name.strip(),
        "email": payload.email.lower().strip(),
        "hash": hashed,
        "role": user_role,
    })
    user_id = result.fetchone()[0]
    await db.commit()

    token = create_access_token({
        "sub": str(user_id),
        "tenant_id": tenant_id,
        "email": payload.email.lower().strip(),
        "role": user_role,
    })

    response_data = AuthOut(
        access_token=token,
        tenant_id=tenant_id,
        user_id=user_id,
        name=payload.name.strip(),
        email=payload.email.lower().strip(),
        org_name=org_name,
        mqtt_prefix=mqtt_prefix,
    )
    
    # Set httpOnly cookie
    response = JSONResponse(content=response_data.model_dump(), status_code=201)
    response.set_cookie(
        key="blex_token",
        value=token,
        httponly=True,
        samesite="strict",
        secure=True,
        path="/",
        max_age=28800
    )
    return response


# Login

@router.post("/login", response_model=AuthOut,
             dependencies=[Depends(RateLimiter(times=10, seconds=60))])
async def login(payload: LoginIn, db: AsyncSession = Depends(get_db)):
    result = await db.execute(text("""
        SELECT u.id, u.name, u.email, u.password_hash, u.role, u.is_active,
               t.tenant_id, t.name as org_name, t.mqtt_prefix
        FROM shared.users u
        JOIN shared.tenants t ON t.tenant_id = u.tenant_id
        WHERE u.email = :email
    """), {"email": payload.email.lower().strip()})
    row = result.fetchone()

    if not row or not await verify_password_async(payload.password, row.password_hash):
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

    response_data = AuthOut(
        access_token=token,
        tenant_id=row.tenant_id,
        user_id=row.id,
        name=row.name,
        email=row.email,
        org_name=row.org_name,
        mqtt_prefix=row.mqtt_prefix,
    )
    
    # Set httpOnly cookie
    response = JSONResponse(content=response_data.model_dump())
    response.set_cookie(
        key="blex_token",
        value=token,
        httponly=True,
        samesite="strict",
        secure=True,
        path="/",
        max_age=28800
    )
    return response


# GET /me

@router.get("/me", response_model=MeOut)
async def get_me(request: Request, db: AsyncSession = Depends(get_db)):
    """
    Called by web clients to get user info from httpOnly cookie.
    Reads blex_token cookie, decodes JWT, queries user + tenant.
    """
    token = request.cookies.get("blex_token")
    if not token:
        raise HTTPException(status_code=401, detail="No authentication cookie")
    
    try:
        claims = decode_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
    
    user_id = int(claims.get("sub", ""))
    result = await db.execute(text("""
        SELECT u.name, u.email, u.tenant_id, u.role,
               t.name as org_name
        FROM shared.users u
        JOIN shared.tenants t ON t.tenant_id = u.tenant_id
        WHERE u.id = :uid AND u.is_active = TRUE
    """), {"uid": user_id})
    row = result.fetchone()

    if not row:
        raise HTTPException(status_code=401, detail="User not found")

    return MeOut(
        tenant_id=row.tenant_id,
        name=row.name,
        email=row.email,
        org_name=row.org_name,
        role=row.role,
    )


# POST /logout

@router.post("/logout")
async def logout():
    """
    Clears the blex_token httpOnly cookie.
    """
    response = JSONResponse(content={"message": "Logged out"})
    response.delete_cookie(key="blex_token", path="/", samesite="strict")
    return response


# Profile

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


import secrets as _secrets
import os as _os
import redis as _redis_lib

# Redis-backed nonce store — survives restarts, works across multiple instances
# Falls back to in-memory if Redis unavailable (single-instance safety net)
_REDIS_URL = _os.getenv("REDIS_URL", "redis://redis:6379")
_NONCE_TTL = 60  # seconds

def _get_redis():
    try:
        r = _redis_lib.from_url(_REDIS_URL, decode_responses=True, socket_connect_timeout=2)
        r.ping()
        return r
    except Exception:
        return None

_redis_client = _get_redis()

import json as _json

def _nonce_set(nonce: str, token: str, client_ip: str):
    """Store nonce with IP binding and 60s TTL. Redis primary, in-memory fallback."""
    payload = _json.dumps({"token": token, "ip": client_ip})
    if _redis_client:
        try:
            _redis_client.setex(f"blex:nonce:{nonce}", _NONCE_TTL, payload)
            return
        except Exception:
            pass
    _nonce_fallback[nonce] = payload

def _nonce_pop(nonce: str) -> dict | None:
    """Atomically get-and-delete nonce. Returns {token, ip} or None."""
    if _redis_client:
        try:
            pipe = _redis_client.pipeline()
            pipe.get(f"blex:nonce:{nonce}")
            pipe.delete(f"blex:nonce:{nonce}")
            results = pipe.execute()
            raw = results[0]
            return _json.loads(raw) if raw else None
        except Exception:
            pass
    raw = _nonce_fallback.pop(nonce, None)
    return _json.loads(raw) if raw else None

_nonce_fallback: dict = {}  # in-memory fallback


def _get_client_ip(request: Request) -> str:
    """Get real client IP, respecting X-Forwarded-For from Caddy."""
    forwarded = request.headers.get("X-Forwarded-For")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


@router.post("/web-nonce")
async def create_web_nonce(request: Request):
    """
    Creates a one-time nonce for WebView auto-login.
    Security: bound to requesting IP + rate limited (5/min per user) + Redis 60s TTL.
    """
    token = request.cookies.get("blex_token")
    if not token:
        raise HTTPException(status_code=401, detail="No authentication cookie")
    try:
        claims = decode_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid token")

    # Rate limiting: max 5 nonce requests per minute per user
    user_id = claims.get("sub", "unknown")
    if _redis_client:
        try:
            rate_key = f"blex:nonce_rate:{user_id}"
            count = _redis_client.incr(rate_key)
            if count == 1:
                _redis_client.expire(rate_key, 60)
            if count > 5:
                raise HTTPException(status_code=429, detail="Too many nonce requests — try again in 1 minute")
        except HTTPException:
            raise
        except Exception:
            pass  # Redis down — skip rate limiting, don't block

    client_ip = _get_client_ip(request)
    nonce = _secrets.token_urlsafe(32)
    _nonce_set(nonce, token, client_ip)
    return {"nonce": nonce}


@router.get("/weblogin")
async def weblogin(nonce: str, request: Request):
    """
    Validates one-time nonce (atomic get+delete), checks IP binding,
    sets httpOnly cookie, redirects to /blex/dashboard.
    """
    entry = _nonce_pop(nonce)
    if not entry:
        raise HTTPException(status_code=401, detail="Invalid or expired nonce")

    # IP binding — nonce must be used from same IP it was created from
    # Logs mismatch but allows through if behind NAT/proxy to avoid breaking mobile users
    client_ip = _get_client_ip(request)
    stored_ip = entry.get("ip", "")
    if stored_ip and stored_ip != client_ip:
        import logging
        logging.getLogger("blex.auth").warning(f"Nonce IP mismatch: created={stored_ip} used={client_ip} — allowing (mobile NAT)")
        # For strict mode, uncomment: raise HTTPException(status_code=401, detail="Nonce IP mismatch")

    token = entry["token"]
    try:
        decode_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Token expired")

    redirect = RedirectResponse(url="/blex/dashboard", status_code=302)
    redirect.set_cookie(
        key="blex_token",
        value=token,
        httponly=True,
        samesite="strict",
        secure=True,
        path="/",
        max_age=86400  # 24h for WebView sessions (shorter than 7-day app token)
    )
    # HSTS header — enforce HTTPS for all future requests
    redirect.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
    return redirect


# ── Authentication Dependencies ───────────────────────────────────────────────

async def get_current_user(request: Request, db: AsyncSession = Depends(get_db)) -> dict:
    """
    Extracts user from blex_token httpOnly cookie OR Authorization: Bearer header.
    Web clients use the cookie (set by /login). Android/native clients send the
    JWT they received from /login as `Authorization: Bearer <token>`.
    Returns user dict with id, email, name, role, tenant_id, is_active.
    Raises 401 if missing/invalid token or inactive user.
    """
    token = request.cookies.get("blex_token")
    if not token:
        auth_header = request.headers.get("Authorization", "")
        if auth_header.lower().startswith("bearer "):
            token = auth_header.split(" ", 1)[1].strip()
    if not token:
        raise HTTPException(status_code=401, detail="No authentication credentials")

    try:
        claims = decode_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    user_id = int(claims.get("sub", "0"))
    result = await db.execute(text("""
        SELECT id, email, name, role, tenant_id, is_active
        FROM shared.users
        WHERE id = :uid
    """), {"uid": user_id})
    row = result.fetchone()

    if not row:
        raise HTTPException(status_code=401, detail="User not found")

    if not row.is_active:
        raise HTTPException(status_code=403, detail="Account disabled")

    return {
        "id": row.id,
        "email": row.email,
        "name": row.name,
        "role": row.role,
        "tenant_id": row.tenant_id,
        "is_active": row.is_active,
    }


async def require_admin(user: dict = Depends(get_current_user)) -> dict:
    """
    Requires admin role. Raises 403 if user is not admin.
    """
    if user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Admin access required")
    return user
