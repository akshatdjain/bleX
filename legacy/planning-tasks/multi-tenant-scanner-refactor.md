# BleX Multi-Tenant + Scanner Refactor — Task Plan

**Date:** 2026-05-17  
**Context:** Moving from single-tenant demo to proper multi-tenant architecture. DGX runs the full stack (MQTT broker, master engine, FastAPI, Postgres, Redis). Pi/ESP32/Android devices are scanners only. Dave, Ehab, and Raghu are the first three tenants.

---

## DGX Current State (as-is)

### Services Running (5 weeks uptime)

| Container | Role | Ports |
|-----------|------|-------|
| `mqtt_broker` (Mosquitto) | MQTT broker | 1883 (raw), 9001 (WebSocket via Caddy) |
| `master_engine` (Python) | Zone decision engine | internal |
| `master_fifo_consumer` (Python) | Redis→API event consumer | internal |
| `asset_tracking-asset_api` (FastAPI) | CRUD + movement REST API | 5000→8000 |
| `asset_tracking-ui_api` (FastAPI) | Read-only dashboard API | 4000→9000 |
| `asset_tracking-db` (PostgreSQL 15) | Database | internal |
| `master_redis` (Redis 7) | Zone event FIFO queue | internal |
| `adminer` | DB web UI | 8080 |

### Key File Locations

| File | Purpose |
|------|---------|
| `/home/akshat/master/master.py` | Zone decision engine (425 lines) |
| `/home/akshat/master/fifo_consumer.py` | Redis BLPOP → POST /api/asset/movement |
| `/home/akshat/master/config.py` | MQTT/Redis/API config |
| `/home/akshat/master/docker-compose.yml` | master_engine + master_fifo_consumer |
| `/home/akshat/mqtt/mosquitto.conf` | Mosquitto config (auth required, no persist) |
| `/home/akshat/mqtt/docker-compose.yml` | Mosquitto + Caddy |
| `/home/akshat/mqtt/Caddyfile` | TLS + /mqtt WebSocket route |
| `/home/akshat/asset_tracking/` | FastAPI backend + Postgres |
| `/home/akshat/tags_tracking/` | ⚠️ Duplicate of asset_tracking — NOT proper multi-tenancy |

### Current Data Flow

```
BLE Beacon
  ↓
Pi/ESP32/Android scanner
  ↓ MQTT topic: ble/scanner/{scanner_mac}  ← NO TENANT PREFIX (single tenant)
Mosquitto broker (DGX :1883)
  ↓ subscribes ble/scanner/#
master_engine (master.py)
  - Kalman + hysteresis (5 dBm) + dwell (5s) + TTL (10s) + lost timeout (30s)
  - Loads scanner→zone map via long-poll from API
  ↓ Redis FIFO: zone:movement:queue
master_fifo_consumer
  - BLPOP → POST /api/asset/movement
  ↓
asset_api FastAPI
  - Asset whitelist filter (unknown beacons ignored)
  - INSERT movement_log, UPDATE mst_asset.current_zone_id
  ↓
PostgreSQL (single schema, single tenant)
  ↓
ui_api reads for sigmatic-asc.tech/beam dashboard
```

### Current Single-Tenant Schema

```sql
mst_zone       (id, zone_name, description, dimension)
mst_scanner    (id, mac_id, name, type, last_heartbeat)
mst_asset      (id, bluetooth_id, asset_name, current_zone_id, last_movement_dt, extra JSON)
mst_zone_scanner (id, mst_zone_id, mst_scanner_id)  -- many-to-many
movement_log   (id, bluetooth_id, from_zone_id, to_zone_id, deciding_rssi, timestamp_movement)
mst_master     (id, name, mac, ip)
```

### Notable Issues Found

1. **Hardcoded IPs** in `master/config.py` — MQTT broker is `10.1.2.223` (not container hostname)
2. **`tags_tracking/`** is a full code duplicate, not real multi-tenancy
3. **No tenant_id anywhere** in schema, MQTT topics, or API
4. **MQTT auth enabled** (password file) but credentials hardcoded in docker-compose
5. **MQTT topic:** `ble/scanner/#` — one flat namespace, all tenants would collide

---

## Multi-Tenancy Design Decisions

### MQTT Topic Format (unchanged)
```
ble/{tenant_id}/scanner/{scanner_mac}
```
Examples:
- `ble/dave_house/scanner/AA:BB:CC:DD:EE:FF`
- `ble/ehab_house/scanner/11:22:33:44:55:66`

---

### Decision 1: Database Isolation — **Schema-per-tenant** ✓

**Verdict: Schema-per-tenant wins.** The earlier claim that "SQLAlchemy async makes this hard" was a misconception — it is not hard.

**The actual truth about SQLAlchemy async + schemas:**
- `SET search_path TO tenant_xyz` is pool-dangerous (the next connection checkout inherits the wrong schema) — this is the thing people warn about
- But SQLAlchemy has a built-in, pool-safe solution: `schema_translate_map` — rewrites table schemas at query compilation time, per-session, no raw SQL needed
- One line per request: `session.execute_options = {"schema_translate_map": {None: "tenant_dave_house"}}`
- This is a non-issue

**Why schema-per-tenant is better for BleX:**

| Factor | Schema-per-tenant | Column-level tenant_id |
|--------|-------------------|------------------------|
| Security | Bulletproof — physically impossible to leak cross-tenant | One missed WHERE clause = Customer A sees Customer B's asset locations |
| movement_log scale | Each tenant's log is its own table — naturally partitioned | One massive table across all tenants, composite indexes required everywhere |
| Tenant deletion | `DROP SCHEMA CASCADE` — instant, clean | DELETE across 6+ tables + VACUUM |
| Per-tenant backup | `pg_dump -n tenant_dave_house` — exact snapshot | Must filter every table by tenant_id |
| Migrations | Loop Alembic over all schemas (supported natively) | Single migration but must backfill |
| New tenant | CREATE SCHEMA + 6 CREATE TABLEs (~1 second) | Insert a row in tenants table |
| Cross-tenant analytics | Query across schemas with schema.table or views (possible) | Trivial — remove the WHERE filter |

At 20–200 tenants × 6 tables = 120–1200 relations. PostgreSQL handles this fine up to 10,000+ schemas.

**Global (shared) schema** — one extra schema called `shared` or `public`:
```sql
-- shared schema: tenant registry + cross-tenant routing
tenants (tenant_id TEXT PK, name, mqtt_prefix, created_at, plan_tier)
```

**FastAPI integration:**
```python
# Per-request dependency — pool-safe, no SET search_path
async def get_tenant_db(
    x_tenant_id: str = Header(...),
    db: AsyncSession = Depends(get_raw_db)
) -> AsyncSession:
    schema = f"tenant_{x_tenant_id}"
    db.sync_session.execute_options = {
        "schema_translate_map": {None: schema}
    }
    return db
```

---

### Decision 2: Master Engine Isolation — **Tiered containers** ✓

**Verdict: Container-per-tenant, but tiered by size.** Not one-container-for-all (zero failure isolation), not 200 containers blindly (unnecessary for small tenants).

**Why not single process for all tenants:**
- One tenant's scanner flood (malfunction, stuck Pi) starves all other tenants' zone processing
- One unhandled exception crashes everyone
- Can't restart one tenant without restarting all

**Why not a container per tenant blindly:**
- 200 small tenants (2-3 scanners, 10 beacons each) × ~40MB = 8GB RAM wasted on almost-idle processes
- MQTT data volumes are tiny — a single process can handle 200 small tenants trivially

**The tiered approach:**

| Tier | Criteria | Container model |
|------|----------|-----------------|
| **Dedicated** | >10 scanners OR >100 beacons | Own `master_engine_{tenant_id}` container |
| **Pooled** | ≤10 scanners AND ≤100 beacons | Shared pool container handling N small tenants |

At 200 tenants: maybe 20-40 get dedicated containers, 160-180 share 2-3 pool containers. Total: ~25-45 containers, ~2-3GB RAM.

**Per-tenant container (dedicated tier) — master.py changes:**
```python
TENANT_ID = os.environ["TENANT_ID"]  # e.g. "dave_house"
MQTT_TOPIC = f"ble/{TENANT_ID}/scanner/#"  # subscribes only to this tenant
REDIS_KEY  = f"zone_events:{TENANT_ID}"   # namespaced Redis queue
API_ZONE_MAP = f"{API_BASE}/api/runtime/scanner-zone-map?tenant_id={TENANT_ID}"

# In-memory state — no tenant partitioning needed (it's the whole process)
ASSET_STATE = {"AA:BB:CC": {...}}        # clean, no nested tenant key
SCANNER_ZONE_MAP = {"B8:27:EB:...": 1}
```

**Pool container (shared tier) — same master.py but multi-tenant dict:**
```python
TENANT_IDS = os.environ["TENANT_IDS"].split(",")  # e.g. "ehab_house,raghu_home,..."
# Subscribe to all assigned tenants
for tid in TENANT_IDS:
    client.subscribe(f"ble/{tid}/scanner/#")

# Partitioned state — MAC collisions handled by tenant key
ASSET_STATE = {"dave_house": {"AA:BB": {...}}, "ehab_house": {"AA:BB": {...}}}
```

**FIFO consumer** — can be shared, reads tenant from Redis key:
```python
key, event = redis_client.blpop([f"zone_events:{t}" for t in TENANT_IDS])
tenant_id = key.split(":")[2]  # zone_events:{tenant_id}
requests.post(f"{API_BASE}/api/asset/movement", json={**event, "tenant_id": tenant_id})
```

**Tenant provisioning script (`tools/provision_tenant.py`):**
```python
def provision_tenant(tenant_id: str, tier: str = "pooled"):
    create_postgres_schema(tenant_id)   # CREATE SCHEMA tenant_{id} + all tables
    register_in_shared_table(tenant_id) # INSERT INTO shared.tenants
    add_mqtt_acl(tenant_id)             # mosquitto ACL: ble/{tenant_id}/# 
    if tier == "dedicated":
        start_master_container(tenant_id)
    else:
        assign_to_pool_container(tenant_id)
```

---

### Full Isolation Layer Summary

| Layer | Mechanism |
|-------|-----------|
| MQTT | Topic prefix `ble/{tenant_id}/` + Mosquitto ACL per tenant |
| Postgres | Schema-per-tenant (`tenant_dave_house`, `tenant_ehab_house`, ...) + `shared` schema for registry |
| Redis | Key prefix `zone_events:{tenant_id}:` |
| master_engine | Dedicated container (large tenants) or pool container (small tenants) |
| API | `X-Tenant-ID` header → `schema_translate_map` per request |

### Initial Tenants

| tenant_id | Owner | Tier | Notes |
|-----------|-------|------|-------|
| `dave_house` | Dave (VP Eng) | pooled | Home demo |
| `ehab_house` | Ehab | pooled | Home demo |
| `raghu_home` | Raghu (Manager) | pooled | Home demo |
| `default` | Internal | pooled | Backward compat for old Pi images |

---

## Task 1 — Pi Provisioner: Bad WiFi Revert to "setup" Network

**File:** `current/scanner/provisioner_service.py`

### Correct Setup Flow (how it actually works)

```
Pi boots
  ↓ connects to "setup" WiFi (tablet's hotspot — SSID/pass baked into Pi image)
  ↓ Pi is a WiFi CLIENT — it NEVER acts as an AP
  ↓ Pi and tablet are on the same local network (192.168.x.x)
  ↓
BleX App → Configurator → Scanners tab
  ↓ UDP discovery finds Pi on the shared LAN
  ↓ Pi appears in the scanner list
  ↓
IT person selects Pi, enters site WiFi creds (hospital / office network)
  ↓ App POSTs {ssid, psk} to Pi at 192.168.x.x:8888/provision
  ↓
Pi tries nmcli connection up {new_ssid}
  ↓
  ├── SUCCESS → Pi joins site WiFi, starts publishing BLE data to tablet via MQTT
  │
  └── FAIL (wrong creds — Dave's case)
        Pi drops off "setup" network while trying to join bad network
        Pi never joins bad network either → stuck in limbo
        Pi disappears from Configurator/Scanners (UDP gone, different subnet or no network)
        Dave had to physically unplug/replug Pi to reboot it back onto "setup"

CORRECT BEHAVIOUR ON FAIL:
  Pi detects no connectivity → deletes bad profile → reconnects to "setup"
  → reappears in Configurator/Scanners tab automatically
  → IT person retries with correct creds, no physical intervention needed
```

### Root Cause in provisioner_service.py
- Lines 61-64: `setup` connection torn down **before** verifying the new network works
- Line 67: `Popen` fires connect and returns immediately — no wait, no result check
- `check_zombie_fallback()` defined at line 82 but **never called** (line 98 just runs `serve_forever()`)
- Once `setup` is torn down and new network fails, Pi has no saved fallback to return to

### Fix — Two Parts

**Part A: Don't tear down "setup" until new connection confirmed**
```python
# REMOVE these premature teardown lines (61-64):
# subprocess.run(["sudo", "nmcli", "connection", "down", "setup"], ...)
# subprocess.run(["sudo", "nmcli", "connection", "down", "AsseTrack-Setup"], ...)

# Instead: let nmcli's autoconnect-priority handle it
# New network gets priority 10, "setup" stays at priority 0 as fallback
# nmcli will naturally fall back to "setup" if new network unreachable
```

**Part B: Watchdog thread confirms connection or forces fallback**
```python
# After nmcli connection add + up {new_ssid}:
# Start daemon thread:
#   for 60s (every 5s): ping -c 1 -W 2 {gateway or 8.8.8.8}
#   SUCCESS → write status=connected, optionally then tear down "setup"
#   TIMEOUT → nmcli connection delete {new_ssid}
#             nmcli connection up setup  (or AsseTrack-Setup)
#             write status=failed
```

**Part C: `GET /status` endpoint (new)**
```json
{"state": "idle|connecting|connected|failed", "ssid": "DaveHome"}
```
Pi serves this on port 8888 while the watchdog runs.  
Android app polls this every 3s after submitting creds and shows live feedback:
- Spinner → "Checking connectivity..."
- Connected → "Connected to DaveHome ✓ — scanner is live"
- Failed → "Wrong credentials — scanner reconnected to setup network. Try again."

**Also fix:** `provisioner/esp32/provision_listener.py` — same issue, same fix pattern

**Files to change:**
- `current/scanner/provisioner_service.py`
- `provisioner/esp32/provision_listener.py`
- `android/.../HotspotTab.kt` — add /status polling + feedback UI

---

## Task 2 — Pi Scanner: tenant_id in MQTT Topic

**Files:** `current/scanner/scanner.py`, `current/scanner/config.py`

### Change

Scanner reads `tenant_id` from `~/mqtt_config.json` (written by provisioner).  
Topic changes from `ble/scanner/{mac}` → `ble/{tenant_id}/scanner/{mac}`

```python
# mqtt_config.json (written by provisioner)
{
  "mqtt_host": "192.168.x.x",
  "mqtt_port": 1883,
  "tenant_id": "dave_house"   # NEW
}
```

**Backward compat:** If `tenant_id` missing → fall back to `ble/scanner/{mac}` (old images still work)

**Payload also gains `tenant_id` field:**
```json
{"tenant_id": "dave_house", "scanner_id": "AA:BB", "mac": "...", "rssi": {...}, ...}
```

**Files to change:**
- `current/scanner/scanner.py` — load tenant_id, update topic string
- `current/scanner/config.py` — add `TENANT_ID = "default"` fallback
- `current/scanner/provisioner_service.py` — store `tenant_id` in mqtt_config.json

---

## Task 3 — Android App: Push tenant_id + Auto-Apply Config Bundle

**Files:** `android/.../HotspotTab.kt`, `SettingsManager.kt`, `AppConfig.kt`, `PayloadBuilder.kt`, `MqttManager.kt`

### Provisioner Payload (Android → Pi)

```json
{
  "ssid": "DaveHome",
  "psk": "password123",
  "mqtt_host": "192.168.x.x",
  "mqtt_port": 1883,
  "tenant_id": "dave_house",
  "api_url": "https://sigmatic-asc.tech/asset",
  "web_url": "https://sigmatic-asc.tech/beam"
}
```

### Provisioner Response (Pi → Android)

```json
{
  "status": "ok",
  "message": "Processing config...",
  "config": {
    "api_url": "https://sigmatic-asc.tech/asset",
    "web_url": "https://sigmatic-asc.tech/beam",
    "mqtt_host": "192.168.x.x",
    "tenant_id": "dave_house"
  }
}
```

App auto-saves returned config to SettingsManager — no manual URL entry ever.

### Android MQTT Topic

Android scanner also needs tenant prefix:  
`ble/{tenant_id}/scanner/{android_device_id}`

**Files to change:**
- `android/.../HotspotTab.kt` — add tenant_id field, send full payload, poll /status, auto-apply config
- `android/.../SettingsManager.kt` — add `tenant_id` pref key
- `android/.../AppConfig.kt` — add `DEFAULT_TENANT_ID = "default"`
- `android/.../PayloadBuilder.kt` — add `tenant_id` to beacon payloads
- `android/.../MqttManager.kt` — use `ble/{tenant_id}/scanner/{id}` topic

---

## Task 4 — DGX master.py: Two-Container Architecture + Smart Tiering

### The Two Container Types

**Container A: `master_pool`** — handles all small/new tenants
- Subscribes `ble/+/scanner/#` (wildcard across all tenants)
- Routes in-memory state by tenant_id: `ASSET_STATE[tenant_id][asset_mac]`
- Runs one long-poll thread per active tenant for scanner-zone-map
- Redis keys: `zone_events:{tenant_id}`
- All tenants start here

**Container B: `master_dedicated_{tenant_id}`** — one per large tenant
- `TENANT_ID` env var set to a single tenant (e.g. `dave_house`)
- Subscribes only `ble/dave_house/scanner/#`
- Clean single-tenant code — no dict partitioning, no routing logic
- One long-poll thread for its own scanner-zone-map
- Redis key: `zone_events:dave_house`
- State is rebuilt naturally from live MQTT within ~30s of startup (no handoff needed)

### Smart Tiering — Auto-Promotion via Cron

A cron job on DGX checks tenant metrics every 5 minutes and promotes/demotes:

```
# /home/akshat/master/check_tiers.py  (runs every 5 min via crontab)

For each tenant in shared.tenants:
  count = SELECT COUNT(*) FROM tenant_{id}.mst_asset
  current_tier = shared.tenants.tier

  if count > PROMOTE_THRESHOLD and current_tier == "pooled":
      → start dedicated container for this tenant
      → unsubscribe from pool (pool's subscription is wildcard, so no change needed —
         the dedicated container's faster processing will "win" the Redis queue)
      → update shared.tenants SET tier = 'dedicated'
      → log the promotion

  if count < DEMOTE_THRESHOLD and current_tier == "dedicated":
      → stop dedicated container
      → update shared.tenants SET tier = 'pooled'
      → log the demotion
```

**Thresholds** (to be tuned later, starting values):
```python
PROMOTE_THRESHOLD = 100  # assets/beacons → gets own container
DEMOTE_THRESHOLD  = 20   # assets → back to pool (hysteresis gap prevents flapping)
```

**State handoff on promotion:** None needed. When a dedicated container starts, it:
1. Subscribes to `ble/{tenant_id}/scanner/#`
2. Receives live MQTT messages (scanners publish every 3s)
3. Rebuilds full zone state within one SCANNER_TTL window (10s of data → full picture)
4. The pool container still processes that tenant briefly during the overlap — this is harmless (both push to the same Redis key, same idempotent zone logic, at most a duplicate event)

**On demotion:** Dedicated container stops. Pool container's wildcard subscription automatically picks up the tenant again.

### master.py Changes

**Pool version** (`master_pool.py` — new file, extends master.py):
```python
# Partitioned state
ASSET_STATE    = {}  # {tenant_id: {asset_mac: state_dict}}
SCANNER_ZONE_MAP = {}  # {tenant_id: {scanner_mac: zone_id}}
ZONE_MAP_THREADS = {}  # {tenant_id: Thread}

def on_message(client, userdata, msg):
    parts = msg.topic.split("/")
    if len(parts) == 4:
        tenant_id, scanner_mac = parts[1], parts[3]
    elif len(parts) == 3:
        tenant_id, scanner_mac = "default", parts[2]  # backward compat
    else:
        return

    if tenant_id not in ASSET_STATE:
        ASSET_STATE[tenant_id] = {}
        SCANNER_ZONE_MAP[tenant_id] = {}
        start_zone_map_thread(tenant_id)  # spin up long-poll for new tenant

    process_beacon(tenant_id, scanner_mac, json.loads(msg.payload))

# Zone map long-poll — one thread per tenant
def zone_map_thread(tenant_id):
    while True:
        resp = requests.get(
            f"{API_BASE}/api/runtime/scanner-zone-map/watch",
            params={"tenant_id": tenant_id, "version": current_version[tenant_id]},
            timeout=65
        )
        SCANNER_ZONE_MAP[tenant_id] = resp.json()["scanner_zone_map"]
```

**Dedicated version** — just set `TENANT_ID` env var, subscribe to single topic, no dict nesting:
```python
TENANT_ID   = os.environ["TENANT_ID"]
MQTT_TOPIC  = f"ble/{TENANT_ID}/scanner/#"
REDIS_KEY   = f"zone_events:{TENANT_ID}"
API_ZONE_MAP = f"{API_BASE}/api/runtime/scanner-zone-map/watch?tenant_id={TENANT_ID}"
# ASSET_STATE and SCANNER_ZONE_MAP remain flat dicts (no tenant nesting)
```

### check_tiers.py (cron script)
```python
#!/usr/bin/env python3
# crontab: */5 * * * * /home/akshat/master/check_tiers.py >> /home/akshat/master/logs/tiers.log 2>&1

import psycopg2, subprocess, json

PROMOTE_THRESHOLD = 100
DEMOTE_THRESHOLD  = 20

def get_asset_counts():
    conn = psycopg2.connect(DATABASE_URL)
    cur = conn.cursor()
    cur.execute("SELECT tenant_id, tier FROM shared.tenants")
    tenants = cur.fetchall()
    counts = {}
    for tenant_id, tier in tenants:
        try:
            cur.execute(f"SELECT COUNT(*) FROM tenant_{tenant_id}.mst_asset")
            counts[tenant_id] = {"count": cur.fetchone()[0], "tier": tier}
        except Exception:
            pass
    return counts

def promote(tenant_id):
    subprocess.run([
        "docker", "run", "-d",
        "--name", f"master_dedicated_{tenant_id}",
        "--network", "asset_tracking_default",
        "-e", f"TENANT_ID={tenant_id}",
        "-e", f"API_BASE={API_BASE}",
        "-e", f"MQTT_BROKER={MQTT_BROKER}",
        "-e", f"REDIS_HOST={REDIS_HOST}",
        "blex/master_engine:latest"
    ])
    # Update tier in DB
    # log

def demote(tenant_id):
    subprocess.run(["docker", "stop", f"master_dedicated_{tenant_id}"])
    subprocess.run(["docker", "rm", f"master_dedicated_{tenant_id}"])
    # Update tier in DB
    # log

if __name__ == "__main__":
    for tenant_id, info in get_asset_counts().items():
        if info["count"] > PROMOTE_THRESHOLD and info["tier"] == "pooled":
            promote(tenant_id)
        elif info["count"] < DEMOTE_THRESHOLD and info["tier"] == "dedicated":
            demote(tenant_id)
```

### fifo_consumer.py Changes

Needs `tenant_id` in every API POST:
```python
# Was: redis_client.blpop("zone:movement:queue")
# Now: BLPOP on all active tenant queues
keys = [f"zone_events:{t}" for t in get_active_tenants()]
key, raw = redis_client.blpop(keys, timeout=5)
tenant_id = key.decode().split(":")[1]  # zone_events:{tenant_id}
event = json.loads(raw)
requests.post(
    f"{API_BASE}/api/asset/movement",
    headers={"X-Tenant-ID": tenant_id},
    json=event
)
```

**Files to create/change:**
- `/home/akshat/master/master.py` — add `TENANT_ID` env var support (dedicated mode)
- `/home/akshat/master/master_pool.py` — new file, pool mode with tenant routing
- `/home/akshat/master/fifo_consumer.py` — multi-queue BLPOP + tenant header
- `/home/akshat/master/check_tiers.py` — new cron script for auto-promotion
- `/home/akshat/master/docker-compose.yml` — add `master_pool` service, template for dedicated
- DGX crontab — add `*/5 * * * * /home/akshat/master/check_tiers.py`

---

## Task 5 — Postgres: Schema-per-tenant + API Routing

**Context:** Move from single `public` schema to per-tenant schemas. `tags_tracking/` dead code — delete it.

### Schema Structure

```
PostgreSQL database: asset_tracking
├── shared          ← global registry (new schema)
│   └── tenants (tenant_id TEXT PK, name, mqtt_prefix, tier, created_at, plan)
├── tenant_dave_house   ← per-tenant schema
│   ├── mst_zone
│   ├── mst_scanner
│   ├── mst_asset
│   ├── mst_zone_scanner
│   └── movement_log
├── tenant_ehab_house
│   └── (same tables)
├── tenant_raghu_home
│   └── (same tables)
└── public              ← existing data (migrated to tenant_default)
    └── (all existing tables — migrate to tenant_default, then drop from public)
```

### Provisioning a New Tenant (SQL)

```sql
-- Run once per new tenant (via provision_tenant.py script)
CREATE SCHEMA IF NOT EXISTS tenant_{tenant_id};

CREATE TABLE tenant_{tenant_id}.mst_zone AS SELECT * FROM public.mst_zone WHERE false;
CREATE TABLE tenant_{tenant_id}.mst_scanner AS SELECT * FROM public.mst_scanner WHERE false;
CREATE TABLE tenant_{tenant_id}.mst_asset AS SELECT * FROM public.mst_asset WHERE false;
CREATE TABLE tenant_{tenant_id}.mst_zone_scanner AS SELECT * FROM public.mst_zone_scanner WHERE false;
CREATE TABLE tenant_{tenant_id}.movement_log AS SELECT * FROM public.movement_log WHERE false;
-- Restore constraints, indexes, sequences on new schema tables

INSERT INTO shared.tenants (tenant_id, name, tier) VALUES ('{tenant_id}', '{name}', 'pooled');
```

### FastAPI Changes

SQLAlchemy `schema_translate_map` per request — pool-safe, no `SET search_path`:

```python
# database.py — add tenant session factory
def get_tenant_session(tenant_id: str) -> AsyncSession:
    schema = f"tenant_{tenant_id}"
    session = AsyncSessionLocal()
    # Rewrite all {None}.table references to tenant schema at query time
    session.sync_session.get_bind().execution_options(
        schema_translate_map={None: schema}
    )
    return session

# FastAPI dependency
async def get_tenant_db(
    x_tenant_id: str = Header(default="default"),
) -> AsyncGenerator[AsyncSession, None]:
    schema = f"tenant_{x_tenant_id}"
    async with AsyncSessionLocal() as session:
        await session.execute(
            text("SET search_path TO :schema"),
            {"schema": schema}
        )
        yield session
```

> Note: `SET search_path` is safe here because we use a fresh session per request (not pooled connection reuse). The session is closed at end of request, connection returned to pool resets via pool event.

All existing routers gain one extra dependency — no other query changes needed since table names don't change, only the schema:
```python
@router.post("/asset/movement")
async def asset_movement(
    payload: MovementIn,
    db: AsyncSession = Depends(get_tenant_db)  # ← only change per router
):
    # all existing query code unchanged
```

**New endpoint:** `POST /api/tenants`
```python
@router.post("/tenants")
async def create_tenant(tenant_id: str, name: str):
    # Runs CREATE SCHEMA + CREATE TABLEs via asyncpg raw SQL
    # Inserts into shared.tenants
    # Returns {"ok": True, "tenant_id": tenant_id}
```

### Migration: Existing Data → `tenant_default`

```sql
-- Preserve current data as the "default" tenant
CREATE SCHEMA tenant_default;
-- Copy all tables + data from public → tenant_default
INSERT INTO tenant_default.mst_zone SELECT * FROM public.mst_zone;
INSERT INTO tenant_default.mst_scanner SELECT * FROM public.mst_scanner;
-- etc.
-- Insert into registry
INSERT INTO shared.tenants VALUES ('default', 'Default (legacy)', 'pooled', now(), 'free');
```

### Kill tags_tracking

```bash
# Confirm it's safe (no live traffic, same schema as asset_tracking)
ssh dgx "docker ps | grep tags"  # should show nothing running from tags_tracking
# Archive then remove
ssh dgx "mv /home/akshat/tags_tracking /home/akshat/tags_tracking_archived_$(date +%Y%m%d)"
```

**Files to change:**
- `/home/akshat/asset_tracking/asset_api/database.py` — add `get_tenant_db` dependency
- `/home/akshat/asset_tracking/asset_api/routers/*.py` — swap `get_db` → `get_tenant_db`
- New: `/home/akshat/asset_tracking/asset_api/routers/tenants.py` — tenant CRUD endpoint
- New: `/home/akshat/asset_tracking/tools/provision_tenant.py` — CLI bootstrap script
- New: `/home/akshat/asset_tracking/tools/migrate_to_schemas.sql` — one-time migration

---

## Task 6 — Slim Pi Image (scanner-only, no master)

### Current Pi images include (unnecessarily):
- `master.py` zone logic
- Redis
- Full zone decision dependencies

### New scanner-only image:
- **Removes:** `master.py`, Redis, zone logic
- **Keeps:** `scanner.py`, `provisioner_service.py`, `bleak`, `paho-mqtt`, `systemd` services
- **Systemd services:**
  - `provisioner.service` — runs on boot, listens port 8888
  - `scanner.service` — starts after provisioner confirmed WiFi, runs 24/7
- **Tag:** `v2.1.0` on GitHub releases

---

## Implementation Order

```
Phase 1 — Fix live pain (Pi provisioner)
  1. Pi provisioner watchdog fix          current/scanner/provisioner_service.py
  2. ESP32 provisioner same fix           provisioner/esp32/provision_listener.py
  3. Android Hotspot tab polling UI       android/.../HotspotTab.kt

Phase 2 — Tenant identity at source
  4. Pi scanner tenant_id in topic        current/scanner/scanner.py + config.py
  5. Android MQTT topic tenant prefix     android/.../MqttManager.kt + PayloadBuilder.kt
  6. Android auto-config from provision   android/.../HotspotTab.kt + SettingsManager.kt

Phase 3 — DGX backend (do in this exact order — API before master)
  7. Postgres schema migration            tools/migrate_to_schemas.sql
     └─ Create shared schema + tenants table
     └─ Migrate public data → tenant_default schema
     └─ Create schemas for dave_house, ehab_house, raghu_home
  8. FastAPI tenant routing               asset_api/database.py + all routers
     └─ get_tenant_db dependency
     └─ New /api/tenants endpoint
     └─ provision_tenant.py CLI script
  9. master_pool.py (pool container)      /home/akshat/master/master_pool.py
     └─ Wildcard subscribe ble/+/scanner/#
     └─ Per-tenant state partitioning
     └─ Per-tenant zone map long-poll threads
 10. master.py (dedicated container mode) /home/akshat/master/master.py
     └─ TENANT_ID env var → single-tenant subscribe
 11. fifo_consumer.py multi-tenant        /home/akshat/master/fifo_consumer.py
     └─ Multi-queue BLPOP + X-Tenant-ID header
 12. check_tiers.py cron + Docker SDK     /home/akshat/master/check_tiers.py
     └─ Auto-promote/demote based on asset count
     └─ Add to DGX crontab: */5 * * * *
 13. docker-compose updates               /home/akshat/master/docker-compose.yml
     └─ master_pool service replaces master_engine
     └─ Env vars for dedicated container template

Phase 4 — Cleanup
 14. Slim Pi image v2.1.0               Strip master.py/Redis from Pi image, tag release
 15. Kill tags_tracking                 Archive /home/akshat/tags_tracking_archived_*
```

---

## Open Questions

- [ ] **Promotion thresholds** — PROMOTE_THRESHOLD=100 assets is starting value, tune after first real deployments
- [ ] **MQTT ACL per tenant** — currently one shared password file. Future: per-tenant MQTT credentials so `dave` can only pub/sub to `ble/dave_house/#`
- [ ] **tags_tracking/** — verify no live traffic before archiving (check docker ps on DGX)
- [ ] **fifo_consumer pool** — single consumer or one per active tenant queue? Start with single consumer doing multi-BLPOP
- [ ] **master_pool thread count** — one long-poll thread per active tenant. At 100 pooled tenants = 100 threads. Switch to asyncio if thread count becomes a problem
