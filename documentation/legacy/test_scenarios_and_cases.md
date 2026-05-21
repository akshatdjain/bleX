# BleX System Test Scenarios and Functional Test Cases

This document outlines the validation procedures for the BleX Asset Tracking platform. It covers end-to-end integration, edge node synchronization, and real-time movement decision logic.

---

## 1. High-Level System Scenarios

### 1.1 Initialization and Bootstrap
- **Context**: The system is coming online from a cold start.
- **Scenario**: Validate that the Master Node correctly registers its network footprint and that Scanners can independently discover the Master for MQTT communication.

### 1.2 Asset Mobility Lifecycle
- **Context**: Standard operation within the facility.
- **Scenario**: Verify that as an asset moves through physically different signal environments, the state transitions correctly from Discovery → Movement → Arrival → Exit.

### 1.3 State Resilience and Synchronization
- **Context**: Configuration changes made via the Web Dashboard.
- **Scenario**: Ensure that when a user remaps a scanner to a new zone, the edge Master receives an instant notification and updates its triangulation logic without a service restart.

---

## 2. Functional Test Cases

### 2.1 TC-01: Asset Discovery and Registry Entry
- **Objective**: Verify that a previously unknown BLE beacon is correctly identified and registered in the database.
- **Pre-conditions**: Beacon is broadcasting; Scanner is online and pointing to Master.
- **Steps**:
    1. Activate a new BLE beacon within range of a registered scanner.
    2. Monitor the Master Node logs for the incoming raw advertising packet.
    3. Check the `mst_asset` table in the database.
- **Expected Result**: A new record is created in `mst_asset` with the correct `bluetooth_id`.

### 2.2 TC-02: Intra-Zone Movement Transition
- **Objective**: Validate the logic that handles a confirmed move between Zone A and Zone B.
- **Pre-conditions**: Asset is currently registered in Zone A.
- **Steps**:
    1. Physically move the beacon from the range of Scanners in Zone A to Scanners in Zone B.
    2. Maintain presence in Zone B for a duration longer than `DWELL_TIME_SEC`.
    3. Verify the API response at `/api/assets/current`.
- **Expected Result**: 
    - The `current_zone_id` in `mst_asset` updates to Zone B.
    - A new entry appears in the `movement_log` with the correct `from_zone_id` and `to_zone_id`.

### 2.3 TC-03: Signal Hysteresis and Noise Filtering
- **Objective**: Ensure that a move is NOT triggered by minor signal fluctuations (RSSI jitter).
- **Pre-conditions**: Asset is in Zone A; `HYSTERESIS_DBM` is set to 5 dB.
- **Steps**:
    1. Place the asset at the edge of Zone A and Zone B.
    2. Simulate a temporary 3 dB drop in Zone A signal.
    3. Verify the Master Node decision log.
- **Expected Result**: No movement is triggered. The asset remains registered in Zone A until the signal difference exceeds the hysteresis threshold and confirm counts.

### 2.4 TC-04: Asset "EXIT" / Lost Connectivity
- **Objective**: Validate the timeout logic when an asset stops broadcasting.
- **Pre-conditions**: Asset is active in Zone A.
- **Steps**:
    1. Remove power from the beacon or move it completely out of scanner range.
    2. Wait for the `LOST_TIMEOUT` duration (e.g., 60 seconds).
    3. Inspect the UI Dashboard or the `REDIS_ASSET_ZONE_KEY`.
- **Expected Result**: 
    - The asset status changes to "EXIT".
    - `current_zone_id` in `mst_asset` is nullified.
    - An "EXIT" event is recorded in the `movement_log`.

### 2.5 TC-05: Real-time Config Sync (Long-Polling)
- **Objective**: Verify that scanner-to-zone mapping changes propagate to the Master instantly.
- **Pre-conditions**: Master is running and listening on `/api/runtime/scanner-zone-map/watch`.
- **Steps**:
    1. Using the Web UI (or API), move Scanner SC1 from Zone 1 to Zone 2.
    2. Verify the Master Node stdout/logger.
- **Expected Result**: The Master Node logs "[MASTER] Scanner → Zone map reloaded via API (Version X)" within seconds of the change.

---

## 3. Negative and Edge Case Testing

### 3.1 TC-06: Database Connection Interruption
- **Objective**: Verify Master Node behavior when the API/DB is unreachable.
- **Steps**:
    1. Stop the `ui_api` service.
    2. Trigger an asset movement.
- **Expected Result**: The Master Node should continue queuing events in Redis (`RPUSH`). Once the API is restored, the queued events should be processed into the database without loss.

### 3.2 TC-07: Malformed MQTT Payloads
- **Objective**: Ensure the Master logic is resilient to garbage data.
- **Steps**:
    1. Publish a non-JSON or incorrectly formatted string to the `ble/raw/#` topic.
- **Expected Result**: The Master Node should catch the exception silently (or log a warning) and continue processing valid packets without crashing.

### 3.3 TC-08: Rapid Oscillating Movement
- **Objective**: Test the "Debounce" behavior under extreme conditions.
- **Steps**:
    1. Rapidly move an asset between Zone A and Zone B every 1 second.
- **Expected Result**: The system should maintain the current state until the asset dwells in one location long enough to satisfy `ZONE_CONFIRM_COUNT` and `DWELL_TIME_SEC`, preventing log spam.
