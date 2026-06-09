"""
Admin user management endpoints.
All endpoints require admin role.
"""

import json
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import text
from pydantic import BaseModel, EmailStr
from typing import Optional
from datetime import datetime

from database import get_db
from routers.auth import require_admin
from auth import hash_password


async def _audit(db: AsyncSession, tenant_id: str, event_type: str, actor: str, payload: dict):
    """Insert tenant_event audit row. Best-effort: caller commits."""
    await db.execute(text("""
        INSERT INTO shared.tenant_events (tenant_id, event_type, actor, payload)
        VALUES (:tid, :et, :actor, CAST(:p AS jsonb))
    """), {"tid": tenant_id, "et": event_type, "actor": actor,
           "p": json.dumps(payload, default=str)})

router = APIRouter(prefix="/api/admin/users", tags=["Admin - Users"])


# ── Schemas ───────────────────────────────────────────────────────────────────

class UserOut(BaseModel):
    id: int
    email: str
    name: str
    tenant_id: str
    is_active: bool
    created_at: Optional[str]
    last_login: Optional[str]

class UserCreateIn(BaseModel):
    email: EmailStr
    name: str
    password: str
    tenant_id: str
    is_active: bool = True

class UserUpdateIn(BaseModel):
    name: Optional[str] = None
    is_active: Optional[bool] = None
    password: Optional[str] = None  # If provided, update password


# ── GET /api/admin/users ──────────────────────────────────────────────────────

@router.get("", response_model=list[UserOut], dependencies=[Depends(require_admin)])
async def list_users(db: AsyncSession = Depends(get_db)):
    """List all users across all tenants."""
    result = await db.execute(text("""
        SELECT id, email, name, tenant_id, is_active, created_at, last_login
        FROM shared.users
        ORDER BY created_at DESC
    """))
    rows = result.fetchall()
    return [
        UserOut(
            id=r.id,
            email=r.email,
            name=r.name,
            tenant_id=r.tenant_id,
            is_active=r.is_active,
            created_at=r.created_at.isoformat() if r.created_at else None,
            last_login=r.last_login.isoformat() if r.last_login else None,
        )
        for r in rows
    ]


# ── GET /api/admin/users/{user_id} ────────────────────────────────────────────

@router.get("/{user_id}", response_model=UserOut, dependencies=[Depends(require_admin)])
async def get_user(user_id: int, db: AsyncSession = Depends(get_db)):
    """Get single user detail."""
    result = await db.execute(text("""
        SELECT id, email, name, tenant_id, is_active, created_at, last_login
        FROM shared.users
        WHERE id = :uid
    """), {"uid": user_id})
    row = result.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="User not found")
    return UserOut(
        id=row.id,
        email=row.email,
        name=row.name,
        tenant_id=row.tenant_id,
        is_active=row.is_active,
        created_at=row.created_at.isoformat() if row.created_at else None,
        last_login=row.last_login.isoformat() if row.last_login else None,
    )


# ── POST /api/admin/users ─────────────────────────────────────────────────────

@router.post("", status_code=201)
async def create_user(payload: UserCreateIn,
                      admin: dict = Depends(require_admin),
                      db: AsyncSession = Depends(get_db)):
    """Create a new user."""
    # Check email not already registered
    existing = await db.execute(
        text("SELECT id FROM shared.users WHERE email = :email"),
        {"email": payload.email.lower().strip()}
    )
    if existing.fetchone():
        raise HTTPException(status_code=400, detail="Email already registered")

    # Validate tenant exists
    tenant_check = await db.execute(
        text("SELECT tenant_id FROM shared.tenants WHERE tenant_id = :tid"),
        {"tid": payload.tenant_id}
    )
    if not tenant_check.fetchone():
        raise HTTPException(status_code=400, detail="Tenant not found")

    # Hash password
    hashed = hash_password(payload.password)

    # Create user (role is always 'user' in shared.users table)
    result = await db.execute(text("""
        INSERT INTO shared.users (email, name, password_hash, tenant_id, is_active)
        VALUES (:email, :name, :hash, :tid, :active)
        RETURNING id
    """), {
        "email": payload.email.lower().strip(),
        "name": payload.name.strip(),
        "hash": hashed,
        "tid": payload.tenant_id,
        "active": payload.is_active,
    })
    user_id = result.fetchone()[0]
    await _audit(db, payload.tenant_id, "user_created",
                 admin.get("email", "admin"),
                 {"user_id": user_id, "email": payload.email.lower().strip()})
    await db.commit()

    return {"ok": True, "id": user_id, "email": payload.email.lower().strip()}


# ── PATCH /api/admin/users/{user_id} ──────────────────────────────────────────

@router.patch("/{user_id}")
async def update_user(user_id: int, payload: UserUpdateIn,
                      admin: dict = Depends(require_admin),
                      db: AsyncSession = Depends(get_db)):
    """Update user fields."""
    existing = (await db.execute(
        text("SELECT id, tenant_id FROM shared.users WHERE id = :uid"),
        {"uid": user_id}
    )).fetchone()
    if not existing:
        raise HTTPException(status_code=404, detail="User not found")

    updates = {}
    if payload.name is not None:
        updates["name"] = payload.name.strip()
    if payload.is_active is not None:
        updates["is_active"] = payload.is_active
    if payload.password is not None:
        updates["password_hash"] = hash_password(payload.password)

    if not updates:
        raise HTTPException(status_code=400, detail="No fields to update")

    set_clause = ", ".join(f"{k} = :{k}" for k in updates)
    params = dict(updates)
    params["uid"] = user_id

    await db.execute(
        text(f"UPDATE shared.users SET {set_clause} WHERE id = :uid"),
        params
    )
    audit_payload = {k: ("***" if k == "password_hash" else v) for k, v in updates.items()}
    audit_payload["user_id"] = user_id
    await _audit(db, existing.tenant_id, "user_updated",
                 admin.get("email", "admin"), audit_payload)
    await db.commit()

    return {"ok": True, "updated": list(updates.keys())}


# ── DELETE /api/admin/users/{user_id} ─────────────────────────────────────────

@router.delete("/{user_id}")
async def delete_user(user_id: int,
                      admin: dict = Depends(require_admin),
                      db: AsyncSession = Depends(get_db)):
    """Soft delete user (set is_active = false)."""
    existing = (await db.execute(
        text("SELECT id, tenant_id FROM shared.users WHERE id = :uid"),
        {"uid": user_id}
    )).fetchone()
    if not existing:
        raise HTTPException(status_code=404, detail="User not found")

    await db.execute(
        text("UPDATE shared.users SET is_active = FALSE WHERE id = :uid"),
        {"uid": user_id}
    )
    await _audit(db, existing.tenant_id, "user_deleted",
                 admin.get("email", "admin"), {"user_id": user_id})
    await db.commit()

    return {"ok": True, "deleted": user_id}
