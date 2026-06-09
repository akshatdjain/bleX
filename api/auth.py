"""
auth.py — JWT + password helpers.

JWT algorithm: RS256 (asymmetric).
- Sign with JWT_PRIVATE_KEY (PEM, multi-line). Verify with JWT_PUBLIC_KEY.
- Both keys come from environment. Fail-fast if missing.
- Tokens carry a `typ` claim: "access" (15m) or "refresh" (7d).
"""
import hashlib
import os
import secrets
from datetime import datetime, timedelta, timezone
from typing import Optional, Tuple

from jose import jwt, JWTError
from passlib.context import CryptContext


# ── Password hashing ─────────────────────────────────────────────────────────

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto", bcrypt__rounds=10)


def hash_password(plain: str) -> str:
    return pwd_context.hash(plain[:72])


def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain[:72], hashed)


# ── Keys ────────────────────────────────────────────────────────────────────

JWT_PRIVATE_KEY = os.environ.get("JWT_PRIVATE_KEY")
JWT_PUBLIC_KEY  = os.environ.get("JWT_PUBLIC_KEY")
if not JWT_PRIVATE_KEY or not JWT_PUBLIC_KEY:
    raise RuntimeError(
        "JWT_PRIVATE_KEY and JWT_PUBLIC_KEY environment variables are required. "
        "Generate with: openssl genrsa -out priv.pem 2048; openssl rsa -in priv.pem -pubout -out pub.pem"
    )

ALGORITHM = "RS256"
ACCESS_TOKEN_EXPIRE_MIN   = 15
REFRESH_TOKEN_EXPIRE_DAYS = 7


# ── Tokens ──────────────────────────────────────────────────────────────────

def create_access_token(user_id: int, role: str, tenant_id: str) -> str:
    """15-minute access token. Sent as `Authorization: Bearer ...` on every API call."""
    now = datetime.now(timezone.utc)
    claims = {
        "sub":       str(user_id),
        "role":      role,
        "tenant_id": tenant_id,
        "typ":       "access",
        "iat":       int(now.timestamp()),
        "exp":       int((now + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MIN)).timestamp()),
    }
    return jwt.encode(claims, JWT_PRIVATE_KEY, algorithm=ALGORITHM)


def create_refresh_token(user_id: int, family_id: str, role: str = "user") -> Tuple[str, datetime]:
    """7-day refresh token. Sent in HttpOnly cookie. Rotated on every use."""
    now = datetime.now(timezone.utc)
    exp = now + timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS)
    claims = {
        "sub": str(user_id),
        "fam": family_id,
        "role": role,
        "typ": "refresh",
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp()),
        "jti": secrets.token_urlsafe(16),
    }
    return jwt.encode(claims, JWT_PRIVATE_KEY, algorithm=ALGORITHM), exp


def decode_token(token: str, expected_typ: Optional[str] = None) -> dict:
    """Decode + verify JWT. Raises jose.JWTError on invalid signature/expiry,
    ValueError on typ mismatch."""
    claims = jwt.decode(token, JWT_PUBLIC_KEY, algorithms=[ALGORITHM])
    if expected_typ and claims.get("typ") != expected_typ:
        raise ValueError(f"Token typ mismatch: expected {expected_typ}, got {claims.get('typ')}")
    return claims


# ── SHA-256 helper (used for refresh + device token storage) ────────────────

def sha256_hex(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()
