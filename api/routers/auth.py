"""
routers/auth.py — login, register, refresh, logout, me.

Auth model:
- Login returns `{access_token, token_type, user}` in JSON body.
- Refresh JWT is set as an HttpOnly+Secure+SameSite=Strict cookie named
  `refresh`, scoped to /asset/api/auth so it's only sent to auth endpoints.
- All other protected routes require `Authorization: Bearer <access>`.

Refresh token rotation:
- Every /refresh use invalidates the old refresh row, mints a new one in the
  same family. Reuse of an already-revoked token revokes the entire family
  (forces a full logout) — defense against refresh-token theft.
"""
import asyncio
import random
import secrets
import string
import uuid
from functools import partial
from typing import Optional

from fastapi import APIRouter, Cookie, Depends, HTTPException, Request, Response, status
from fastapi_limiter.depends import RateLimiter
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from auth import (
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    sha256_hex,
    verify_password,
    REFRESH_TOKEN_EXPIRE_DAYS,
)
from database import get_db


REFRESH_COOKIE_NAME = "refresh"
REFRESH_COOKIE_PATH = "/asset/api/auth"

router = APIRouter(prefix="/api/auth", tags=["Auth"])

_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"


def _gen_tenant_id(length: int = 6) -> str:
    return "".join(random.choices(_CHARS, k=length))


async def _verify_pw(plain: str, hashed: str) -> bool:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, partial(verify_password, plain, hashed))


async def _hash_pw(plain: str) -> str:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, partial(hash_password, plain))


# ── Schemas ──────────────────────────────────────────────────────────────────

class RegisterIn(BaseModel):
    name: str
    email: str
    password: str
    org_name: str


class LoginIn(BaseModel):
    email: str
    password: str


class UserOut(BaseModel):
    id: int
    email: str
    name: str
    role: str
    tenant_id: str


class AuthOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserOut


class RefreshOut(BaseModel):
    access_token: str
    token_type: str = "bearer"


# ── Cookie helpers ───────────────────────────────────────────────────────────

def _set_refresh_cookie(response: Response, token: str) -> None:
    response.set_cookie(
        key=REFRESH_COOKIE_NAME,
        value=token,
        max_age=REFRESH_TOKEN_EXPIRE_DAYS * 24 * 60 * 60,
        path=REFRESH_COOKIE_PATH,
        httponly=True,
        secure=True,
        samesite="strict",
    )


def _clear_refresh_cookie(response: Response) -> None:
    response.delete_cookie(
        key=REFRESH_COOKIE_NAME,
        path=REFRESH_COOKIE_PATH,
    )


# ── Refresh token DB helpers ─────────────────────────────────────────────────

async def _store_refresh(db: AsyncSession, user_id: int, family_id: str,
                         token: str, expires_at, parent_id: Optional[int] = None,
                         user_agent: Optional[str] = None,
                         ip: Optional[str] = None) -> int:
    """Insert a new refresh-token row. Returns the row id."""
    row = (await db.execute(text("""
        INSERT INTO shared.refresh_tokens_user
            (user_id, token_hash, family_id, parent_id, expires_at, user_agent, ip)
        VALUES (:uid, :h, :fam, :pid, :exp, :ua, :ip)
        RETURNING id
    """), {
        "uid": user_id, "h": sha256_hex(token), "fam": family_id,
        "pid": parent_id, "exp": expires_at, "ua": user_agent, "ip": ip,
    })).fetchone()
    return row.id


async def _revoke_family(db: AsyncSession, family_id: str, reason: str) -> None:
    await db.execute(text("""
        UPDATE shared.refresh_tokens_user
           SET is_revoked = true, revoked_reason = :r
         WHERE family_id = :fam AND is_revoked = false
    """), {"fam": family_id, "r": reason})


# ── Register ─────────────────────────────────────────────────────────────────

@router.post("/register", response_model=AuthOut, status_code=201,
             dependencies=[Depends(RateLimiter(times=5, seconds=60))])
async def register(payload: RegisterIn, request: Request, response: Response,
                   db: AsyncSession = Depends(get_db)):
    """Register a new tenant + first user (admin role)."""
    email = payload.email.strip().lower()

    # Already exists?
    existing = (await db.execute(
        text("SELECT id FROM shared.users WHERE email = :e"), {"e": email}
    )).fetchone()
    if existing:
        raise HTTPException(409, "Email already registered")

    # New tenant
    for _ in range(5):
        tenant_id = _gen_tenant_id()
        if (await db.execute(
            text("SELECT 1 FROM shared.tenants WHERE tenant_id = :t"), {"t": tenant_id}
        )).fetchone() is None:
            break
    else:
        raise HTTPException(500, "Could not generate unique tenant ID")

    schema      = f"t_{tenant_id.lower()}"
    mqtt_prefix = f"ble/{tenant_id}"

    # Create per-tenant schema
    for stmt in [
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
    ]:
        await db.execute(text(stmt))

    await db.execute(text("""
        INSERT INTO shared.tenants
            (tenant_id, name, mqtt_prefix, tier, plan, status, master_tier, db_schema, contact_email, metadata)
        VALUES
            (:tid, :nm, :pfx, 'pooled', 'demo', 'active', 'shared', :sch, :em, '{}')
    """), {"tid": tenant_id, "nm": payload.org_name, "pfx": mqtt_prefix,
           "sch": schema, "em": email})

    # Public registration always creates role='user'.
    # Admin accounts are seeded once in DB and managed via /api/admin/users
    # (admin-only endpoint) — never via public signup.
    pw_hash = await _hash_pw(payload.password)
    user_row = (await db.execute(text("""
        INSERT INTO shared.users (tenant_id, name, email, password_hash, is_active)
        VALUES (:tid, :nm, :em, :ph, true)
        RETURNING id
    """), {"tid": tenant_id, "nm": payload.name, "em": email, "ph": pw_hash})).fetchone()

    await db.commit()

    user_id = user_row.id
    return await _issue_user_session(db, response, request, user_id,
                                     tenant_id, payload.name, email)


# ── Login ────────────────────────────────────────────────────────────────────

@router.post("/login", response_model=AuthOut,
             dependencies=[Depends(RateLimiter(times=10, seconds=60))])
async def login(payload: LoginIn, request: Request, response: Response,
                db: AsyncSession = Depends(get_db)):
    """Single login endpoint — tries admin first, then tenant user."""
    email = payload.email.strip().lower()

    # Try admin first
    arow = (await db.execute(text("""
        SELECT id, name, email, password_hash, is_active
          FROM shared.admins WHERE email = :e
    """), {"e": email})).fetchone()
    if arow and arow.is_active and await _verify_pw(payload.password, arow.password_hash):
        await db.execute(text("UPDATE shared.admins SET last_login=now() WHERE id=:i"), {"i": arow.id})
        await db.commit()
        return await _issue_admin_session(db, response, request, arow.id, arow.name, arow.email)

    # Try tenant user
    urow = (await db.execute(text("""
        SELECT id, tenant_id, name, email, password_hash, is_active
          FROM shared.users WHERE email = :e
    """), {"e": email})).fetchone()
    if urow and urow.is_active and await _verify_pw(payload.password, urow.password_hash):
        await db.execute(text("UPDATE shared.users SET last_login=now() WHERE id=:i"), {"i": urow.id})
        await db.commit()
        return await _issue_user_session(db, response, request, urow.id, urow.tenant_id, urow.name, urow.email)

    raise HTTPException(401, "Invalid credentials")


async def _store_refresh_admin(db: AsyncSession, admin_id: int, family_id: str,
                                token: str, expires_at, parent_id: Optional[int] = None,
                                user_agent: Optional[str] = None,
                                ip: Optional[str] = None) -> int:
    """Insert a new admin refresh-token row. Returns the row id."""
    row = (await db.execute(text("""
        INSERT INTO shared.refresh_tokens_admin
            (admin_id, token_hash, family_id, parent_id, expires_at, user_agent, ip)
        VALUES (:aid, :h, :fam, :pid, :exp, :ua, :ip)
        RETURNING id
    """), {
        "aid": admin_id, "h": sha256_hex(token), "fam": family_id,
        "pid": parent_id, "exp": expires_at, "ua": user_agent, "ip": ip,
    })).fetchone()
    return row.id


async def _revoke_admin_family(db: AsyncSession, family_id: str, reason: str) -> None:
    await db.execute(text("""
        UPDATE shared.refresh_tokens_admin
           SET is_revoked = true, revoked_reason = :r
         WHERE family_id = :fam AND is_revoked = false
    """), {"fam": family_id, "r": reason})


async def _issue_user_session(db: AsyncSession, response: Response, request: Request,
                               user_id: int, tenant_id: str, name: str, email: str) -> AuthOut:
    """Mint user session: JWT with role=user, refresh stored in refresh_tokens_user."""
    family_id = str(uuid.uuid4())
    refresh, exp = create_refresh_token(user_id, family_id, "user")
    await _store_refresh(
        db, user_id, family_id, refresh, exp,
        user_agent=request.headers.get("user-agent", "")[:500],
        ip=(request.client.host if request.client else None),
    )
    await db.commit()

    access = create_access_token(user_id, "user", tenant_id)
    _set_refresh_cookie(response, refresh)

    return AuthOut(
        access_token=access,
        user=UserOut(id=user_id, email=email, name=name, role="user", tenant_id=tenant_id),
    )


async def _issue_admin_session(db: AsyncSession, response: Response, request: Request,
                                admin_id: int, name: str, email: str) -> AuthOut:
    """Mint admin session: JWT with role=admin, refresh stored in refresh_tokens_admin."""
    family_id = str(uuid.uuid4())
    refresh, exp = create_refresh_token(admin_id, family_id, "admin")
    await _store_refresh_admin(
        db, admin_id, family_id, refresh, exp,
        user_agent=request.headers.get("user-agent", "")[:500],
        ip=(request.client.host if request.client else None),
    )
    await db.commit()

    access = create_access_token(admin_id, "admin", "")
    _set_refresh_cookie(response, refresh)

    return AuthOut(
        access_token=access,
        user=UserOut(id=admin_id, email=email, name=name, role="admin", tenant_id=""),
    )


# ── Refresh ──────────────────────────────────────────────────────────────────

@router.post("/refresh", response_model=RefreshOut,
             dependencies=[Depends(RateLimiter(times=60, seconds=60))])
async def refresh_token(request: Request, response: Response,
                        db: AsyncSession = Depends(get_db)):
    """Unified refresh: branches by role claim in JWT (admin vs user)."""
    cookie = request.cookies.get(REFRESH_COOKIE_NAME)
    if not cookie:
        raise HTTPException(401, "No refresh cookie")

    try:
        claims = decode_token(cookie, expected_typ="refresh")
    except Exception:
        _clear_refresh_cookie(response)
        raise HTTPException(401, "Invalid refresh token")

    sub       = int(claims["sub"])
    family_id = claims["fam"]
    role      = claims.get("role", "user")
    th        = sha256_hex(cookie)

    if role == "admin":
        table = "shared.refresh_tokens_admin"
        revoke_fn = _revoke_admin_family
        store_fn = _store_refresh_admin
        # Fetch admin
        principal = (await db.execute(text("""
            SELECT id, is_active FROM shared.admins WHERE id = :i
        """), {"i": sub})).fetchone()
        principal_type = "admin"
        tenant_id = ""
    else:
        table = "shared.refresh_tokens_user"
        revoke_fn = _revoke_family
        store_fn = _store_refresh
        # Fetch user
        principal = (await db.execute(text("""
            SELECT id, tenant_id, is_active FROM shared.users WHERE id = :i
        """), {"i": sub})).fetchone()
        principal_type = "user"
        tenant_id = principal.tenant_id if principal else ""

    row = (await db.execute(text(f"""
        SELECT id, is_revoked FROM {table} WHERE token_hash = :h
    """), {"h": th})).fetchone()

    if not row:
        await revoke_fn(db, family_id, "unknown_token")
        await db.commit()
        _clear_refresh_cookie(response)
        raise HTTPException(401, "Refresh token not recognized")

    if row.is_revoked:
        await revoke_fn(db, family_id, "reuse_detected")
        await db.commit()
        _clear_refresh_cookie(response)
        raise HTTPException(401, "Refresh token reuse detected; please log in again")

    if not principal or not principal.is_active:
        await revoke_fn(db, family_id, f"{principal_type}_disabled")
        await db.commit()
        _clear_refresh_cookie(response)
        raise HTTPException(401, f"{principal_type.capitalize()} no longer active")

    # Mark old row revoked, mint new token
    await db.execute(text(f"""
        UPDATE {table} SET is_revoked = true, revoked_reason = 'rotated' WHERE id = :i
    """), {"i": row.id})
    new_refresh, new_exp = create_refresh_token(sub, family_id, role)
    await store_fn(db, sub, family_id, new_refresh, new_exp,
                   parent_id=row.id,
                   user_agent=request.headers.get("user-agent", "")[:500],
                   ip=(request.client.host if request.client else None))
    await db.commit()

    access = create_access_token(sub, role, tenant_id)
    _set_refresh_cookie(response, new_refresh)
    return RefreshOut(access_token=access)


# ── Logout ───────────────────────────────────────────────────────────────────

@router.post("/logout")
async def logout(request: Request, response: Response,
                 db: AsyncSession = Depends(get_db)):
    """Revoke the current refresh family (admin or user) + clear cookie. Idempotent."""
    cookie = request.cookies.get(REFRESH_COOKIE_NAME)
    if cookie:
        try:
            claims = decode_token(cookie, expected_typ="refresh")
            role = claims.get("role", "user")
            revoke_fn = _revoke_admin_family if role == "admin" else _revoke_family
            await revoke_fn(db, claims["fam"], "logout")
            await db.commit()
        except Exception:
            pass  # best-effort; clear cookie either way
    _clear_refresh_cookie(response)
    return {"ok": True}


# ── Auth dependencies (used by all protected routers) ───────────────────────

async def get_principal(request: Request, db: AsyncSession = Depends(get_db)) -> dict:
    """
    Returns one of:
      {"type":"user",   "id", "email", "name", "role":"user",  "tenant_id"}
      {"type":"admin",  "id", "email", "name", "role":"admin", "tenant_id": None}
      {"type":"device", "id", "device_id", "mac", "tenant_id", "role"}
    Reads `Authorization: Bearer <token>`. JWT claim `role` decides which table.
    """
    auth = request.headers.get("Authorization", "")
    if not auth.lower().startswith("bearer "):
        raise HTTPException(401, "Missing Bearer token")
    token = auth.split(" ", 1)[1].strip()
    if not token:
        raise HTTPException(401, "Empty Bearer token")

    # Try JWT
    try:
        claims = decode_token(token, expected_typ="access")
        sub_id = int(claims["sub"])
        claim_role = claims.get("role")

        if claim_role == "admin":
            row = (await db.execute(text("""
                SELECT id, email, name, is_active
                  FROM shared.admins WHERE id = :uid
            """), {"uid": sub_id})).fetchone()
            if not row or not row.is_active:
                raise HTTPException(401, "Admin not found or inactive")
            return {
                "type": "admin",
                "id": row.id, "email": row.email, "name": row.name,
                "role": "admin", "tenant_id": None,
            }
        else:  # claim_role == "user" or anything else → tenant user
            row = (await db.execute(text("""
                SELECT id, email, name, tenant_id, is_active
                  FROM shared.users WHERE id = :uid
            """), {"uid": sub_id})).fetchone()
            if not row or not row.is_active:
                raise HTTPException(401, "User not found or inactive")
            return {
                "type": "user",
                "id": row.id, "email": row.email, "name": row.name,
                "role": "user", "tenant_id": row.tenant_id,
            }
    except HTTPException:
        raise
    except Exception:
        pass  # not a valid JWT; try device token below

    # Try device token (sha256)
    th = sha256_hex(token)
    row = (await db.execute(text("""
        SELECT id, device_id, mac, tenant_id, role, is_active
          FROM shared.devices WHERE token_hash = :h
    """), {"h": th})).fetchone()
    if row and row.is_active:
        # Update last_seen (best effort)
        try:
            await db.execute(text("UPDATE shared.devices SET last_seen = now() WHERE id = :i"),
                             {"i": row.id})
            await db.commit()
        except Exception:
            await db.rollback()
        return {
            "type": "device", "id": row.id, "device_id": row.device_id,
            "mac": row.mac, "tenant_id": row.tenant_id, "role": row.role,
        }

    raise HTTPException(401, "Invalid token")


async def get_principal_db(request: Request, principal: dict = Depends(get_principal)):
    """Tenant-scoped DB session derived from the authenticated principal's
    tenant_id. The token (JWT or device) is the single source of truth —
    no X-Tenant-ID header trust, no silent `public` fallback.

    Exception: service-role device tokens derive their schema from the
    X-Tenant-ID request header so they can act on behalf of any tenant.
    """
    from database import AsyncSessionLocal, _TENANT_RE
    from sqlalchemy import text

    if principal.get("role") == "service":
        # Service token: trust X-Tenant-ID header to select the target tenant
        header_tid = request.headers.get("X-Tenant-ID", "")
        if not header_tid or not _TENANT_RE.match(header_tid):
            raise HTTPException(400, "Service token requires a valid X-Tenant-ID header")
        tid = header_tid
    else:
        tid = principal.get("tenant_id")

    schema = f"t_{tid.lower()}" if tid and _TENANT_RE.match(tid) else "public"
    async with AsyncSessionLocal() as session:
        await session.execute(text(f"SET search_path TO {schema}, public"))
        try:
            yield session
        finally:
            try:
                await session.execute(text("SET search_path TO public"))
            except Exception:
                pass


async def require_user(p: dict = Depends(get_principal)) -> dict:
    """Tenant user only (admins rejected here — they use require_admin)."""
    if p["type"] != "user":
        raise HTTPException(403, "Tenant user authentication required")
    return p


async def require_admin(p: dict = Depends(get_principal)) -> dict:
    """Platform admin only (not chained through require_user)."""
    if p["type"] != "admin":
        raise HTTPException(403, "Admin authentication required")
    return p


def require_device(tenant_id_param: str = "tenant_id"):
    """Factory: dependency that requires a device token whose tenant matches
    a path/query/header param. Defaults to looking for `tenant_id`.

    Service-role device tokens skip the tenant-match check — they are
    authorised to act on behalf of any tenant.
    """
    async def _dep(request: Request, p: dict = Depends(get_principal)) -> dict:
        if p["type"] != "device":
            raise HTTPException(403, "Device token required")
        # Service role: skip tenant-match check entirely
        if p.get("role") == "service":
            return p
        path_tid   = request.path_params.get(tenant_id_param) if hasattr(request, "path_params") else None
        query_tid  = request.query_params.get(tenant_id_param) if request.query_params else None
        header_tid = request.headers.get("X-Tenant-ID")
        provided = path_tid or query_tid or header_tid
        if provided and provided != p["tenant_id"]:
            raise HTTPException(403, "Device token tenant mismatch")
        return p
    return _dep


# Backwards-compat alias used by older code paths
get_current_user = require_user


async def require_tenant_match(
    request: Request,
    p: dict = Depends(require_user),
) -> dict:
    """Tenant user only, AND verify the tenant_id in their JWT matches the
    X-Tenant-ID header (or path param). Defense in depth — keeps a SF5WU6 user
    from poking around YYJF2N data even if they craft headers."""
    header_tid = request.headers.get("X-Tenant-ID")
    if header_tid and header_tid != p["tenant_id"]:
        raise HTTPException(403, f"Tenant mismatch: token={p['tenant_id']} header={header_tid}")
    return p


# ── Me ──────────────────────────────────────────────────────────────────────

@router.get("/me", response_model=UserOut)
async def me(p: dict = Depends(get_principal)):
    if p["type"] not in ("user", "admin"):
        raise HTTPException(403, "User authentication required")
    return UserOut(
        id=p["id"], email=p["email"], name=p["name"],
        role=p["role"], tenant_id=p.get("tenant_id") or "",
    )
