# BleX Security & Feature Session - TBD and Technical Debt

**Date:** Session covering BLEX-1 security hardening + feature work  
**Branch:** `feat/blex-1-2-rbac-bearer-auth`

---

## What Was Built This Session

### Authentication & Security

| Feature | Details |
|---------|---------|
| **Unified Login** | Single `/api/auth/login` endpoint dispatches admin vs user by DB lookup order |
| **JWT Tokens** | RS256 signing; 15-minute access tokens + 7-day refresh tokens with family revocation on reuse |
| **Device Tokens** | SHA256-hashed, never-expiring, revocable tokens for Pi devices stored in `shared.devices` |
| **Principal Resolution** | `get_principal()` handles both JWT (users/admins) and opaque device tokens |
| **Tenant Derivation** | `get_principal_db()` derives tenant schema from `principal["tenant_id"]` — token is source of truth, not headers |
| **Header Security** | Removed `X-Tenant-ID` header trust from runtime/health endpoints (was leaking public schema access) |
| **Device Provisioning** | `POST /api/devices/provision`: tenant-facing endpoint to issue Pi API tokens (no admin required) |
| **Config Endpoint** | `GET /api/tenants/{id}/config`: now requires user JWT with matching tenant_id |
| **Config Merge** | Merged `tenants_config.py` into `tenants.py` |
| **Duplicate Cleanup** | Removed all duplicate entries from `shared.admins` (was causing every user to log in as admin) |

### Pi Provisioning Flow

**Android App → Pi Device:**
1. Android: Register → issues API token from DGX → stores in EncryptedSharedPreferences (AES256-GCM, Android Keystore)
2. Android: Provision → reads stored token → POSTs full config to Pi `:8888`
3. Pi provisioner: writes `/etc/blex/blex.env` with `BLEX_API_TOKEN`, MQTT creds, mode, role

**Critical Fixes in Pi Provisioner:**
- Fixed sudoers path: `tee /etc/blex/env` → `tee /etc/blex/blex.env` (writes were silently failing)
- Fixed logs permissions: `chown blex:blex /home/blex/blex/logs/` (blex-provisioner was crashing)
- Fixed MQTT credentials: `MQTT_USERNAME/PASSWORD` were blank in `blex.env` → scanner was connecting anonymously → broker rejected

### Android App Fixes

| Issue | Fix |
|-------|-----|
| **Token Refresh** | Added `withRefresh {}` wrapper on all API calls; catches 401/Invalid token → tries refresh → retries |
| **Refresh Token Persistence** | Refresh cookie persisted in EncryptedSharedPreferences (survives app updates/restarts) |
| **MQTT Client ID** | Changed from hardcoded `blex-bridge-remote` to `blex-bridge-{last8ofMAC}` (was causing session takeover loop) |
| **Zone Parsing** | Fixed field rename: `zone_name` → `name` in API responses |
| **Zone ID Types** | Fixed string vs int parsing in zone lists |
| **Zones Tab Display** | Now shows assigned scanners (list_zones wasn't returning scanners array) |
| **Scanner Management** | Added rename button to registered scanner cards |
| **Master Role** | Hidden in cloud mode (blex-master.service only runs in local mode) |

### Cloud Master (DGX)

| Issue | Fix |
|-------|-----|
| **Missing Auth Header** | `master.py` was never sending `Authorization: Bearer` header → got 401 on every `/watch` call |
| **Docker Env Injection** | `docker-compose.yml` wasn't injecting `BLEX_API_TOKEN` into container environment |
| **Build Failure** | Dockerfile had `COPY pyproject.toml` (doesn't exist) → every build failed |

### Web Dashboard Fixes

| Issue | Fix |
|-------|-----|
| **Route Missing** | Added `/blex` route to Caddy (accidentally removed) |
| **Asset Path Handling** | Changed `handle /asset*` → `handle_path /asset*` (wasn't stripping prefix) |
| **Zone Status Logic** | Now shows correct active/inactive based on 24-hour activity, not scanner status |
| **Logs History** | Returns full `LogEntry` shape (asset name, zone names, type) |
| **Detail Pages** | Added missing `GET /{id}` endpoints for assets/zones |
| **Notifications/Health** | Added health/summary endpoints (were 404ing) |

---

## Shortcuts Taken (Option A Fixes)

These are workarounds that need proper implementation. Each one can be tackled independently.

### TBD-001: Mode Sync to Server

**Current State:**
- `provisionMode` is app-local only (stored in `SettingsManager`)
- When provisioning, mode comes from the app switch, NOT the server
- Mode resets to "cloud" on app reinstall

**The Problem:**
- If you open the app on a different tablet, it won't know the current mode
- No single source of truth for device mode across instances

**Proper Implementation:**
```
User switches mode in app
  ↓
PATCH /api/tenants/{id}/mode { "mode": "local" | "cloud" }
  ↓
Backend updates DB → getTenantConfig returns correct mode
  ↓
buildProvisionBody uses server mode instead of app-local mode
```

**Work Required:**
- 1 new tenant-facing PATCH endpoint (~10 lines API)
- `ApiService.updateTenantMode()` method (~5 lines)
- Wire into mode switch confirmation dialogs
- **Effort:** ~1 hour
- **Risk:** Low — isolated endpoint, no cascade effects

---

### TBD-002: Sensitive Data in Plain SharedPreferences

**Current State:**
- `authToken`, `siteWifiPsk`, `mqttPassword`, `brokerPassword`, `remotePassword` stored in plain `SharedPreferences`
- Readable on rooted devices; no protection at rest

**Only Encrypted:**
- Device tokens ✓
- Refresh cookie ✓

**Proper Implementation:**
Migrate all sensitive fields to `securePrefs` (EncryptedSharedPreferences) in `SettingsManager`:
- Audit all `SharedPreferences` writes
- Move each sensitive field to encrypted storage
- Verify no references to old plain-text keys

**Work Required:**
- Scan `SettingsManager` for all credential storage
- Migrate ~5 sensitive fields
- Add migration logic for existing installs
- **Effort:** ~2 hours
- **Risk:** Medium — affects data persistence; needs testing on app upgrade

---

### TBD-003: Unauthenticated Tenant Registration

**Current State:**
- `POST /api/tenants/register` is completely unauthenticated
- Anyone can create a tenant
- **Evidence:** Audit agents created junk tenants `XKRPZA` and `T9DP6D` in production DB

**Proper Implementation:**

**Option A: Rate Limiting**
```python
@router.post("/register")
@rate_limit(requests=5, window=3600)  # 5 registrations per hour per IP
async def register_tenant(req: TenantRegister):
    # existing logic
```

**Option B: Signed Invite Codes**
- Admin generates invite code in dashboard → cryptographic signature
- User provides code during registration → backend validates signature
- Rate limiting + optional invite codes for extra control

**Database Cleanup:**
```sql
DELETE FROM shared.tenants WHERE tenant_id IN ('XKRPZA', 'T9DP6D');
```

**Work Required:**
- Rate limiting middleware (~30 lines)
- OR invite code signing system (~100 lines)
- Database cleanup script
- **Effort:** ~3 hours
- **Risk:** Low if rate limiting only; Medium if adding invite system

---

### TBD-004: OkHttp Migration for Android

**Current State:**
- All HTTP uses `HttpURLConnection` with custom `withRefresh {}` wrapper
- Works but lacks professional transport layer features

**Why It's Better:**
- Automatic connection pooling
- Interceptor chain for clean auth handling
- Built-in retry logic
- Easier testing with mock interceptors

**Proper Implementation:**
```kotlin
// Current approach
withRefresh {
    val response = HttpURLConnection.getInputStream()
}

// Target approach
val client = OkHttpClient.Builder()
    .authenticator(BearerTokenAuthenticator(settingsManager))
    .build()
```

**Work Required:**
- 20+ API methods need rewriting
- Create interceptor for Bearer token injection
- Create authenticator for 401 handling
- Update all `ApiService` methods
- **Effort:** ~8 hours
- **Risk:** Medium — large refactor, needs thorough testing

---

### TBD-005: Pi Image & Deployment Script

**Current State:**
- No `setup.sh` or `requirements.txt` tracked for fresh Pi deployment
- Code deployed manually to live Pi
- `requirements.txt` created on live Pi at `/home/blex/blex/requirements.txt` but NOT committed

**Missing Pieces:**
```
setup.sh should:
  ├─ Install Python dependencies
  ├─ Copy systemd services
  ├─ Create blex user & group
  ├─ Configure sudoers for blex-provisioner
  ├─ Create /etc/blex directory
  ├─ Generate blex.env from template
  └─ Set proper file permissions
```

**Proper Implementation:**

1. **Commit `requirements.txt`** from live Pi:
   ```bash
   scp pi@blex:/home/blex/blex/requirements.txt ./pi/requirements.txt
   git add pi/requirements.txt
   ```

2. **Create `pi/setup.sh`:**
   ```bash
   #!/bin/bash
   set -e
   
   # Install Python deps
   pip install -r requirements.txt
   
   # Copy systemd services
   sudo cp systemd/*.service /etc/systemd/system/
   sudo systemctl daemon-reload
   
   # Create blex user
   sudo useradd -m -s /bin/bash blex || true
   
   # Configure sudoers
   echo 'blex ALL=(ALL) NOPASSWD: /usr/bin/tee /etc/blex/blex.env' | sudo tee /etc/sudoers.d/blex-provisioner
   
   # Create directories
   sudo mkdir -p /etc/blex /home/blex/blex/logs
   sudo chown blex:blex /etc/blex /home/blex/blex/logs
   
   # Template env file
   sudo cp pi/blex.env.example /etc/blex/blex.env.example
   ```

3. **Test on fresh Pi image**

**Work Required:**
- Extract actual requirements from live Pi
- Write and test `setup.sh`
- Document manual Pi setup steps
- **Effort:** ~2 hours
- **Risk:** Low — script-only, can iterate

---

### TBD-006: Pi Image Creation Pipeline

**Current State:**
- No CI/CD for Pi images
- Manual process: Win32DiskImager + pishrink
- Images not versioned or tracked

**Proper Implementation:**

```yaml
# .github/workflows/build-pi-image.yml
name: Build Pi Image
on:
  push:
    tags: [v*]
permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Build with pi-gen
        uses: nakamura196/pi-gen-action@v0.2.0
        with:
          release: bookworm
          stage-list: stage0 stage1 stage2 stage3 stage4 stage5
          export-last-stage-only: true
      
      - name: Compress image with pishrink
        run: |
          wget https://raw.githubusercontent.com/Drewsif/PiShrink/master/pishrink.sh
          chmod +x pishrink.sh
          ./pishrink.sh -Z image.img
      
      - name: Upload to Release
        uses: softprops/action-gh-release@v1
        with:
          files: image.img.zip
          draft: false
```

**Work Required:**
- Research pi-gen workflow
- Configure stage directories for BleX pre-install
- Test image build in CI
- Document release process
- **Effort:** ~4 hours
- **Risk:** Medium — depends on pi-gen tool; needs testing

---

### TBD-007: Android Release Signing

**Current State:**
- Only debug APK can be built
- No keystore configured
- No release APK production capability

**Proper Implementation:**

1. **Create/Obtain Keystore:**
   ```bash
   keytool -genkey -v -keystore blex-release.keystore \
     -alias blex-release -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configure `keystore.properties`:**
   ```properties
   storeFile=../blex-release.keystore
   storePassword=<password>
   keyAlias=blex-release
   keyPassword=<password>
   ```

3. **Update `build.gradle` to use it:**
   ```gradle
   android {
       signingConfigs {
           release {
               def keystoreFile = rootProject.file('keystore.properties')
               def keystoreProperties = new Properties()
               keystoreProperties.load(new FileInputStream(keystoreFile))
               
               storeFile file(keystoreProperties['storeFile'])
               storePassword keystoreProperties['storePassword']
               keyAlias keystoreProperties['keyAlias']
               keyPassword keystoreProperties['keyPassword']
           }
       }
       buildTypes {
           release {
               signingConfig signingConfigs.release
           }
       }
   }
   ```

4. **GitHub Actions Workflow:**
   ```yaml
   on:
     push:
       tags: [v*]
   
   jobs:
     release:
       steps:
         - uses: actions/checkout@v4
         - name: Build signed APK
           run: ./gradlew assembleRelease
         - name: Upload to Release
           uses: softprops/action-gh-release@v1
           with:
             files: app/build/outputs/apk/release/app-release.apk
   ```

**Work Required:**
- Generate keystore (one-time)
- Add keystore to GitHub Secrets
- Configure CI workflow
- Update gradle build
- **Effort:** ~1.5 hours
- **Risk:** Low — straightforward Android build process

---

### TBD-008: UNIQUE Constraint on Zone-Scanner Association

**Current State:**
- UNIQUE constraint added ONLY to `t_sf5wu6.mst_zone_scanner`
- Other tenant schemas (`t_yyjf2n`, `t_rgm7c7`, etc.) missing constraint
- **Bug:** Scanners can be added multiple times to same zone

**Affected Schemas:**
```
t_sf5wu6     ✓ Has UNIQUE constraint
t_yyjf2n     ✗ Missing
t_rgm7c7     ✗ Missing
(+ any future tenant schemas)
```

**Proper Implementation:**

```sql
-- Migration script: migrations/add_zone_scanner_unique.sql

DO $$
DECLARE
    tenant_schema TEXT;
BEGIN
    -- Find all tenant schemas
    FOR tenant_schema IN
        SELECT schema_name 
        FROM information_schema.schemata 
        WHERE schema_name ~ '^t_[a-z0-9]+$'
    LOOP
        -- Add UNIQUE constraint if it doesn't exist
        EXECUTE format(
            'ALTER TABLE %I.mst_zone_scanner 
             ADD CONSTRAINT %I UNIQUE (mst_zone_id, mst_scanner_id)',
            tenant_schema,
            tenant_schema || '_zone_scanner_unique'
        );
    END LOOP;
END $$;
```

**Also Update Tenant Registration:**
```python
# In POST /api/tenants/register — when creating new tenant schema

async def register_tenant(req: TenantRegister):
    tenant_id = generate_tenant_id()
    
    async with get_db() as db:
        # ... create tables ...
        
        # Add constraint during table creation
        await db.execute(f"""
            ALTER TABLE {tenant_id}.mst_zone_scanner 
            ADD CONSTRAINT {tenant_id}_zone_scanner_unique 
            UNIQUE (mst_zone_id, mst_scanner_id)
        """)
```

**Work Required:**
- Write migration script
- Test on all existing tenant schemas
- Update tenant registration logic
- **Effort:** ~1 hour
- **Risk:** Low — straightforward schema change

---

### TBD-009: Stale Systemd Service State

**Current State:**
- `blex-sage@blex-provisioner.service` shows as failed in systemd
- Leftover from yesterday's provisioner crash
- Non-functional but noisy

**One-Time Fix:**
```bash
ssh pi@blex
sudo systemctl reset-failed blex-sage@blex-provisioner.service
sudo systemctl status blex-sage@blex-provisioner.service
```

**Work Required:**
- Execute reset command on Pi
- Verify service status
- **Effort:** ~2 minutes

---

### TBD-010: Jira Tickets

| Ticket | Status | Action |
|--------|--------|--------|
| SIGP-2242 (BleX-1-2) | ✓ Complete | Close ticket |
| SIGP-2243 (BleX-1-3) | ✓ Complete | Close ticket |
| SIGP-2247 (BleX-1-7) | ✓ Complete | Close ticket |

---

## Architecture Reference

### Token Flow

```
┌─────────────────────────────────────────────────────────────┐
│ Android App                                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  login() ──────→ POST /api/auth/login                      │
│                  ↓                                          │
│                 Returns: access_token (JWT, 15min)         │
│                         + refresh cookie (httpOnly, 7d)    │
│                  ↓                                          │
│            Store: accessToken in SettingsManager           │
│            Store: refreshCookie in EncryptedSharedPrefs    │
│                                                             │
│  Any API call gets 401                                     │
│            ↓                                                │
│  Catch 401 → withRefresh()                                 │
│            ↓                                                │
│  POST /api/auth/refresh (cookie sent automatically)        │
│            ↓                                                │
│  Returns: new access_token                                 │
│            ↓                                                │
│  Retry original request with new token                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Pi Device                                                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Register → POST /api/devices/provision                    │
│             ↓                                              │
│             Returns: device_token (opaque, sha256 hashed)  │
│             ↓                                              │
│             Store in EncryptedSharedPreferences            │
│             Keyed by MAC address (survives app update)     │
│             ↓                                              │
│  Provision → GET /api/tenants/{id}/config                 │
│             (uses device_token in Authorization header)    │
│             ↓                                              │
│  POST http://{pi_ip}:8888/provision                        │
│  Body: { tenant_id, mode, mqtt_*, api_token, role }        │
│             ↓                                              │
│  Pi writes /etc/blex/blex.env                              │
│  Pi runs /usr/local/bin/blex-flags.sh                      │
│  Pi restarts blex-scanner.service                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Tenant Schema Isolation

```
PostgreSQL Database
├── shared
│   ├── tenants         (tenant_id, tenant_name, created_at)
│   ├── users           (user_id, email, tenant_id, hashed_password)
│   ├── admins          (admin_id, email, hashed_password)
│   ├── devices         (device_id, device_token_hash, mac, tenant_id)
│   └── refresh_tokens  (token_id, family_id, user_id, revoked)
│
├── t_sf5wu6            (tenant schema #1)
│   ├── mst_zone
│   ├── mst_scanner
│   ├── mst_asset
│   ├── movement_log
│   └── mst_zone_scanner (with UNIQUE constraint ✓)
│
├── t_yyjf2n            (tenant schema #2)
│   ├── mst_zone
│   ├── mst_scanner
│   ├── mst_asset
│   ├── movement_log
│   └── mst_zone_scanner (MISSING UNIQUE constraint ✗)
│
└── public              (legacy, unused)
```

**Key Isolation Mechanism:**
```python
# get_principal_db() in auth.py

async def get_principal_db(token: str) -> Dict:
    principal = decode_jwt(token)  # Contains tenant_id
    tenant_id = principal["tenant_id"]
    
    # Every database query runs in tenant's schema
    async with get_db() as db:
        await db.execute(f"SET search_path TO t_{tenant_id}, public")
        # All queries now default to tenant schema
        
    return principal
```

**Critical:** `tenant_id` comes from the JWT token, NEVER from caller-supplied headers like `X-Tenant-ID`.

### Pi Provisioning Flow

```
┌──────────────────────────────────────────────────────────┐
│ Step 1: Register Device                                  │
├──────────────────────────────────────────────────────────┤
│                                                          │
│ Android: User taps "Register New Pi"                    │
│          ↓                                               │
│ POST /api/devices/provision                             │
│ Headers: Authorization: Bearer {user_jwt}               │
│ Body: { mac: "AA:BB:CC:DD:EE:FF" }                     │
│          ↓                                               │
│ API: Generates device_token (random 32-char string)     │
│      Stores: sha256(device_token) in shared.devices     │
│      Returns: device_token (once, never shown again)    │
│          ↓                                               │
│ Android: Stores device_token in EncryptedSharedPrefs    │
│          Keyed by MAC + "_device_token"                 │
│          (survives app reinstall on same device)        │
│                                                          │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ Step 2: Get Configuration                                │
├──────────────────────────────────────────────────────────┤
│                                                          │
│ Android: GET /api/tenants/{tenant_id}/config            │
│          Headers: Authorization: Bearer {device_token}   │
│          ↓                                               │
│ API: get_principal_db(device_token)                      │
│      - Validates token_hash against shared.devices       │
│      - Derives tenant_id from device record              │
│      - Sets search_path to t_{tenant_id}                │
│          ↓                                               │
│ Returns:                                                 │
│ {                                                        │
│   tenant_id: "t_sf5wu6",                                 │
│   mqtt_broker: "sigmatic-asc.tech",                      │
│   mqtt_port: 8883,                                       │
│   mqtt_username: "blex_t_sf5wu6",                        │
│   mqtt_password: "..." (from secrets),                   │
│   mode: "cloud" | "local",                              │
│   role: "scanner" | "master"                            │
│ }                                                        │
│                                                          │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ Step 3: Provision Pi                                     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│ Android: Builds provisionBody from config               │
│          ↓                                               │
│ HTTP POST http://{pi_local_ip}:8888/provision           │
│ Body:                                                    │
│ {                                                        │
│   tenant_id: "t_sf5wu6",                                 │
│   api_token: "{device_token}",                          │
│   mqtt_broker: "sigmatic-asc.tech",                      │
│   mqtt_port: 8883,                                       │
│   mqtt_username: "blex_t_sf5wu6",                        │
│   mqtt_password: "...",                                  │
│   mode: "cloud",                                         │
│   role: "scanner"                                        │
│ }                                                        │
│          ↓                                               │
│ Pi: Receives request in blex-provisioner                │
│     Writes /etc/blex/blex.env:                           │
│     ├─ TENANT_ID=t_sf5wu6                               │
│     ├─ BLEX_API_TOKEN={device_token}                    │
│     ├─ MQTT_BROKER=sigmatic-asc.tech                    │
│     ├─ MQTT_PORT=8883                                   │
│     ├─ MQTT_USERNAME=blex_t_sf5wu6                      │
│     ├─ MQTT_PASSWORD=...                                │
│     ├─ MODE=cloud                                        │
│     └─ ROLE=scanner                                      │
│          ↓                                               │
│     Runs: /usr/local/bin/blex-flags.sh                   │
│     (loads environment, sets systemd variables)          │
│          ↓                                               │
│     systemctl restart blex-scanner@blex-provisioner     │
│          ↓                                               │
│ Pi starts blex-scanner:                                  │
│   - Reads /etc/blex/blex.env                             │
│   - Connects to MQTT broker (sigmatic-asc.tech:8883)    │
│   - Uses device_token for API calls to DGX               │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### Mode Behavior

```
┌────────────────────────────────────────────────────┐
│ CLOUD MODE                                         │
├────────────────────────────────────────────────────┤
│                                                    │
│ Pi Device:                                         │
│   └─ blex-scanner                                  │
│      └─ Publishes movement events to:             │
│         MQTT: sigmatic-asc.tech:8883              │
│              (remote cloud broker)                │
│                                                    │
│ DGX Master (sigmatic-asc.tech):                   │
│   └─ master_engine container                      │
│      ├─ Subscribes to MQTT                        │
│      ├─ Runs zone logic (movement aggregation)    │
│      └─ Updates DB with zone events               │
│                                                    │
│ Pi Systemd:                                        │
│   ├─ blex-scanner.service    → ACTIVE ✓           │
│   ├─ blex-master.service     → INACTIVE ✓         │
│   ├─ redis.service           → INACTIVE ✓         │
│   └─ blex-fifo-consumer      → INACTIVE ✓         │
│                                                    │
│ Use Case:                                          │
│   Multiple sites → One cloud aggregator           │
│   Pi is lightweight sensor only                    │
│                                                    │
└────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│ LOCAL MODE                                         │
├────────────────────────────────────────────────────┤
│                                                    │
│ Pi Device:                                         │
│   ├─ blex-scanner                                  │
│   │  └─ Publishes movement events to:             │
│   │     MQTT: 127.0.0.1:1883                      │
│   │          (local broker on same Pi)            │
│   │                                                │
│   ├─ mosquitto.service                             │
│   │  └─ Local MQTT broker                         │
│   │                                                │
│   ├─ redis.service                                 │
│   │  └─ In-memory event cache                     │
│   │                                                │
│   ├─ blex-fifo-consumer                            │
│   │  └─ Reads from FIFO queue                     │
│   │     Writes movement_log entries               │
│   │                                                │
│   └─ blex-master.service                           │
│      ├─ Subscribes to MQTT                        │
│      ├─ Runs zone logic (movement aggregation)    │
│      └─ Updates DB with zone events               │
│                                                    │
│ Pi Systemd:                                        │
│   ├─ blex-scanner.service     → ACTIVE ✓          │
│   ├─ blex-master.service      → ACTIVE ✓          │
│   ├─ redis.service            → ACTIVE ✓          │
│   └─ blex-fifo-consumer       → ACTIVE ✓          │
│                                                    │
│ Use Case:                                          │
│   Single isolated site (lab, warehouse)           │
│   Pi runs complete stack independently            │
│   No cloud dependency                              │
│                                                    │
└────────────────────────────────────────────────────┘
```

**Key Difference:**
- **Cloud Mode:** MQTT traffic goes to remote broker → remote master logic
- **Local Mode:** MQTT traffic stays local → local master logic runs on same Pi

The `MODE` environment variable in `/etc/blex/blex.env` determines which systemd services start.

---

## Prioritization for Next Sprint

### Critical (Blocks Production)
1. **TBD-003:** Rate limit `POST /api/tenants/register` — prevent abuse
2. **TBD-008:** Add UNIQUE constraint to ALL tenant schemas — prevent duplicate scanners in zones
3. **TBD-009:** Reset failed systemd service — cleanup

### High (Security/UX Impact)
4. **TBD-002:** Migrate credentials to encrypted storage — eliminate rooted device risk
5. **TBD-001:** Mode sync to server — single source of truth

### Medium (Infrastructure)
6. **TBD-005:** Commit Pi deployment script — reproducible setup
7. **TBD-006:** Pi image CI/CD pipeline — automate releases
8. **TBD-007:** Android signing — production APK capability

### Low (Technical Debt)
9. **TBD-004:** OkHttp migration — cleaner transport layer
10. **TBD-010:** Close Jira tickets — admin cleanup

---

## Quick Reference: Key Files Modified

### Backend (FastAPI)
- `api/auth.py` — JWT generation, token validation, `get_principal()`
- `api/tenants.py` — unified login, config endpoint, merged tenants_config
- `api/devices.py` — `POST /api/devices/provision` endpoint
- `api/middleware.py` — `get_principal_db()` for schema isolation

### Android App
- `SettingsManager.kt` — token storage, refresh cookie persistence
- `ApiService.kt` — `withRefresh {}` wrapper on all API calls
- `ZonesTab.kt` — display assigned scanners
- `ScannerFragment.kt` — rename button, MQTT client ID generation

### Pi
- `/etc/blex/blex.env` — environment configuration (source of truth)
- `/usr/local/bin/blex-flags.sh` — loads env, sets systemd variables
- `blex-provisioner` — writes config, manages sudoers

### Dashboard
- `caddy/Caddyfile` — fixed `/blex` route, `handle_path` for assets
- `dashboard/pages/zones.ts` — 24-hour activity logic
- `dashboard/api/logs.ts` — full LogEntry responses

---

## Notes for Future Developer

1. **Never trust caller-supplied tenant ID.** Always derive from authenticated token.
2. **Device tokens are one-time use.** After provisioning, the token is stored locally on the device; it's never transmitted again (except in API calls as Bearer token).
3. **Refresh tokens use family revocation.** If a refresh token is reused, the entire family is revoked — this blocks token theft attempts.
4. **Mode is currently client-driven.** TBD-001 will change this to server-driven, but until then, each device has independent mode state.
5. **Pi requires manual setup.** There's no golden image yet (see TBD-005 and TBD-006). Fresh Pi deployments require running setup steps manually.
6. **MQTT credentials should match tenant ID.** Username pattern: `blex_{tenant_id}`. If they don't match, scanners can't authenticate to the broker.

---

## Session Summary

- **Security hardening:** JWT auth, token revocation, device token provisioning ✓
- **Android app:** Token refresh, encrypted storage, MQTT tuning, zone display ✓
- **Pi provisioning:** Fixed paths, credentials, permissions ✓
- **Cloud master:** Fixed auth headers, docker env, build errors ✓
- **Web dashboard:** Fixed routes, endpoints, zone status logic ✓

**Total shortcuts:** 10 items spanning security, infrastructure, and tech debt  
**Estimated cleanup effort:** ~20–25 hours spread across next 2–3 sprints  
**Risk level:** Low to Medium depending on item
