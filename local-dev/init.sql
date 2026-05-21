-- BleX Local Dev — Postgres init
-- Tenant schemas (t_XXXXXX) are created dynamically on /api/auth/register
-- This file only sets up the shared tables needed for auth

CREATE SCHEMA IF NOT EXISTS shared;

CREATE TABLE IF NOT EXISTS shared.tenants (
    tenant_id   TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    mqtt_prefix TEXT NOT NULL,
    tier        TEXT NOT NULL DEFAULT 'pooled',
    plan        TEXT DEFAULT 'demo',
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS shared.users (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     TEXT NOT NULL REFERENCES shared.tenants(tenant_id),
    name          TEXT NOT NULL,
    email         TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'admin',
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    last_login    TIMESTAMPTZ,
    is_active     BOOLEAN DEFAULT TRUE,
    UNIQUE(email, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_users_tenant ON shared.users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_users_email  ON shared.users(email);
