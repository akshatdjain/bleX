# BleX Multi-Tenant Verification Test Scenarios
Date: 2026-05-17

---

## TEST SUITE 1: Provisioner Watchdog (Pi)

### T1.1: Happy Path — Correct WiFi Creds
**Description**: Pi provisions with valid WiFi credentials. Watchdog monitors connection and succeeds.

**Precondition**:
- Pi is running provisioner service on port 8888
- Pi is currently on setup SSID (setup@1234)
- Valid target WiFi network available (e.g., "raghu-wifi" with known password)
- DGX is reachable and `/api/runtime/scanner-zone-map` is responsive

**Steps**:
1. From configurator, send POST to Pi:8888/provision with:
   ```json
   {
     "ssid": "raghu-wifi",
     "psk": "correct-password",
     "mqtt_host": "104.0.140.113",
     "mqtt_port": 1883,
     "tenant_id": "raghu_home"
   }
   ```
2. Observe Pi console output for "Attempting to connect..."
3. Wait up to 60 seconds for watchdog to verify connectivity
4. Fetch GET Pi:8888/status repeatedly

**Expected**:
- POST returns 200 OK with `status: ok` immediately (within 1 second)
- Response includes `config` bundle with `api_url`, `web_url`, `mqtt_host`, `tenant_id`
- Console shows "Watchdog started for SSID 'raghu-wifi', timeout=60s"
- Watchdog pings 8.8.8.8 every 5 seconds
- Within 5-10 seconds, watchdog reports "WiFi connection to 'raghu-wifi' verified!"
- Console shows "Tearing down setup network..."
- `/status` returns `{state: "connected", ssid: "raghu-wifi"}`
- File `~/mqtt_config.json` contains:
  ```json
  {
    "mqtt_host": "104.0.140.113",
    "mqtt_port": 1883,
    "tenant_id": "raghu_home"
  }
  ```

**Pass Criteria**:
- ✓ POST response is 200 OK (not 201 or deferred)
- ✓ `/status` returns `state: "connected"` within 30 seconds
- ✓ Setup network is torn down (Wi-Fi provisioning tab no longer appears)
- ✓ `mqtt_config.json` file exists with all three fields
- ✓ Pi reappears in Configurator/Scanners tab (scanner online indicator)

---

### T1.2: Bad WiFi Credentials — Timeout → Revert
**Description**: Pi provisions with invalid credentials. Watchdog times out after 60s, reverts to setup, and marks as failed.

**Precondition**:
- Pi is running provisioner service on port 8888
- Pi is currently on setup SSID
- Internet connectivity is not available or credentials are deliberately wrong
- DGX is reachable but will not be contacted during this test

**Steps**:
1. From configurator, send POST to Pi:8888/provision with:
   ```json
   {
     "ssid": "invalid-ssid",
     "psk": "wrong-password",
     "mqtt_host": "104.0.140.113",
     "mqtt_port": 1883,
     "tenant_id": "raghu_home"
   }
   ```
2. Observe console output
3. Fetch GET Pi:8888/status repeatedly during watchdog
4. After 60 seconds, check status again
5. Verify Pi has reverted to setup network

**Expected**:
- POST returns 200 OK immediately
- Console shows "Watchdog started for SSID 'invalid-ssid', timeout=60s"
- Watchdog pings 8.8.8.8 every 5 seconds for ~60 seconds
- All pings fail (no internet on bad connection)
- After 60 seconds: "Watchdog timeout: failed to connect to 'invalid-ssid' within 60s"
- Console shows "Reverting to setup network..."
- nmcli is called to delete the failed connection
- `/status` returns `{state: "failed", ssid: "invalid-ssid", error: "timeout"}`
- Pi WiFi module reconnects to setup SSID
- Pi reappears in Configurator/Scanners with online indicator

**Pass Criteria**:
- ✓ POST is 200 OK (not delayed by watchdog)
- ✓ `/status` shows `state: "failed"` after 65 seconds
- ✓ Pi reverts to setup SSID (can SSH to `setup@1234` again)
- ✓ `mqtt_config.json` was saved (even though provision failed)
- ✓ Watchdog completes within 65 seconds (not hung)

---

### T1.3: GET /status Returns Idle Before Any Provision Attempt
**Description**: Fresh Pi boot, no provisioning yet. Status endpoint returns idle state.

**Precondition**:
- Pi has just booted or provisioner_service.py was restarted
- No provision attempt has been made yet

**Steps**:
1. Immediately fetch GET Pi:8888/status
2. Observe response

**Expected**:
- HTTP 200 OK
- Response body: `{state: "idle", ssid: null}`

**Pass Criteria**:
- ✓ Returns `state: "idle"` (not "connecting" or "unknown")
- ✓ `ssid` is null or omitted
- ✓ No config fields are present (not "connected" yet)

---

### T1.4: GET /status Returns Connecting During Watchdog
**Description**: During an active provision attempt, status reflects the in-progress state.

**Precondition**:
- Pi is running provisioner service
- Provision has been triggered but watchdog is still running (before success/failure)

**Steps**:
1. Send POST to Pi:8888/provision with valid WiFi but intentionally slow internet (e.g., tethered with poor signal)
2. Immediately fetch GET Pi:8888/status multiple times (every 2 seconds)
3. Observe transition from "idle" → "connecting" → "connected" or "failed"

**Expected**:
- POST returns 200 OK immediately
- `/status` initially shows `{state: "idle", ssid: null}` (if called within ~1 second)
- Then `/status` shows watchdog state (could be "idle" → "connected" or "idle" → "failed" depending on timing)
- Final `/status` shows `{state: "connected", ssid: "..."}` or `{state: "failed", ...}`

**Pass Criteria**:
- ✓ Status transitions occur within 60 seconds total
- ✓ Final state is either "connected" or "failed" (not hung in "connecting")
- ✓ HTTP responses are always 200 OK (not 202 or 500)

---

### T1.5: tenant_id Written to mqtt_config.json After Successful Provision
**Description**: Verify that tenant_id is persisted to the config file for use by scanner.

**Precondition**:
- Pi has successfully provisioned (T1.1 passed)
- SSH access available to Pi

**Steps**:
1. SSH to Pi: `ssh pi@<pi-ip>`
2. Cat the file: `cat ~/mqtt_config.json`
3. Parse JSON and verify tenant_id field

**Expected**:
```json
{
  "mqtt_host": "104.0.140.113",
  "mqtt_port": 1883,
  "tenant_id": "raghu_home"
}
```

**Pass Criteria**:
- ✓ File exists at `~/mqtt_config.json`
- ✓ `tenant_id` field matches the value sent in provision request
- ✓ File is valid JSON (no syntax errors)
- ✓ File is readable by scanner process (permissions 644 or better)

---

### T1.6: Config Bundle Returned in POST /provision Response
**Description**: POST response includes all required config fields for UI to display.

**Precondition**:
- Pi is running provisioner service
- Provision endpoint is ready to handle request

**Steps**:
1. Send POST to Pi:8888/provision with all required fields
2. Parse JSON response
3. Verify structure

**Expected**:
```json
{
  "status": "ok",
  "message": "Connecting...",
  "config": {
    "api_url": "https://sigmatic-asc.tech/asset",
    "web_url": "https://sigmatic-asc.tech/beam",
    "mqtt_host": "104.0.140.113",
    "tenant_id": "raghu_home"
  }
}
```

**Pass Criteria**:
- ✓ Response contains all 4 config fields (api_url, web_url, mqtt_host, tenant_id)
- ✓ api_url is correct base endpoint for asset API
- ✓ web_url is correct base endpoint for dashboard
- ✓ mqtt_host matches the request
- ✓ tenant_id matches the request
- ✓ Response is valid JSON (status code 200, not 500)

---

### T1.7: Pi Reappears in Configurator/Scanners After Failed Provision (UDP Rediscovery)
**Description**: After a failed provision attempt, Pi re-enables its UDP discovery listener and reappears in the Scanner discovery list.

**Precondition**:
- Pi has just experienced a failed provision (T1.2 passed)
- Pi has reverted to setup SSID
- Configurator app is running on Android tablet

**Steps**:
1. On Configurator/Scanners tab, tap "Rescan" or wait for auto-refresh (~10 seconds)
2. Observe scanner list for Pi's UDP beacon
3. Pi should appear with state "online" and IP address on setup network

**Expected**:
- Scanner list shows Pi with:
  - MAC address (e.g., `B8:27:EB:XX:XX:XX`)
  - Type: `master-scanner` or `edge-scanner`
  - State: `online` (green indicator)
  - IP: something like `192.168.1.50` (from setup SSID)

**Pass Criteria**:
- ✓ Pi appears in scanner list within 15 seconds of reboot
- ✓ IP is on setup network (192.168.1.x)
- ✓ State indicator is green/online
- ✓ Can tap on Pi to attempt re-provision

---

## TEST SUITE 2: Multi-Tenant MQTT Topics

### T2.1: Scanner with tenant_id=raghu_home Publishes to ble/raghu_home/scanner/{mac}
**Description**: Verify that scanner correctly reads tenant_id and uses multi-tenant MQTT topic.

**Precondition**:
- Pi has been provisioned with `tenant_id: "raghu_home"` (from T1.1)
- `~/mqtt_config.json` contains `tenant_id: "raghu_home"`
- Scanner process is running on Pi
- MQTT broker (Moquette or DGX) is reachable
- A subscribed client is listening to `ble/raghu_home/scanner/#`

**Steps**:
1. SSH to Pi and verify mqtt_config.json: `cat ~/mqtt_config.json`
2. Start scanner: `python3 current/scanner/scanner.py`
3. On DGX, subscribe to topic: `mosquitto_sub -h 104.0.140.113 -p 1883 -t 'ble/raghu_home/scanner/#'`
4. (Or monitor via MQTT browser)
5. Place a BLE beacon within range of Pi
6. Wait for scanner to detect and publish

**Expected**:
- Scanner console output shows: `[SCANNER] tenant_id=raghu_home topic=ble/raghu_home/scanner/<MAC>`
- MQTT broker receives publish on topic `ble/raghu_home/scanner/<MAC>` (not `ble/scanner/<MAC>`)
- Payload includes field: `"tenant_id": "raghu_home"`

**Pass Criteria**:
- ✓ Topic path includes `raghu_home` segment
- ✓ Payload contains `tenant_id: "raghu_home"`
- ✓ No messages on old topic `ble/scanner/<MAC>`

---

### T2.2: Scanner with No mqtt_config.json Publishes to ble/scanner/{mac} (Backward Compat)
**Description**: Legacy Pi without mqtt_config.json falls back to default tenant and old topic format.

**Precondition**:
- Pi has no `~/mqtt_config.json` file (simulates old Pi or fresh setup)
- Scanner process is ready to run

**Steps**:
1. SSH to Pi and ensure mqtt_config.json doesn't exist: `rm ~/mqtt_config.json`
2. Start scanner: `python3 current/scanner/scanner.py`
3. Observe console output
4. Place BLE beacon within range
5. Monitor MQTT topic

**Expected**:
- Scanner logs: `[SCANNER] tenant_id=default topic=ble/scanner/<MAC>`
- MQTT broker receives publish on topic `ble/scanner/<MAC>` (old format, no tenant in path)
- Payload includes field: `"tenant_id": "default"`

**Pass Criteria**:
- ✓ Automatically falls back to `default` tenant
- ✓ Uses old topic format `ble/scanner/<MAC>`
- ✓ No errors in scanner logs
- ✓ Backward compatibility preserved

---

### T2.3: Scanner with tenant_id=default Publishes to ble/scanner/{mac} (Backward Compat)
**Description**: Explicitly set tenant_id=default behaves like legacy scanner.

**Precondition**:
- Pi has `~/mqtt_config.json` with `tenant_id: "default"`
- Scanner process is ready to run

**Steps**:
1. SSH to Pi and create mqtt_config.json: `cat > ~/mqtt_config.json << 'EOF'
{
  "mqtt_host": "104.0.140.113",
  "mqtt_port": 1883,
  "tenant_id": "default"
}
EOF`
2. Start scanner: `python3 current/scanner/scanner.py`
3. Observe console output
4. Place BLE beacon within range
5. Monitor MQTT topic

**Expected**:
- Scanner logs: `[SCANNER] tenant_id=default topic=ble/scanner/<MAC>`
- MQTT broker receives publish on topic `ble/scanner/<MAC>` (not multi-tenant path)
- Payload includes field: `"tenant_id": "default"`

**Pass Criteria**:
- ✓ Even though tenant_id is set, it uses old topic format when tenant_id == "default"
- ✓ Logic preserves backward compatibility
- ✓ Payload always includes tenant_id for diagnostics

---

### T2.4: Two Scanners with Different tenant_ids Publish to Separate Topics — No Collision
**Description**: Two simultaneously running scanners with different tenants don't collide on MQTT broker.

**Precondition**:
- Two Pis available (or two scanner processes on same Pi with mocked scanner_id)
- Pi1 has `mqtt_config.json` with `tenant_id: "raghu_home"`
- Pi2 has `mqtt_config.json` with `tenant_id: "dave_house"`
- Both connect to same MQTT broker on DGX
- BLE beacons are within range of both Pis

**Steps**:
1. SSH Pi1, start scanner: `python3 current/scanner/scanner.py`
2. SSH Pi2, start scanner: `python3 current/scanner/scanner.py`
3. On DGX, subscribe to all: `mosquitto_sub -h 104.0.140.113 -p 1883 -t 'ble/#' -v`
4. Place a beacon within range of both
5. Observe topic routing

**Expected**:
- Pi1 publishes to: `ble/raghu_home/scanner/<Pi1_MAC>`
- Pi2 publishes to: `ble/dave_house/scanner/<Pi2_MAC>`
- Broker receives both without collision
- Each topic is independent (no cross-tenant message mixing)
- Console outputs:
  - Pi1: `[SCANNER] tenant_id=raghu_home topic=ble/raghu_home/scanner/<MAC>`
  - Pi2: `[SCANNER] tenant_id=dave_house topic=ble/dave_house/scanner/<MAC>`

**Pass Criteria**:
- ✓ Two distinct topics used (no `/` collision)
- ✓ Broker doesn't merge or filter messages between tenants
- ✓ Both scanners publish continuously without errors
- ✓ Master can subscribe to single tenant: `ble/raghu_home/scanner/#` and only see Pi1

---

## TEST SUITE 3: Database Schema Isolation

### T3.1: raghu_home Schema Has All 6 Tables
**Description**: Verify the Postgres schema is fully populated with required tables.

**Precondition**:
- DGX Postgres is running and asset_tracking database exists
- raghu_home schema has been migrated

**Steps**:
1. SSH to DGX
2. Run: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT table_name FROM information_schema.tables WHERE table_schema='raghu_home' ORDER BY table_name;"`

**Expected**:
```
    table_name    
------------------
 movement_log
 mst_asset
 mst_master
 mst_scanner
 mst_zone
 mst_zone_scanner
(6 rows)
```

**Pass Criteria**:
- ✓ Exactly 6 tables present
- ✓ All required table names are present
- ✓ No extra tables
- ✓ No errors from psql

---

### T3.2: INSERT into raghu_home.mst_scanner Doesn't Affect public.mst_scanner
**Description**: Schema isolation: raghu_home data is independent of public data.

**Precondition**:
- raghu_home and public schemas both have mst_scanner table
- DGX Postgres is running

**Steps**:
1. SSH to DGX
2. Count rows in public.mst_scanner: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT COUNT(*) FROM public.mst_scanner;"`
3. Count rows in raghu_home.mst_scanner: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT COUNT(*) FROM raghu_home.mst_scanner;"`
4. Insert a test scanner into raghu_home: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "INSERT INTO raghu_home.mst_scanner (mac_id, name, type) VALUES ('AA:BB:CC:DD:EE:FF', 'test-scanner', 'edge-scanner');"`
5. Count rows again in both schemas

**Expected**:
- public.mst_scanner row count unchanged
- raghu_home.mst_scanner row count incremented by 1
- Two separate tables, no data bleed

**Pass Criteria**:
- ✓ public count before = public count after
- ✓ raghu_home count before + 1 = raghu_home count after
- ✓ INSERT succeeds without error
- ✓ No foreign key constraints violated between schemas

---

### T3.3: shared.tenants Has All 4 Registered Tenants
**Description**: Central tenant registry is populated and accessible to all schemas.

**Precondition**:
- DGX Postgres is running with shared schema
- shared.tenants table has been seeded

**Steps**:
1. SSH to DGX
2. Query: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT tenant_id, name, mqtt_prefix, tier FROM shared.tenants ORDER BY tenant_id;"`

**Expected**:
```
 tenant_id  |       name       |  mqtt_prefix   |  tier  
------------+------------------+----------------+--------
 default    | Default (legacy) | ble/scanner    | pooled
 dave_house | Dave House Demo  | ble/dave_house | pooled
 ehab_house | Ehab House Demo  | ble/ehab_house | pooled
 raghu_home | Raghu Home Demo  | ble/raghu_home | pooled
(4 rows)
```

**Pass Criteria**:
- ✓ Exactly 4 tenants present
- ✓ All have unique tenant_id
- ✓ mqtt_prefix matches tenant_id pattern (ble/{tenant_id} except default)
- ✓ All have tier = "pooled"
- ✓ Query returns results in consistent order

---

### T3.4: DROP raghu_home.mst_asset Row Doesn't Affect public.mst_asset
**Description**: Verify physical table separation at schema level.

**Precondition**:
- Both raghu_home and public schemas have data in mst_asset
- DGX Postgres is running

**Steps**:
1. SSH to DGX
2. Count rows in public.mst_asset: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT COUNT(*) FROM public.mst_asset;"`
3. Count rows in raghu_home.mst_asset: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT COUNT(*) FROM raghu_home.mst_asset;"`
4. Insert a test asset into raghu_home: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "INSERT INTO raghu_home.mst_asset (bluetooth_id, name) VALUES ('AA:BB:CC:DD:EE:FF', 'test-asset');"`
5. Count rows again in both
6. Delete from raghu_home: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "DELETE FROM raghu_home.mst_asset WHERE bluetooth_id='AA:BB:CC:DD:EE:FF';"`
7. Verify count in public unchanged

**Expected**:
- public.mst_asset row count unchanged throughout
- raghu_home.mst_asset row count incremented, then decremented
- No cascade deletes to public schema

**Pass Criteria**:
- ✓ INSERT into raghu_home succeeds
- ✓ public count never changes
- ✓ DELETE from raghu_home succeeds
- ✓ public and raghu_home are truly independent tables

---

## TEST SUITE 4: Raghu Setup Readiness (Run ~30 min before setup session)

### T4.1: DGX Postgres raghu_home Schema Exists with 6 Tables
**Description**: Pre-flight check: infrastructure ready for Raghu's first connection.

**Precondition**:
- DGX is running and accessible
- Postgres migration has been completed

**Steps**:
1. SSH to DGX: `ssh raghu@104.0.140.113`
2. Run: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name='raghu_home';"`
3. Run: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT COUNT(*) as table_count FROM information_schema.tables WHERE table_schema='raghu_home';"`

**Expected**:
- Schema exists (returns 1 row)
- Table count is 6

**Pass Criteria**:
- ✓ Schema query returns `raghu_home` (1 row)
- ✓ Table count query returns `6`

---

### T4.2: shared.tenants Has raghu_home with tier=pooled
**Description**: Tenant registry entry is ready.

**Precondition**:
- DGX Postgres is running

**Steps**:
1. SSH to DGX
2. Run: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT tenant_id, tier FROM shared.tenants WHERE tenant_id='raghu_home';"`

**Expected**:
```
 tenant_id  |  tier  
------------+--------
 raghu_home | pooled
(1 row)
```

**Pass Criteria**:
- ✓ Tenant exists
- ✓ Tier is "pooled" (connection pooling enabled)

---

### T4.3: master_pool Container Can Subscribe to ble/raghu_home/scanner/#
**Description**: Master pool is ready to listen for Raghu's scanner zone events.

**Precondition**:
- DGX docker-compose is running (asset_tracking-master-pool-1 container)
- MQTT broker is accessible on port 1883
- Provisioner watchdog has succeeded (T1.1) and scanner is publishing

**Steps**:
1. SSH to DGX
2. Run: `docker exec asset_tracking-master-pool-1 python3 -c "import paho.mqtt.client as mqtt; c = mqtt.Client(); c.connect('localhost', 1883); c.subscribe('ble/raghu_home/scanner/#'); print('Subscribed OK')"`
   (Or check container logs for subscription attempts)
3. Alternatively, use mosquitto_sub: `docker exec asset_tracking-db-1 mosquitto_sub -h localhost -t 'ble/raghu_home/scanner/#' &` then stop after 2 seconds

**Expected**:
- Subscription succeeds without error
- No permission denied errors
- Master_pool container can reach MQTT broker

**Pass Criteria**:
- ✓ Subscription command exits with code 0
- ✓ No TLS/auth errors
- ✓ No connection refused errors

---

### T4.4: Pi Provisioner Serves GET /status → {state: idle} on Fresh Boot
**Description**: Raghu's Pi is ready to provision.

**Precondition**:
- Pi has been freshly booted or provisioner restarted
- No provision attempt yet

**Steps**:
1. From Android tablet, GET `http://<pi-ip>:8888/status`
2. Observe response

**Expected**:
```json
{
  "state": "idle",
  "ssid": null
}
```

**Pass Criteria**:
- ✓ HTTP 200 OK
- ✓ state is "idle"
- ✓ Response received within 2 seconds

---

### T4.5: Full End-to-End: Pi Provisions → Publishes ble/raghu_home/scanner/{mac} → Master Sees It
**Description**: Complete flow verification before Raghu's setup session.

**Precondition**:
- Pi is ready to provision (T4.4 passed)
- DGX is running with raghu_home schema and master_pool
- Target WiFi network is available (Raghu's site network)
- BLE beacons are placed within range of Pi

**Steps**:
1. On Android tablet, navigate to Configurator/Scanners
2. Tap Pi to provision
3. Enter WiFi: SSID, password, MQTT broker (DGX IP), tenant_id="raghu_home"
4. Confirm provision
5. Monitor Pi console: `ssh pi@<pi-ip> sudo journalctl -f | grep -E "provisioner|scanner|MQTT"`
6. After watchdog succeeds, scanner should start automatically (or start manually: `python3 current/scanner/scanner.py`)
7. Place a BLE beacon in range
8. SSH to DGX and subscribe: `docker exec asset_tracking-db-1 mosquitto_sub -h localhost -p 1883 -t 'ble/raghu_home/scanner/#' -v`
9. Observe MQTT message with beacon RSSI

**Expected**:
- Provision POST returns 200 OK within 1 second
- Watchdog succeeds (state → "connected") within 30 seconds
- Pi WiFi joins Raghu's network
- Scanner starts and publishes to `ble/raghu_home/scanner/<PI_MAC>`
- MQTT message contains `tenant_id: "raghu_home"`
- Master_pool can subscribe and see all beacons from Raghu's Pi

**Pass Criteria**:
- ✓ `/status` shows state="connected"
- ✓ mqtt_config.json has tenant_id="raghu_home"
- ✓ Scanner logs show tenant_id=raghu_home
- ✓ MQTT topic is `ble/raghu_home/scanner/{mac}` (not `ble/scanner/{mac}`)
- ✓ Payload includes tenant_id and beacon RSSI
- ✓ DGX master_pool receives the message

---

## TEST SUITE 5: Non-Regression (Existing Deployment Not Broken)

### T5.1: Existing public Schema Data Unchanged After Postgres Migration
**Description**: Verify no data loss in legacy deployment during multi-tenant migration.

**Precondition**:
- DGX Postgres was running before migration with data in public schema
- Migration completed successfully

**Steps**:
1. SSH to DGX
2. Run: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT COUNT(*) as count FROM public.mst_scanner;"`
3. Run: `docker exec asset_tracking-db-1 psql -U postgres -d asset_tracking -c "SELECT COUNT(*) as count FROM public.mst_asset;"`
4. Compare against known pre-migration counts (if documented)

**Expected**:
- Both counts are non-zero or match pre-migration state
- No truncation or data loss errors
- Queries execute without permission denied

**Pass Criteria**:
- ✓ public schema tables are still populated
- ✓ No data has been deleted
- ✓ Row counts match or are reasonable

---

### T5.2: Scanner Without mqtt_config.json Still Works (Falls Back to Default Tenant)
**Description**: Old Pi or fresh Pi without provisioning doesn't break when scanner starts.

**Precondition**:
- Pi has no mqtt_config.json (removed or never created)
- Scanner process is ready to run
- MQTT broker is accessible on default config (MQTT_BROKER from config.py)

**Steps**:
1. SSH to Pi
2. Ensure mqtt_config.json doesn't exist: `rm ~/mqtt_config.json`
3. Start scanner: `python3 current/scanner/scanner.py`
4. Monitor console for 5 seconds
5. Place a BLE beacon in range

**Expected**:
- Scanner starts without error
- Console shows: `[SCANNER] tenant_id=default topic=ble/scanner/<MAC>`
- No "file not found" or exception
- Publishes to `ble/scanner/<MAC>` topic
- Continues to work as before migration

**Pass Criteria**:
- ✓ No crashes on startup
- ✓ Tenant_id defaults to "default"
- ✓ Topic is `ble/scanner/<MAC>` (old format)
- ✓ Beacons are detected and published

---

### T5.3: Old Pi Images (No tenant_id) Still Publish to ble/scanner/{mac}
**Description**: Legacy Pi running old scanner code without tenant_id support.

**Precondition**:
- Old Pi image is still running (before tenant_id was added)
- Or scanner code reverted to version before tenant_id changes
- MQTT broker is accessible

**Steps**:
1. Start old scanner version on Pi
2. Monitor MQTT broker: `mosquitto_sub -h 104.0.140.113 -p 1883 -t 'ble/scanner/#' -v`
3. Place BLE beacon in range

**Expected**:
- Publishes to: `ble/scanner/<MAC>` (no tenant segment)
- No tenant_id field in payload (or always "default")
- Broker receives messages without collision with new tenants

**Pass Criteria**:
- ✓ Topic is `ble/scanner/<MAC>` (not `ble/{tenant}/scanner/{mac}`)
- ✓ No errors in old Pi logs
- ✓ Master can still subscribe to `ble/scanner/#` for legacy support

---

### T5.4: Existing API Endpoints Still Respond on /asset and /beam
**Description**: Backend API is not broken by database schema changes.

**Precondition**:
- Backend is running on DGX (uvicorn on port 8000 or behind reverse proxy)
- API endpoints are accessible via https://sigmatic-asc.tech/asset and /beam

**Steps**:
1. Test GET `https://sigmatic-asc.tech/asset/api/assets` (public schema)
2. Test GET `https://sigmatic-asc.tech/asset/api/zones`
3. Test GET `https://sigmatic-asc.tech/beam/` (web dashboard)
4. Observe response codes and data

**Expected**:
- All endpoints return 200 OK (if authenticated) or 401 Unauthorized (if auth required, not 500)
- No 500 Internal Server Error
- API still uses public schema for legacy requests
- Responses are JSON and well-formed

**Pass Criteria**:
- ✓ GET /asset returns 200 or 401 (not 500)
- ✓ GET /zones returns 200 or 401
- ✓ GET /beam returns HTML (not 500)
- ✓ No database connection errors in logs

---

## Test Execution Notes

**Order of Execution**:
1. Run T5.x (non-regression) first to confirm legacy still works
2. Run T3.x (schema isolation) to verify database structure
3. Run T1.x (provisioner watchdog) to test Pi hardware interaction
4. Run T2.x (MQTT topics) to verify routing
5. Run T4.x (readiness) as final pre-flight check before Raghu's session
   
**Environment Setup**:
- DGX must be running and reachable on 104.0.140.113
- Pi must have fresh provisioner_service.py deployed
- Android tablet with Configurator app must be on same WiFi or same network as Pi (setup network if not provisioned)
- BLE beacons must be available for testing (iBeacon or Eddystone)
- MQTT broker must be running and listening on port 1883

**Troubleshooting**:
- If provisioner times out: check internet connectivity from Pi, verify ping to 8.8.8.8 works
- If MQTT topics not appearing: verify mqtt_config.json is being written, check MQTT broker logs for connection errors
- If schema missing: re-run Postgres migration, verify DGX docker-compose restart
- If API fails: check backend logs on DGX for database connection errors

**Expected Duration**: ~60 minutes for full test suite (T5, T3, T1, T2, T4 in order)
