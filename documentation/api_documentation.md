# BleX API and Database Documentation

This document provides a comprehensive technical reference for the BleX backend infrastructure, covering the RESTful API surface and the PostgreSQL relational schema.

---

## 1. Database Schema

The persistence layer is managed via PostgreSQL. All entity relationships are enforced through foreign key constraints to ensure transactional integrity.

### 1.1 Table: mst_zone
Primary registry for logical tracking areas.
- **id** (Integer, PK): Internal serial identifier.
- **zone_name** (Text): Human-readable name of the area.
- **description** (Text): Optional descriptive metadata.
- **dimension** (JSON): Coordinate data for UI mapping.

### 1.2 Table: mst_scanner
Registry for hardware edge nodes.
- **id** (Integer, PK): Internal serial identifier.
- **mac_id** (Text, Unique): Unique hardware MAC address.
- **name** (Text): Display name for the scanner.
- **type** (Text): Hardware category (e.g., "PI", "ESP32").
- **last_heartbeat** (DateTime): Timestamp of the last health check-in.
- **created_at** (DateTime): System insertion timestamp.

### 1.3 Table: mst_asset
Registry for tracked beacons and their current state.
- **id** (Integer, PK): Internal serial identifier.
- **bluetooth_id** (Text, Unique): The MAC address of the BLE beacon.
- **asset_name** (Text): Descriptive name (e.g., "Wheelchair 04").
- **current_zone_id** (Integer, FK): Reference to `mst_zone.id`.
- **last_movement_dt** (DateTime): Timestamp of the last confirmed zone change.
- **extra** (JSON): Metadata containing battery levels and real-time status.
- **created_at** (DateTime): System insertion timestamp.

### 1.4 Table: mst_master
Registry for the current central controller.
- **id** (Integer, PK): Internal serial identifier.
- **name** (Text): Identifier for the master instance.
- **mac** (Text, Unique): Hardware MAC address of the master.
- **ip** (Text): Current local or remote IP address.
- **created_at** (DateTime): System insertion timestamp.

### 1.5 Table: movement_log
Immutable audit trail of all asset transitions.
- **id** (BigInteger, PK): Incremental log identifier.
- **bluetooth_id** (Text): MAC address of the asset.
- **from_zone_id** (Integer, FK): Origin zone reference.
- **to_zone_id** (Integer, FK): Destination zone reference.
- **deciding_rssi** (Numeric): The signal strength that triggered the transition.
- **timestamp_movement** (DateTime): The exact time the movement was detected by scanners.

### 1.6 Table: mst_zone_scanner (Mapping)
Many-to-Many relationship between Zones and Scanners.
- **id** (Integer, PK): Mapping identifier.
- **mst_zone_id** (Integer, FK): Reference to `mst_zone.id`.
- **mst_scanner_id** (Integer, FK): Reference to `mst_scanner.id`.

---

## 2. API Documentation

The API is built using FastAPI and follows standard REST principles where applicable.

### 2.1 Assets Router (`/api/assets`)
Handles beacon lifecycle management.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/` | List all registered assets. |
| **POST** | `/` | Register a new asset (unique bluetooth_id required). |
| **PUT** | `/{id}` | Update asset name or bluetooth_id. |
| **DELETE** | `/{id}` | Permanently remove an asset from the registry. |

### 2.2 Health Router (`/api/health`)
Used by the Master Node for bulk status synchronization.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **POST** | `/scanners/bulk` | Batch update `last_heartbeat` for multiple scanners. |
| **POST** | `/beacons/bulk` | Batch update `battery` and `last_seen` metadata for assets. |

### 2.3 Movement Router (`/api`)
Real-time status and historical event retrieval.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/assets/current` | Returns the current zone and latest RSSI for all active assets. |
| **GET** | `/assets/history` | Returns the last 50 zone-change events for the UI ledger. |
| **POST** | `/asset/movement` | Endpoint for the Master Node to submit confirmed movements. |

### 2.4 Runtime Router (`/api/runtime`)
Bootstrap and synchronization logic for edge nodes.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/scanner-zone-map` | Returns the `{scanner_mac: zone_id}` mapping for the master. |
| **GET** | `/scanner-zone-map/watch` | Long-polling endpoint for the Master to detect zone map changes. |
| **POST** | `/master` | Master Node registers its current IP and MAC. |
| **GET** | `/master` | Returns the current Master IP for scanners to use. |
| **GET** | `/master/watch` | Long-polling for Scanners to detect Master IP migrations. |
| **POST** | `/scanner` | Scanners register their boot process and receive the Master IP. |

### 2.5 Scanners Router (`/api/scanners`)
Physical scanner hardware management.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/` | List all registered scanners. |
| **POST** | `/` | Register a new scanner node. |
| **PUT** | `/by-mac/{mac}` | Upsert logic to update scanner name/type by MAC. |
| **DELETE** | `/{id}` | Remove scanner and its zone associations. |

### 2.6 Zones Router (`/api/zones`)
Tracking area configuration.

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/` | List all zones with their assigned scanners. |
| **POST** | `/` | Create a new logical zone. |
| **PUT** | `/{id}` | Update zone name or description. |
| **DELETE** | `/{id}` | Remove a zone and nullify all related asset/log references. |
| **POST** | `/{id}/scanners` | Assign a scanner to a specific zone. |
| **DELETE** | `/{id}/scanners/{sid}` | Unassign a scanner from a specific zone. |
