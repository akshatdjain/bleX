# BleX API Layer - Complete Reference

**Document Version:** 1.0  
**Last Updated:** 16th of April 2026  
**Status:** Production  
**Classification:** Internal

---

## Table of Contents
1. [API Overview](#api-overview)
2. [Authentication](#authentication)
3. [Movement Events API](#movement-events-api)
4. [Assets API](#assets-api)
5. [Zones API](#zones-api)
6. [Scanners API](#scanners-api)
7. [Runtime Configuration API](#runtime-configuration-api)
8. [Health Monitoring API](#health-monitoring-api)
9. [Error Responses](#error-responses)
10. [Code Examples](#code-examples)

---

## API Overview

### Base Information

**Base URL**: `http://api.blex.example.com` (production)  
**Base URL**: `http://localhost:8000` (development)  
**API Version**: v1.0.0  
**Framework**: FastAPI  
**Protocol**: HTTP/HTTPS  
**Data Format**: JSON  
**Documentation**: `/docs` (Swagger UI), `/redoc` (ReDoc)

### Technology Stack
- **Framework**: FastAPI 0.100+
- **Server**: Uvicorn (ASGI)
- **Database**: PostgreSQL 14+ (async via asyncpg)
- **ORM**: SQLAlchemy 2.0+ (async)
- **Validation**: Pydantic 2.0+

### API Design Principles
1. **RESTful**: Standard HTTP methods (GET, POST, PUT, DELETE)
2. **Async First**: All endpoints use async/await
3. **Type Safe**: Pydantic schema validation
4. **Self-Documenting**: Automatic OpenAPI generation
5. **CORS Enabled**: Cross-origin requests allowed

### Quick Start

**Access API Documentation**:
```bash
# Swagger UI (interactive)
open http://localhost:8000/docs

# ReDoc (documentation)
open http://localhost:8000/redoc

# OpenAPI JSON schema
curl http://localhost:8000/openapi.json
```

**Health Check**:
```bash
curl http://localhost:8000/health
# Response: {"status": "ok"}
```

---

## Authentication

### Current State
**Authentication**: None (trusted network)  
**Authorization**: None (all endpoints public)

### Planned (v3.1.0)
**Method**: JWT (JSON Web Tokens)  
**Header**: `Authorization: Bearer <token>`

**Future Login Flow**:
```bash
# Login
POST /api/auth/login
{
  "username": "admin",
  "password": "password"
}
# Response: {"access_token": "eyJ...", "token_type": "bearer"}

# Use token
GET /api/assets
Authorization: Bearer eyJ...
```

---

## Movement Events API

### POST /api/asset/movement

**Purpose**: Submit a confirmed zone-change event from Android tablet or master controller.

**Called By**: Android Tablet, Master Pi Controller  
**Frequency**: Every time an asset changes zones (typically every 1-10 minutes per asset)  
**Database Impact**: Inserts into `movement_log`, updates `mst_asset.current_zone_id`

**Request**:
```http
POST /api/asset/movement
Content-Type: application/json

{
  "asset_mac": "AC:23:3F:A1:B2:C3",
  "from_zone_id": 1,
  "to_zone_id": 2,
  "state": "ZONE",
  "deciding_rssi": -62.5,
  "timestamp": "2024-12-19T10:30:45Z"
}
```

**Request Schema** (`MovementIn`):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `asset_mac` | string | Yes | Beacon MAC address (case-insensitive) |
| `from_zone_id` | integer | No | Previous zone ID (null if first detection) |
| `to_zone_id` | integer | No | New zone ID (null if EXIT) |
| `state` | string | Yes | "ZONE" (entered zone) or "EXIT" (left facility) |
| `deciding_rssi` | float | No | RSSI that triggered the movement (-100 to -30) |
| `timestamp` | string | Yes | ISO8601 timestamp (UTC) |

**Response** (200 OK):
```json
{
  "ok": true,
  "detail": "zone updated → 2"
}
```

**Response** (200 OK - Asset Not Registered):
```json
{
  "ok": true,
  "detail": "asset not registered, ignored"
}
```

**Response** (400 Bad Request):
```json
{
  "detail": "Invalid timestamp format"
}
```

**Response** (500 Internal Server Error):
```json
{
  "detail": "movement_log insert failed"
}
```

**Business Logic**:
1. Parse and validate timestamp
2. Normalize MAC address (uppercase)
3. Check if asset is registered in `mst_asset`
4. If not registered → return success but don't log (whitelist filter)
5. Insert movement event into `movement_log`
6. Update `mst_asset.current_zone_id`:
   - If state="EXIT" → set to NULL
   - If state="ZONE" → set to `to_zone_id`
7. Update `mst_asset.last_movement_dt` to timestamp

**Example (cURL)**:
```bash
curl -X POST http://localhost:8000/api/asset/movement \
  -H "Content-Type: application/json" \
  -d '{
    "asset_mac": "AC:23:3F:A1:B2:C3",
    "from_zone_id": 1,
    "to_zone_id": 2,
    "state": "ZONE",
    "deciding_rssi": -62.5,
    "timestamp": "2024-12-19T10:30:45Z"
  }'
```

**Example (Python)**:
```python
import requests
from datetime import datetime, timezone

response = requests.post(
    "http://localhost:8000/api/asset/movement",
    json={
        "asset_mac": "AC:23:3F:A1:B2:C3",
        "from_zone_id": 1,
        "to_zone_id": 2,
        "state": "ZONE",
        "deciding_rssi": -62.5,
        "timestamp": datetime.now(timezone.utc).isoformat()
    }
)
print(response.json())
```

---

### GET /api/assets/current

**Purpose**: Get current location of all assets (live view).

**Called By**: Web Dashboard, Mobile App  
**Frequency**: Every 5-30 seconds (polling)  
**Database Query**: `SELECT * FROM mst_asset WHERE current_zone_id IS NOT NULL`

**Request**:
```http
GET /api/assets/current
```

**Response** (200 OK):
```json
[
  {
    "mac": "AC:23:3F:A1:B2:C3",
    "zone": 2,
    "last_seen": "2024-12-19T10:30:45Z",
    "rssi": -62
  },
  {
    "mac": "AC:23:3F:D4:E5:F6",
    "zone": 1,
    "last_seen": "2024-12-19T10:29:12Z",
    "rssi": -58
  }
]
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `mac` | string | Beacon MAC address |
| `zone` | integer | Current zone ID |
| `last_seen` | string | ISO8601 timestamp of last movement |
| `rssi` | integer | RSSI from extra JSON field (default -99) |

**Notes**:
- Only returns assets currently in a zone (not exited)
- RSSI is extracted from `mst_asset.extra` JSON field
- Returns empty array `[]` on error (graceful degradation)

**Example (cURL)**:
```bash
curl http://localhost:8000/api/assets/current
```

---

### GET /api/assets/history

**Purpose**: Get recent movement events (audit trail).

**Called By**: Web Dashboard, Reports  
**Frequency**: On-demand  
**Database Query**: `SELECT * FROM movement_log ORDER BY timestamp_movement DESC LIMIT 50`

**Request**:
```http
GET /api/assets/history
```

**Response** (200 OK):
```json
[
  {
    "id": 12345,
    "mac": "AC:23:3F:A1:B2:C3",
    "from_zone": 1,
    "to_zone": 2,
    "timestamp": "2024-12-19T10:30:45Z"
  },
  {
    "id": 12344,
    "mac": "AC:23:3F:D4:E5:F6",
    "from_zone": 3,
    "to_zone": 1,
    "timestamp": "2024-12-19T10:28:12Z"
  }
]
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `id` | integer | Movement log entry ID |
| `mac` | string | Beacon MAC address |
| `from_zone` | integer | Previous zone ID (null if first) |
| `to_zone` | integer | New zone ID (null if exit) |
| `timestamp` | string | ISO8601 timestamp of movement |

**Limitations**:
- Returns last 50 events only (hardcoded limit)
- No pagination (future enhancement)
- No filtering by asset or date (future enhancement)

---

## Assets API

### GET /api/assets

**Purpose**: List all registered assets.

**Request**:
```http
GET /api/assets
```

**Response** (200 OK):
```json
[
  {
    "id": 1,
    "bluetooth_id": "AC:23:3F:A1:B2:C3",
    "asset_name": "Wheelchair 01",
    "current_zone_id": 2
  },
  {
    "id": 2,
    "bluetooth_id": "AC:23:3F:D4:E5:F6",
    "asset_name": "IV Pump 03",
    "current_zone_id": null
  }
]
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `id` | integer | Asset internal ID |
| `bluetooth_id` | string | Beacon MAC address (uppercase) |
| `asset_name` | string | Human-readable name |
| `current_zone_id` | integer | Current zone ID (null if not in facility) |

---

### POST /api/assets

**Purpose**: Register a new asset (beacon).

**Request**:
```http
POST /api/assets
Content-Type: application/json

{
  "bluetooth_id": "AC:23:3F:A1:B2:C3",
  "asset_name": "Wheelchair 01"
}
```

**Request Schema** (`AssetIn`):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `bluetooth_id` | string | Yes | Beacon MAC address |
| `asset_name` | string | No | Human-readable name |

**Response** (200 OK):
```json
{
  "ok": true,
  "id": 1,
  "bluetooth_id": "AC:23:3F:A1:B2:C3"
}
```

**Response** (409 Conflict):
```json
{
  "detail": "Asset already registered"
}
```

**Business Logic**:
1. Normalize MAC address (uppercase)
2. Check if already exists
3. Insert into `mst_asset`
4. Return new asset ID

---

### PUT /api/assets/{asset_id}

**Purpose**: Update asset details.

**Request**:
```http
PUT /api/assets/1
Content-Type: application/json

{
  "bluetooth_id": "AC:23:3F:A1:B2:C3",
  "asset_name": "Wheelchair 01 - Updated"
}
```

**Response** (200 OK):
```json
{
  "ok": true,
  "id": 1,
  "asset_name": "Wheelchair 01 - Updated"
}
```

**Response** (404 Not Found):
```json
{
  "detail": "Asset not found"
}
```

---

### DELETE /api/assets/{asset_id}

**Purpose**: Unregister an asset.

**Request**:
```http
DELETE /api/assets/1
```

**Response** (200 OK):
```json
{
  "ok": true
}
```

**Response** (404 Not Found):
```json
{
  "detail": "Asset not found"
}
```

**Warning**: This does NOT delete movement history. Only removes from asset registry.

---

## Zones API

### GET /api/zones

**Purpose**: List all zones with assigned scanners.

**Request**:
```http
GET /api/zones
```

**Response** (200 OK):
```json
[
  {
    "id": 1,
    "zone_name": "Warehouse A",
    "description": "Main storage area",
    "scanners": [
      {
        "id": 1,
        "mac": "b8:27:eb:12:34:56",
        "name": "Scanner-01",
        "type": "pi"
      },
      {
        "id": 2,
        "mac": "b8:27:eb:78:90:ab",
        "name": "Scanner-02",
        "type": "pi"
      }
    ]
  },
  {
    "id": 2,
    "zone_name": "Warehouse B",
    "description": "Secondary storage",
    "scanners": []
  }
]
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `id` | integer | Zone ID |
| `zone_name` | string | Zone name |
| `description` | string | Zone description (nullable) |
| `scanners` | array | Array of assigned scanners |
| `scanners[].id` | integer | Scanner ID |
| `scanners[].mac` | string | Scanner MAC address |
| `scanners[].name` | string | Scanner name |
| `scanners[].type` | string | "pi" or "esp32" |

---

### POST /api/zones

**Purpose**: Create a new zone.

**Request**:
```http
POST /api/zones
Content-Type: application/json

{
  "zone_name": "Warehouse C",
  "description": "Overflow storage"
}
```

**Request Schema** (`ZoneIn`):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `zone_name` | string | Yes | Zone name |
| `description` | string | No | Zone description |

**Response** (200 OK):
```json
{
  "ok": true,
  "id": 3,
  "zone_name": "Warehouse C"
}
```

**Side Effect**: Triggers `notify_zone_map_changed()` event (long-polling watchers notified)

---

### PUT /api/zones/{zone_id}

**Purpose**: Update zone details.

**Request**:
```http
PUT /api/zones/1
Content-Type: application/json

{
  "zone_name": "Warehouse A - Updated",
  "description": "Main storage area (expanded)"
}
```

**Response** (200 OK):
```json
{
  "ok": true,
  "id": 1,
  "zone_name": "Warehouse A - Updated"
}
```

**Response** (404 Not Found):
```json
{
  "detail": "Zone not found"
}
```

**Side Effect**: Triggers `notify_zone_map_changed()` event

---

### DELETE /api/zones/{zone_id}

**Purpose**: Delete a zone.

**Request**:
```http
DELETE /api/zones/1
```

**Response** (200 OK):
```json
{
  "ok": true
}
```

**Response** (404 Not Found):
```json
{
  "detail": "Zone not found"
}
```

**Cascade Behavior**:
1. Deletes all entries in `mst_zone_scanner` (scanner assignments)
2. Sets `mst_asset.current_zone_id = NULL` for all assets in this zone
3. Sets `movement_log.from_zone_id = NULL` for all movements from this zone
4. Sets `movement_log.to_zone_id = NULL` for all movements to this zone
5. Deletes the zone from `mst_zone`
6. Triggers `notify_zone_map_changed()` event

**Warning**: Movement history is preserved (nullified, not deleted)

---

### POST /api/zones/{zone_id}/scanners

**Purpose**: Assign a scanner to a zone.

**Request**:
```http
POST /api/zones/1/scanners
Content-Type: application/json

{
  "scanner_id": 2
}
```

**Request Schema** (`ZoneScannerIn`):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `scanner_id` | integer | Yes | Scanner ID to assign |

**Response** (200 OK):
```json
{
  "ok": true
}
```

**Response** (200 OK - Already Assigned):
```json
{
  "ok": true,
  "detail": "already assigned"
}
```

**Response** (404 Not Found):
```json
{
  "detail": "Zone not found"
}
```

**Business Logic**:
1. Verify zone exists
2. Verify scanner exists
3. Check if already assigned (idempotent)
4. Insert into `mst_zone_scanner`
5. Trigger `notify_zone_map_changed()` event

**Side Effect**: Master Pi controller receives zone map update via long-polling

---

### DELETE /api/zones/{zone_id}/scanners/{scanner_id}

**Purpose**: Unassign a scanner from a zone.

**Request**:
```http
DELETE /api/zones/1/scanners/2
```

**Response** (200 OK):
```json
{
  "ok": true
}
```

**Business Logic**:
1. Delete from `mst_zone_scanner` (idempotent - no error if not assigned)
2. Trigger `notify_zone_map_changed()` event

---

## Scanners API

### GET /api/scanners

**Purpose**: List all registered scanners.

**Request**:
```http
GET /api/scanners
```

**Response** (200 OK):
```json
[
  {
    "id": 1,
    "mac_id": "b8:27:eb:12:34:56",
    "name": "Scanner-01",
    "type": "pi"
  },
  {
    "id": 2,
    "mac_id": "24:6f:28:cd:ef:01",
    "name": "Scanner-02",
    "type": "esp32"
  }
]
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `id` | integer | Scanner internal ID |
| `mac_id` | string | Scanner MAC address (uppercase) |
| `name` | string | Human-readable name |
| `type` | string | "pi" or "esp32" |

---

### POST /api/scanners

**Purpose**: Register a new scanner.

**Request**:
```http
POST /api/scanners
Content-Type: application/json

{
  "mac_id": "b8:27:eb:12:34:56",
  "name": "Scanner-01",
  "type": "pi"
}
```

**Request Schema** (`ScannerIn`):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `mac_id` | string | Yes | Scanner MAC address |
| `name` | string | No | Human-readable name |
| `type` | string | No | "pi" or "esp32" |

**Response** (200 OK):
```json
{
  "ok": true,
  "id": 1,
  "mac_id": "B8:27:EB:12:34:56"
}
```

**Response** (409 Conflict):
```json
{
  "detail": "Scanner already registered"
}
```

---

### PUT /api/scanners/by-mac/{mac}

**Purpose**: Register or update a scanner by MAC address (upsert).

**Request**:
```http
PUT /api/scanners/by-mac/b8:27:eb:12:34:56
Content-Type: application/json

{
  "mac_id": "b8:27:eb:12:34:56",
  "name": "Scanner-01-Updated",
  "type": "pi"
}
```

**Response** (200 OK - Created):
```json
{
  "ok": true,
  "id": 1,
  "mac_id": "B8:27:EB:12:34:56",
  "updated": false
}
```

**Response** (200 OK - Updated):
```json
{
  "ok": true,
  "id": 1,
  "mac_id": "B8:27:EB:12:34:56",
  "updated": true
}
```

**Business Logic**:
1. Normalize MAC address (uppercase)
2. Check if exists
3. If exists: Update name and type (if provided)
4. If not exists: Create new scanner
5. Return `updated` boolean flag

**Use Case**: Auto-registration by scanners on boot

---

### DELETE /api/scanners/{scanner_id}

**Purpose**: Unregister a scanner.

**Request**:
```http
DELETE /api/scanners/1
```

**Response** (200 OK):
```json
{
  "ok": true
}
```

**Response** (404 Not Found):
```json
{
  "detail": "Scanner not found"
}
```

**Cascade Behavior**:
1. Deletes all entries in `mst_zone_scanner` (zone assignments)
2. Deletes the scanner from `mst_scanner`
3. Triggers `notify_zone_map_changed()` event

---

## Runtime Configuration API

### GET /api/runtime/scanner-zone-map

**Purpose**: Get scanner-to-zone mapping for decision engine.

**Called By**: Master Pi Controller (on startup)  
**Frequency**: Once on startup, then via long-polling  
**Database Query**: JOIN mst_scanner, mst_zone_scanner, mst_zone

**Request**:
```http
GET /api/runtime/scanner-zone-map
```

**Response** (200 OK):
```json
{
  "scanner_zone_map": {
    "B8:27:EB:12:34:56": 1,
    "B8:27:EB:78:90:AB": 2,
    "24:6F:28:CD:EF:01": 3
  },
  "version": 5
}
```

**Response Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `scanner_zone_map` | object | MAP: scanner MAC → zone ID |
| `version` | integer | Global version counter (incremented on change) |

**Use Case**: Master controller loads this on boot to know which scanner belongs to which zone

---

### GET /api/runtime/scanner-zone-map/watch

**Purpose**: Long-polling endpoint for zone map changes.

**Called By**: Master Pi Controller (continuous loop)  
**Frequency**: Continuous (hangs until change)  
**Timeout**: 60 seconds (returns current map if no change)

**Request**:
```http
GET /api/runtime/scanner-zone-map/watch?version=5
```

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `version` | integer | No | Current version (default 0) |

**Response** (200 OK - Immediate if version mismatch):
```json
{
  "scanner_zone_map": {
    "B8:27:EB:12:34:56": 1,
    "B8:27:EB:78:90:AB": 2,
    "24:6F:28:CD:EF:01": 3
  },
  "version": 6
}
```

**Response** (200 OK - After 60s timeout or change event):
Same as above

**How It Works**:
1. Master sends current version
2. If version != server version → return immediately
3. If version == server version → wait for `zone_map_event.wait()` (max 60s)
4. When zone map changes (zone added/deleted, scanner assigned/unassigned) → event fires
5. All watchers wake up and receive new map

**Master Loop**:
```python
import requests

current_version = 0
while True:
    response = requests.get(
        "http://api:8000/api/runtime/scanner-zone-map/watch",
        params={"version": current_version},
        timeout=65  # Slightly longer than server timeout
    )
    data = response.json()
    scanner_zone_map = data["scanner_zone_map"]
    current_version = data["version"]
    update_local_mappings(scanner_zone_map)
```

---

### POST /api/runtime/master

**Purpose**: Master controller registers its IP address.

**Called By**: Master Pi (on boot, on IP change)  
**Frequency**: Once on startup, then on network change  
**Database Impact**: Inserts/updates `mst_master` table

**Request**:
```http
POST /api/runtime/master
Content-Type: application/json

{
  "role": "master",
  "mac": "b8:27:eb:aa:bb:cc",
  "ip": "192.168.1.50",
  "timestamp": "2024-12-19T10:30:45Z"
}
```

**Request Schema** (`MasterRegisterIn`):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `role` | string | Yes | "master" |
| `mac` | string | Yes | Master MAC address |
| `ip` | string | Yes | Master IP address |
| `timestamp` | string | Yes | ISO8601 timestamp |

**Response** (200 OK):
```json
{
  "ok": true,
  "master_ip": "192.168.1.50"
}
```

**Business Logic**:
1. Normalize MAC address (uppercase)
2. Check if master with this MAC exists
3. If exists and IP changed → update IP, trigger `notify_master_ip_changed()` event
4. If not exists → create new master, trigger event
5. Scanners watching `/api/runtime/master/watch` wake up and get new IP

---

### GET /api/runtime/master

**Purpose**: Get current master IP address.

**Called By**: Scanners (on boot)  
**Frequency**: Once on startup

**Request**:
```http
GET /api/runtime/master
```

**Response** (200 OK):
```json
{
  "ok": true,
  "master_ip": "192.168.1.50"
}
```

**Response** (404 Not Found):
```json
{
  "detail": "Master not registered yet"
}
```

---

### GET /api/runtime/master/watch

**Purpose**: Long-polling endpoint for master IP changes.

**Called By**: Scanners (continuous loop)  
**Frequency**: Continuous (hangs until IP changes)  
**Timeout**: 60 seconds

**Request**:
```http
GET /api/runtime/master/watch?current_ip=192.168.1.50
```

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `current_ip` | string | Yes | Current known master IP |

**Response** (200 OK - Immediate if IP different):
```json
{
  "ok": true,
  "master_ip": "192.168.1.51"
}
```

**Response** (200 OK - After timeout or change event):
Same as above

**How It Works**:
1. Scanner sends current known IP
2. If IP in DB != current_ip → return immediately
3. If IP matches → wait for `master_ip_event.wait()` (max 60s)
4. When master IP changes → event fires
5. All watchers wake up and receive new IP

**Scanner Loop**:
```python
import requests
import paho.mqtt.client as mqtt

current_master_ip = "192.168.1.50"
mqtt_client = mqtt.Client()

while True:
    response = requests.get(
        "http://api:8000/api/runtime/master/watch",
        params={"current_ip": current_master_ip},
        timeout=65
    )
    data = response.json()
    new_ip = data["master_ip"]
    
    if new_ip != current_master_ip:
        # Master IP changed - reconnect MQTT
        mqtt_client.disconnect()
        mqtt_client.connect(new_ip, 1883)
        current_master_ip = new_ip
```

---

### POST /api/runtime/scanner

**Purpose**: Scanner registers on boot and gets master IP.

**Called By**: Scanners (on boot)  
**Frequency**: Once on startup

**Request**:
```http
POST /api/runtime/scanner
Content-Type: application/json

{
  "role": "scanner",
  "mac": "b8:27:eb:12:34:56",
  "ip": "192.168.1.101",
  "scanner_type": "pi",
  "timestamp": "2024-12-19T10:30:45Z"
}
```

**Request Schema** (`ScannerRegisterIn`):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `role` | string | Yes | "scanner" |
| `mac` | string | Yes | Scanner MAC address |
| `ip" | string | Yes | Scanner IP address |
| `scanner_type` | string | Yes | "pi" or "esp32" |
| `timestamp` | string | Yes | ISO8601 timestamp |

**Response** (200 OK):
```json
{
  "ok": true,
  "master_ip": "192.168.1.50"
}
```

**Response** (404 Not Found):
```json
{
  "detail": "Master not registered yet"
}
```

**Business Logic**:
1. Log scanner boot event (optional)
2. Return current master IP from `mst_master` table
3. Scanner uses this IP to connect to MQTT broker

---

## Health Monitoring API

### POST /api/health/scanners/bulk

**Purpose**: Bulk update scanner heartbeat timestamps.

**Called By**: Master Pi (every 5 minutes)  
**Frequency**: Every 5 minutes  
**Database Impact**: Updates `mst_scanner.last_heartbeat`

**Request**:
```http
POST /api/health/scanners/bulk
Content-Type: application/json

[
  {
    "scanner_mac": "b8:27:eb:12:34:56",
    "zone_id": 1,
    "is_online": true,
    "last_seen_ago_sec": 30
  },
  {
    "scanner_mac": "24:6f:28:cd:ef:01",
    "zone_id": 2,
    "is_online": false,
    "last_seen_ago_sec": 600
  }
]
```

**Request Schema** (`ScannerHealthItem[]`):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `scanner_mac` | string | Yes | Scanner MAC address |
| `zone_id` | integer | No | Assigned zone ID |
| `is_online` | boolean | Yes | Whether scanner is online |
| `last_seen_ago_sec` | integer | No | Seconds since last MQTT message |

**Response** (200 OK):
```json
{
  "ok": true,
  "updated": 2,
  "total": 2
}
```

**Business Logic**:
1. For each scanner:
   - Calculate `last_heartbeat` = now - `last_seen_ago_sec`
   - Update `mst_scanner.last_heartbeat` (raw SQL for safety)
2. Return count of updated scanners
3. Gracefully handles missing `last_heartbeat` column (for older schemas)

---

### POST /api/health/beacons/bulk

**Purpose**: Bulk update beacon battery and last-seen timestamps.

**Called By**: Master Pi (every 5 minutes)  
**Frequency**: Every 5 minutes  
**Database Impact**: Updates `mst_asset.extra` JSON field

**Request**:
```http
POST /api/health/beacons/bulk
Content-Type: application/json

[
  {
    "asset_mac": "AC:23:3F:A1:B2:C3",
    "battery": 85,
    "last_seen_ago_sec": 45,
    "is_alive": true
  },
  {
    "asset_mac": "AC:23:3F:D4:E5:F6",
    "battery": null,
    "last_seen_ago_sec": 1200,
    "is_alive": false
  }
]
```

**Request Schema** (`BeaconHealthItem[]`):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `asset_mac` | string | Yes | Beacon MAC address |
| `battery` | integer | No | Battery % (0-100, 101=USB, null=unknown) |
| `last_seen_ago_sec` | integer | Yes | Seconds since last sighting |
| `is_alive` | boolean | Yes | Whether beacon is considered alive |

**Response** (200 OK):
```json
{
  "ok": true,
  "updated": 2,
  "total": 2
}
```

**Business Logic**:
1. For each beacon:
   - Calculate `last_seen` = now - `last_seen_ago_sec`
   - Fetch existing `mst_asset.extra` JSON
   - Merge new data: `{"battery": 85, "last_seen": "2024-12-19T...", "is_alive": true}`
   - Update `mst_asset.extra` (preserves other fields)
2. Return count of updated beacons
3. Skip unknown beacons (not in `mst_asset`)

**Extra JSON Structure**:
```json
{
  "battery": 85,
  "last_seen": "2024-12-19T10:30:45Z",
  "is_alive": true,
  "deciding_rssi": -62.5
}
```

---

## Error Responses

### Standard Error Format

All errors follow FastAPI's default format:

```json
{
  "detail": "Error message here"
}
```

### HTTP Status Codes

| Code | Meaning | Usage |
|------|---------|-------|
| **200** | OK | Successful request |
| **400** | Bad Request | Invalid input (timestamp, MAC format, etc.) |
| **404** | Not Found | Resource doesn't exist (asset, zone, scanner) |
| **409** | Conflict | Duplicate resource (asset/scanner already registered) |
| **500** | Internal Server Error | Database error, unexpected exception |

### Common Errors

**Invalid Timestamp**:
```json
{
  "detail": "Invalid timestamp format"
}
```

**Asset Not Found**:
```json
{
  "detail": "Asset not found"
}
```

**Scanner Already Registered**:
```json
{
  "detail": "Scanner already registered"
}
```

**Database Error**:
```json
{
  "detail": "movement_log insert failed"
}
```

---

## Code Examples

### Python Client

**Complete Example**:
```python
import requests
from datetime import datetime, timezone

class BleXClient:
    def __init__(self, base_url="http://localhost:8000"):
        self.base_url = base_url
    
    def register_asset(self, mac, name):
        """Register a new asset"""
        response = requests.post(
            f"{self.base_url}/api/assets",
            json={"bluetooth_id": mac, "asset_name": name}
        )
        return response.json()
    
    def submit_movement(self, asset_mac, from_zone, to_zone, rssi):
        """Submit a zone-change event"""
        response = requests.post(
            f"{self.base_url}/api/asset/movement",
            json={
                "asset_mac": asset_mac,
                "from_zone_id": from_zone,
                "to_zone_id": to_zone,
                "state": "ZONE",
                "deciding_rssi": rssi,
                "timestamp": datetime.now(timezone.utc).isoformat()
            }
        )
        return response.json()
    
    def get_current_locations(self):
        """Get current location of all assets"""
        response = requests.get(f"{self.base_url}/api/assets/current")
        return response.json()
    
    def create_zone(self, name, description=""):
        """Create a new zone"""
        response = requests.post(
            f"{self.base_url}/api/zones",
            json={"zone_name": name, "description": description}
        )
        return response.json()
    
    def assign_scanner_to_zone(self, zone_id, scanner_id):
        """Assign a scanner to a zone"""
        response = requests.post(
            f"{self.base_url}/api/zones/{zone_id}/scanners",
            json={"scanner_id": scanner_id}
        )
        return response.json()

# Usage
client = BleXClient()

# Register an asset
client.register_asset("AC:23:3F:A1:B2:C3", "Wheelchair 01")

# Submit movement
client.submit_movement(
    asset_mac="AC:23:3F:A1:B2:C3",
    from_zone=1,
    to_zone=2,
    rssi=-62.5
)

# Get current locations
locations = client.get_current_locations()
for loc in locations:
    print(f"{loc['mac']} is in zone {loc['zone']}")
```

---

### JavaScript/TypeScript Client

```typescript
interface Asset {
  id: number;
  bluetooth_id: string;
  asset_name: string | null;
  current_zone_id: number | null;
}

interface MovementEvent {
  asset_mac: string;
  from_zone_id: number | null;
  to_zone_id: number | null;
  state: "ZONE" | "EXIT";
  deciding_rssi: number | null;
  timestamp: string;
}

class BleXClient {
  constructor(private baseUrl: string = "http://localhost:8000") {}

  async registerAsset(mac: string, name: string): Promise<any> {
    const response = await fetch(`${this.baseUrl}/api/assets`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ bluetooth_id: mac, asset_name: name })
    });
    return response.json();
  }

  async submitMovement(event: MovementEvent): Promise<any> {
    const response = await fetch(`${this.baseUrl}/api/asset/movement`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(event)
    });
    return response.json();
  }

  async getCurrentLocations(): Promise<any[]> {
    const response = await fetch(`${this.baseUrl}/api/assets/current");
    return response.json();
  }

  async listAssets(): Promise<Asset[]> {
    const response = await fetch(`${this.baseUrl}/api/assets`);
    return response.json();
  }
}

// Usage
const client = new BleXClient();

// Register asset
await client.registerAsset("AC:23:3F:A1:B2:C3", "Wheelchair 01");

// Submit movement
await client.submitMovement({
  asset_mac: "AC:23:3F:A1:B2:C3",
  from_zone_id: 1,
  to_zone_id: 2,
  state: "ZONE",
  deciding_rssi: -62.5,
  timestamp: new Date().toISOString()
});

// Get current locations
const locations = await client.getCurrentLocations();
console.log(locations);
```

---

### cURL Examples

**Register Asset**:
```bash
curl -X POST http://localhost:8000/api/assets \
  -H "Content-Type: application/json" \
  -d '{
    "bluetooth_id": "AC:23:3F:A1:B2:C3",
    "asset_name": "Wheelchair 01"
  }'
```

**Submit Movement Event**:
```bash
curl -X POST http://localhost:8000/api/asset/movement \
  -H "Content-Type: application/json" \
  -d '{
    "asset_mac": "AC:23:3F:A1:B2:C3",
    "from_zone_id": 1,
    "to_zone_id": 2,
    "state": "ZONE",
    "deciding_rssi": -62.5,
    "timestamp": "2024-12-19T10:30:45Z"
  }'
```

**Get Current Locations**:
```bash
curl http://localhost:8000/api/assets/current
```

**Create Zone**:
```bash
curl -X POST http://localhost:8000/api/zones \
  -H "Content-Type: application/json" \
  -d '{
    "zone_name": "Warehouse A",
    "description": "Main storage area"
  }'
```

**Assign Scanner to Zone**:
```bash
curl -X POST http://localhost:8000/api/zones/1/scanners \
  -H "Content-Type: application/json" \
  -d '{"scanner_id": 2}'
```

**Get Scanner-Zone Map**:
```bash
curl http://localhost:8000/api/runtime/scanner-zone-map
```

**Long-Poll for Zone Map Changes**:
```bash
curl "http://localhost:8000/api/runtime/scanner-zone-map/watch?version=5"
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Apr 2026 | Akshat Jain | Complete API reference with all endpoints |

**Next Review Date**: May 2026

---

*This document is part of the BleX technical documentation suite.*