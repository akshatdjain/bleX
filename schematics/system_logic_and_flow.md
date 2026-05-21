# BleX System Components and Data Flow Reference

This document provides a technical baseline for the BleX Asset Tracking platform. It is designed to be used as a source-of-truth for generating detailed block diagrams and data flow visualizations.

---

## 1. System Components

### **1.1 Edge Detection Layer**
- **BLE Beacons**: Broadcast advertising packets (UUID, Major, Minor, TX Power, RSSI).
- **Pi Scanners**: Raspberry Pi nodes running high-frequency HCI sniffing via BlueZ. 
- **Android Hub (Mobile Scanner)**: Tablets running the BleX Android App, using the native Android BLE Scanner API for localized or mobile tracking.

### **1.2 Communication Node**
- **MQTT Broker**: A central message bus (e.g., Mosquitto) hosted on a Master Pi or the Android Tablet Hub. It routes raw telemetry from scanners to the decision logic.

### **1.3 Master Intelligence (The Decision Engine)**
- **Logic Engine**: Consumes raw RSSI streams from MQTT.
    - **Triangulation**: Aggregates signals from multiple scanners for weighted centroid positioning.
    - **Debouncing**: Uses `ZONE_CONFIRM_COUNT` and `HYSTERESIS_DBM` to filter signal noise.
    - **Arrival Logic**: Uses `DWELL_TIME_SEC` to confirm an asset has physically entered a room before recording the movement.
- **Redis (Real-Time State)**: 
    - **Cache**: Stores sub-second current zone maps for every active beacon.
    - **Event Queue**: Uses `RPUSH` to buffer confirmed movement events for backend consumption.

### **1.4 Backend Persistence Hub**
- **Asset API (FastAPI)**: 
    - Consumes confirmed moves from Redis (`BLPOP`).
    - Provides REST endpoints for UI and mobile configuration.
    - Implements **Long-Polling (Watchers)** for Master Node and Scanner synchronization.
- **PostgreSQL Database**:
    - `mst_asset`: Registry and current location state.
    - `movement_log`: Permanent, immutable audit trail of transitions.
    - `mst_zone_scanner`: Relationships between physical scanners and logical tracking zones.

### **1.5 UI & Visualization**
- **Web Dashboard**: A React-based interface for live asset visibility, ledger viewing, and hardware health monitoring.
- **Android App UI**: On-field management for beacon provisioning and hotspot configuration.

---

## 2. Integrated Data Flow

### **Phase A: Ingestion (Real-Time)**
1. **Beacon** → **Scanner**: Radio advertisement received.
2. **Scanner** → **MQTT**: Raw metric (Scanner MAC, Beacon MAC, RSSI, Timestamp) published to `ble/raw/#`.

### **Phase B: Processing (Stateful Logic)**
3. **MQTT** → **Decision Engine**: Engine subscribes to the raw stream.
4. **Decision Engine** → **Redis (Cache)**: Engine compares the new signal against the cached `last_zone`.
5. **Logic Validation**: Engine checks if the signal satisfies the **Dwell Time** and **Hysteresis** thresholds.

### **Phase C: Persistence (Event-Driven)**
6. **Decision Engine** → **Redis (Queue)**: A confirmed move event is pushed to the Redis list.
7. **Redis (Queue)** → **Asset API**: API worker pops the event.
8. **Asset API** → **Postgres**: Records the move in `movement_log` and updates the `mst_asset` location.

### **Phase D: Visualization (Polling/Watcher)**
9. **Web UI** → **Asset API**: Dashboard fetches the `assets/current` status.
10. **Visual Render**: The UI updates the floorplan map and asset ledger instantly.

---

## 3. Configuration & Sync Flow (The Watcher System)

1. **User Change**: An admin remaps a scanner to a new zone via the Web Dashboard.
2. **Postgres Update**: The Asset API writes the new mapping to `mst_zone_scanner`.
3. **Event Notify**: The API triggers a `MAP_CHANGED` event.
4. **Long-Polling Sync**: The Master Node (which has an open "Watch" request to `/api/runtime/scanner-zone-map/watch`) receives the updated mapping instantly.
5. **Hot-Reload**: The Master Node updates its memory-based triangulation logic without a reboot.
