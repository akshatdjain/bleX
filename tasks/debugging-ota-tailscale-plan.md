# BleX Remote Debugging, Log Shipping, and OTA Update Architecture

**Date**: 2026-05-17  
**Status**: Planning Document  
**Scope**: Remote debugging capability for Pi/ESP32 scanners deployed behind customer NAT

---

## Overview

BleX scanners (Pi, ESP32) are deployed at customer sites (hospitals, warehouses) behind customer NAT. This document defines a three-layer architecture for remote debugging, log collection, and OTA updates:

1. **Layer 1: Always-on Passive Monitoring** - Log shipping via MQTT + Loki + Grafana
2. **Layer 2: On-Demand Tailscale SSH** - Ephemeral, user-controlled remote access
3. **Layer 3: OTA Updates** - Controlled rollouts via MQTT command channel

The key innovation in Layer 2 is that Tailscale is NOT running by default. It activates only when explicitly enabled via API call, with short-lived auth keys and auto-cleanup. This eliminates persistent VPN exposure while maintaining on-demand debugging capability.

---

## Layer 1: Always-on Passive Monitoring (MQTT + Loki + Grafana)

### Architecture Overview

All devices (Pi, ESP32, Android) continuously publish logs and health data to the embedded MQTT broker. A lightweight bridge container on DGX subscribes to these topics and forwards to Loki. Grafana queries Loki for visualization and alerting.

### MQTT Topics

| Topic | Producer | Payload | Frequency | Purpose |
|-------|----------|---------|-----------|---------|
| `blex/logs/{tenant_id}/{device_id}` | All devices | JSON log entry | Real-time | Application logs (DEBUG, INFO, WARN, ERROR, FATAL) |
| `blex/health/{tenant_id}/{device_id}` | Pi, ESP32 | JSON health snapshot | Every 60s | CPU, memory, temperature, connectivity, uptime |
| `blex/crash/{tenant_id}/{device_id}` | All devices | JSON stack trace | On crash | Unhandled exceptions, segfaults, assertion failures |

### Health Heartbeat Payload

Devices publish to `blex/health/{tenant_id}/{device_id}` every 60 seconds:

```json
{
  "ts": "2026-05-17T14:23:45Z",
  "device_id": "AA:BB:CC:DD:EE:FF",
  "device_type": "scanner_pi|scanner_esp32|android_hub",
  "tenant_id": "HQTJAC",
  "uptime_s": 3600,
  "cpu_pct": 12.3,
  "mem_free_kb": 245000,
  "temp_c": 48.2,
  "beacons_active": 3,
  "mqtt_connected": true,
  "crash_count": 0,
  "firmware_version": "2.1.0",
  "last_ota_version": "2.1.0",
  "last_ota_time": "2026-05-17T12:00:00Z"
}
```

### Log Entry Payload

Devices publish to `blex/logs/{tenant_id}/{device_id}` in real-time:

```json
{
  "ts": "2026-05-17T14:23:45.123Z",
  "device_id": "AA:BB:CC:DD:EE:FF",
  "device_type": "scanner_pi",
  "tenant_id": "HQTJAC",
  "level": "INFO|DEBUG|WARN|ERROR|FATAL",
  "component": "BleScanner|MqttManager|OtaHandler|TailscaleHandler",
  "message": "Parsed 42 BLE advertisements in scan cycle",
  "metadata": {
    "beacon_count": 42,
    "scan_duration_ms": 5000
  }
}
```

### Crash Report Payload

Devices publish to `blex/crash/{tenant_id}/{device_id}` immediately on fatal error:

```json
{
  "ts": "2026-05-17T14:23:45.123Z",
  "device_id": "AA:BB:CC:DD:EE:FF",
  "device_type": "scanner_pi",
  "tenant_id": "HQTJAC",
  "exception_type": "RuntimeError|SegmentationFault|MemoryError",
  "message": "Out of memory during BLE scan",
  "stack_trace": "Traceback (most recent call last):\n  File ...",
  "context": {
    "uptime_s": 3600,
    "heap_used_kb": 450000,
    "active_tasks": 5
  }
}
```

### Docker Compose Setup on DGX

Deploy Loki, Grafana, and MQTT-to-Loki bridge as containers:

```yaml
version: '3.8'

services:
  loki:
    image: grafana/loki:3.0.0
    container_name: blex-loki
    ports:
      - "3100:3100"
    volumes:
      - ./loki-config.yaml:/etc/loki/local-config.yaml
      - loki-data:/loki
    command: -config.file=/etc/loki/local-config.yaml
    networks:
      - blex-net

  grafana:
    image: grafana/grafana:latest
    container_name: blex-grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=changeme
      - GF_INSTALL_PLUGINS=grafana-clock-panel
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning
    depends_on:
      - loki
    networks:
      - blex-net

  mosquitto:
    image: eclipse-mosquitto:latest
    container_name: blex-mosquitto
    ports:
      - "1883:1883"
    volumes:
      - ./mosquitto.conf:/mosquitto/config/mosquitto.conf
      - mosquitto-data:/mosquitto/data
    networks:
      - blex-net

  mqtt-loki-bridge:
    build:
      context: ./docker/mqtt-loki-bridge
      dockerfile: Dockerfile
    container_name: blex-mqtt-loki-bridge
    environment:
      - MQTT_HOST=mosquitto
      - MQTT_PORT=1883
      - LOKI_URL=http://loki:3100
    depends_on:
      - loki
      - mosquitto
    networks:
      - blex-net
    restart: always

volumes:
  loki-data:
  grafana-data:
  mosquitto-data:

networks:
  blex-net:
    driver: bridge
```

### MQTT-to-Loki Bridge

Lightweight Python container (50 lines) that subscribes to all log topics and forwards to Loki:

```python
# docker/mqtt-loki-bridge/bridge.py
import json
import os
import paho.mqtt.client as mqtt
import requests
from datetime import datetime

MQTT_HOST = os.getenv("MQTT_HOST", "localhost")
MQTT_PORT = int(os.getenv("MQTT_PORT", 1883))
LOKI_URL = os.getenv("LOKI_URL", "http://localhost:3100")

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        topic_parts = msg.topic.split("/")  # blex/logs/TENANT_ID/DEVICE_ID
        
        if "logs" in msg.topic:
            send_to_loki(
                level=payload.get("level", "INFO"),
                message=payload.get("message", ""),
                device_id=payload.get("device_id"),
                tenant_id=payload.get("tenant_id"),
                component=payload.get("component"),
                timestamp=payload.get("ts")
            )
        elif "crash" in msg.topic:
            send_to_loki(
                level="FATAL",
                message=f"{payload.get('exception_type')}: {payload.get('message')}",
                device_id=payload.get("device_id"),
                tenant_id=payload.get("tenant_id"),
                component="CrashHandler",
                timestamp=payload.get("ts"),
                stack_trace=payload.get("stack_trace")
            )
    except Exception as e:
        print(f"Error processing MQTT message: {e}")

def send_to_loki(level, message, device_id, tenant_id, component, timestamp, stack_trace=None):
    labels = {
        "job": "blex",
        "device_id": device_id,
        "tenant_id": tenant_id,
        "component": component,
        "level": level
    }
    
    values = [
        [str(int(datetime.fromisoformat(timestamp.replace("Z", "+00:00")).timestamp() * 1e9)), 
         message + (f"\n{stack_trace}" if stack_trace else "")]
    ]
    
    payload = {
        "streams": [
            {"stream": labels, "values": values}
        ]
    }
    
    try:
        requests.post(f"{LOKI_URL}/loki/api/v1/push", json=payload, timeout=5)
    except Exception as e:
        print(f"Error sending to Loki: {e}")

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("MQTT connected, subscribing to blex/logs/# and blex/crash/#")
        client.subscribe("blex/logs/#")
        client.subscribe("blex/crash/#")

client = mqtt.Client()
client.on_connect = on_connect
client.on_message = on_message
client.connect(MQTT_HOST, MQTT_PORT, keepalive=60)
client.loop_forever()
```

### Offline Detection and Alerting

Monitor heartbeat topic and trigger alerts if a device stops reporting:

```python
# Simple offline detector (runs in bridge or separate container)
import redis
from datetime import datetime, timedelta

r = redis.Redis(host='redis', port=6379, decode_responses=True)

def on_heartbeat(device_id, tenant_id):
    key = f"heartbeat:{tenant_id}:{device_id}"
    r.set(key, datetime.utcnow().isoformat(), ex=300)  # 5 minute TTL

def check_offline_devices():
    # Scan all heartbeat keys
    for key in r.scan_iter("heartbeat:*"):
        if not r.exists(key):
            tenant_id, device_id = key.split(":")[1:3]
            # Mark offline in DB
            # Send alert via Grafana -> Slack webhook
            trigger_alert(tenant_id, device_id, "Device offline")
```

### Grafana Dashboard

Create a dashboard with these panels:

1. **Device Status Table** - Shows all devices, heartbeat status (green/red), uptime, temp, CPU
2. **Log Stream** - Filter by tenant, device, log level, component
3. **Crash Alerts** - Auto-scrolling log of crashes per device
4. **Health Metrics** - Time series: CPU, memory, temperature per device
5. **MQTT Connection Status** - Count of connected vs offline devices

---

## Layer 2: On-Demand Tailscale SSH

### Why Tailscale

- No port forwarding needed (works behind NAT)
- SSH via Tailscale is key-less and firewall-aware
- Tailscale nodes can be ephemeral (auto-remove when disconnected)
- Fine-grained ACL control (only allow specific users to SSH to debug devices)
- Audit trail of every connection attempt
- Mobile-friendly (Tailscale app on phone = access to debug Pis)

### Key Design Principle

Tailscale is NOT running by default on Pi/ESP32 images. When you need to debug:

1. Call API endpoint to enable SSH on a specific device
2. DGX generates ephemeral Tailscale auth key (1-hour TTL, single-use, tagged)
3. MQTT command tells device to bring up Tailscale
4. Device joins Tailnet, reports back its Tailscale IP
5. You SSH in, debug, fix
6. Call API endpoint to disable SSH
7. Device tears down Tailscale, removes auth key, disconnects
8. Repeat from scratch next time (fresh auth key, fresh IP)

This eliminates the risk of a compromised Pi being an always-on backdoor into the Tailnet.

### MQTT Command Channel

Add new subscriber on Pi:

```python
# Pi subscribes to: blex/cmd/{TENANT_ID}/{DEVICE_ID}
def on_command_message(client, userdata, msg):
    payload = json.loads(msg.payload.decode())
    action = payload.get("action")
    params = payload.get("params", {})
    
    if action == "tailscale_up":
        handle_tailscale_up(params)
    elif action == "tailscale_down":
        handle_tailscale_down(params)
    elif action == "ota":
        handle_ota_update(params)
    elif action == "ping":
        handle_ping()
```

### DGX API Endpoints

Add three new endpoints to the asset API (FastAPI):

#### POST /api/devices/{device_id}/ssh/enable

Enable Tailscale SSH on a specific device. Generate ephemeral auth key, send MQTT command.

```python
from fastapi import APIRouter, HTTPException, Depends
from datetime import datetime, timedelta
import requests
import json
import logging

router = APIRouter()

@router.post("/api/devices/{device_id}/ssh/enable")
async def enable_ssh(device_id: str, current_user: User = Depends(get_current_user)):
    """
    Enable Tailscale SSH on a device.
    
    1. Query DB for device (get tenant_id)
    2. Generate ephemeral Tailscale auth key via Tailscale API
    3. Publish MQTT command
    4. Log audit trail
    5. Return status
    """
    
    # 1. Fetch device from DB
    device = await db.execute(
        select(Device).where(Device.device_id == device_id)
    )
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    
    tenant_id = device.tenant_id
    
    # Check authorization: user must belong to this tenant
    if not await user_has_tenant_access(current_user, tenant_id):
        raise HTTPException(status_code=403, detail="Unauthorized")
    
    # 2. Generate Tailscale auth key
    ts_key = generate_tailscale_auth_key(
        device_id=device_id,
        tenant_id=tenant_id,
        ttl_hours=1,
        tags=["tag:blex-debug"]
    )
    
    # 3. Publish MQTT command
    mqtt_client.publish(
        topic=f"blex/cmd/{tenant_id}/{device_id}",
        payload=json.dumps({
            "action": "tailscale_up",
            "params": {
                "auth_key": ts_key,
                "hostname": f"blex-{device_id.replace(':', '-')}",
                "ephemeral": True
            }
        }),
        qos=1
    )
    
    # 4. Log audit trail
    await db.execute(insert(AuditLog).values({
        "tenant_id": tenant_id,
        "device_id": device_id,
        "user_id": current_user.id,
        "action": "ssh_enable",
        "timestamp": datetime.utcnow(),
        "ip_address": request.client.host
    }))
    await db.commit()
    
    return {
        "device_id": device_id,
        "status": "connecting",
        "tailscale_ip": None,
        "message": "SSH enable command sent. Tailscale should be up in 30-60 seconds."
    }

def generate_tailscale_auth_key(device_id, tenant_id, ttl_hours, tags):
    """
    Call Tailscale API to generate ephemeral auth key.
    
    Requires: TAILSCALE_API_KEY environment variable (Tailscale OAuth token)
    """
    ts_api_key = os.getenv("TAILSCALE_API_KEY")
    ts_tailnet = os.getenv("TAILSCALE_TAILNET", "sigmatic-asc.tailnet-0123456789abcdef.ts.net")
    
    url = f"https://api.tailscale.com/api/v2/tailnets/{ts_tailnet}/keys"
    
    headers = {
        "Authorization": f"Bearer {ts_api_key}",
        "Content-Type": "application/json"
    }
    
    payload = {
        "capabilities": {
            "devices": {
                "create": {
                    "reusable": False,
                    "ephemeral": True,
                    "tags": tags,
                    "expiry": int((datetime.utcnow() + timedelta(hours=ttl_hours)).timestamp())
                }
            }
        }
    }
    
    resp = requests.post(url, headers=headers, json=payload)
    resp.raise_for_status()
    
    key_data = resp.json()
    return key_data["key"]
```

#### GET /api/devices/{device_id}/ssh/status

Poll current Tailscale status of a device.

```python
@router.get("/api/devices/{device_id}/ssh/status")
async def get_ssh_status(device_id: str, current_user: User = Depends(get_current_user)):
    """
    Get current Tailscale status for a device.
    
    Queries:
    1. DB for device status
    2. Tailscale API for node IP
    3. Last known heartbeat timestamp
    """
    
    device = await db.execute(
        select(Device).where(Device.device_id == device_id)
    )
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    
    tenant_id = device.tenant_id
    
    if not await user_has_tenant_access(current_user, tenant_id):
        raise HTTPException(status_code=403, detail="Unauthorized")
    
    # Check if Tailscale is currently enabled
    ts_enabled = await db.execute(
        select(TailscaleSession).where(
            TailscaleSession.device_id == device_id,
            TailscaleSession.enabled == True
        )
    )
    
    if not ts_enabled:
        return {
            "device_id": device_id,
            "status": "disconnected",
            "tailscale_ip": None
        }
    
    # Query Tailscale API for node info
    ts_nodes = query_tailscale_nodes(ts_enabled.auth_key)
    node = next((n for n in ts_nodes if device_id in n["hostname"]), None)
    
    if not node or not node.get("addresses"):
        return {
            "device_id": device_id,
            "status": "connecting",
            "tailscale_ip": None
        }
    
    return {
        "device_id": device_id,
        "status": "connected",
        "tailscale_ip": node["addresses"][0],  # IPv4
        "last_seen": node.get("last_seen"),
        "online": node.get("online", False)
    }
```

#### POST /api/devices/{device_id}/ssh/disable

Disable Tailscale SSH. Send down command to device, revoke auth key, clean up.

```python
@router.post("/api/devices/{device_id}/ssh/disable")
async def disable_ssh(device_id: str, current_user: User = Depends(get_current_user)):
    """
    Disable Tailscale SSH on a device.
    
    1. Query DB for device
    2. Publish MQTT down command
    3. Revoke Tailscale auth key via API
    4. Delete session from DB
    5. Log audit trail
    """
    
    device = await db.execute(
        select(Device).where(Device.device_id == device_id)
    )
    if not device:
        raise HTTPException(status_code=404, detail="Device not found")
    
    tenant_id = device.tenant_id
    
    if not await user_has_tenant_access(current_user, tenant_id):
        raise HTTPException(status_code=403, detail="Unauthorized")
    
    # 1. Publish MQTT down command
    mqtt_client.publish(
        topic=f"blex/cmd/{tenant_id}/{device_id}",
        payload=json.dumps({
            "action": "tailscale_down",
            "params": {}
        }),
        qos=1
    )
    
    # 2. Query active session and revoke auth key
    ts_session = await db.execute(
        select(TailscaleSession).where(
            TailscaleSession.device_id == device_id,
            TailscaleSession.enabled == True
        )
    )
    
    if ts_session:
        revoke_tailscale_auth_key(ts_session.auth_key)
        await db.execute(
            update(TailscaleSession)
            .where(TailscaleSession.id == ts_session.id)
            .values({"enabled": False, "disabled_at": datetime.utcnow()})
        )
        await db.commit()
    
    # 3. Log audit trail
    await db.execute(insert(AuditLog).values({
        "tenant_id": tenant_id,
        "device_id": device_id,
        "user_id": current_user.id,
        "action": "ssh_disable",
        "timestamp": datetime.utcnow(),
        "ip_address": request.client.host
    }))
    await db.commit()
    
    return {
        "device_id": device_id,
        "status": "disconnected",
        "message": "SSH disable command sent."
    }
```

### Pi Command Handler

Pi subscribes to `blex/cmd/{TENANT_ID}/{DEVICE_ID}` and handles tailscale_up / tailscale_down:

```python
# current/scanner/command_handler.py

import subprocess
import json
import logging
from datetime import datetime

logger = logging.getLogger(__name__)

def handle_tailscale_up(params):
    """
    Bring up Tailscale with ephemeral auth key.
    
    1. Install Tailscale if not present (first run)
    2. Authenticate with ephemeral auth key
    3. Report back IP via MQTT
    """
    auth_key = params["auth_key"]
    hostname = params.get("hostname", f"blex-{get_device_id()}")
    ephemeral = params.get("ephemeral", True)
    
    try:
        # Step 1: Install if needed
        logger.info("Checking Tailscale installation...")
        result = subprocess.run(
            ["which", "tailscale"],
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            logger.info("Installing Tailscale...")
            subprocess.run(
                ["sudo", "apt-get", "update"],
                check=True,
                timeout=60
            )
            subprocess.run(
                ["sudo", "apt-get", "install", "-y", "tailscale"],
                check=True,
                timeout=120
            )
        
        # Step 2: Bring up with auth key
        logger.info(f"Bringing up Tailscale (hostname: {hostname})...")
        
        cmd = [
            "sudo", "tailscale", "up",
            f"--authkey={auth_key}",
            f"--hostname={hostname}",
            "--advertise-tags=tag:blex-debug",
            "--ssh",
        ]
        
        if ephemeral:
            cmd.append("--ephemeral")
        
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=30
        )
        
        if result.returncode != 0:
            logger.error(f"Tailscale up failed: {result.stderr}")
            publish_response({"status": "failed", "error": result.stderr})
            return
        
        # Step 3: Get IP and report back
        ip_output = subprocess.run(
            ["tailscale", "ip", "-4"],
            capture_output=True,
            text=True,
            timeout=10
        )
        
        if ip_output.returncode == 0:
            tailscale_ip = ip_output.stdout.strip()
            logger.info(f"Tailscale up successful. IP: {tailscale_ip}")
            publish_response({
                "status": "connected",
                "tailscale_ip": tailscale_ip,
                "hostname": hostname,
                "timestamp": datetime.utcnow().isoformat()
            })
        else:
            logger.warning("Could not retrieve Tailscale IP")
            publish_response({"status": "up", "warning": "could_not_get_ip"})
    
    except Exception as e:
        logger.error(f"Tailscale up error: {e}")
        publish_response({"status": "failed", "error": str(e)})

def handle_tailscale_down(params):
    """
    Tear down Tailscale connection.
    
    1. Bring down Tailscale
    2. Optionally remove package (cleanup)
    3. Report status
    """
    try:
        logger.info("Bringing down Tailscale...")
        
        result = subprocess.run(
            ["sudo", "tailscale", "down"],
            capture_output=True,
            text=True,
            timeout=10
        )
        
        if result.returncode != 0:
            logger.error(f"Tailscale down failed: {result.stderr}")
            publish_response({"status": "failed", "error": result.stderr})
            return
        
        logger.info("Tailscale down successful")
        
        # Optional: Remove Tailscale package to save space
        # subprocess.run(["sudo", "apt-get", "remove", "-y", "tailscale"])
        
        publish_response({
            "status": "disconnected",
            "timestamp": datetime.utcnow().isoformat()
        })
    
    except Exception as e:
        logger.error(f"Tailscale down error: {e}")
        publish_response({"status": "failed", "error": str(e)})

def publish_response(response_data):
    """
    Publish response to blex/resp/{TENANT_ID}/{DEVICE_ID}
    """
    response_data["device_id"] = get_device_id()
    response_data["ts"] = datetime.utcnow().isoformat()
    
    mqtt_client.publish(
        topic=f"blex/resp/{get_tenant_id()}/{get_device_id()}",
        payload=json.dumps(response_data),
        qos=1
    )
```

### Tailscale ACL Configuration

Set once in Tailscale admin console. Only developers tagged `tag:blex-dev` can SSH to debug devices:

```json
{
  "acls": [
    {
      "action": "accept",
      "src": ["tag:blex-dev"],
      "dst": ["tag:blex-debug:22"]
    },
    {
      "action": "deny",
      "src": ["*"],
      "dst": ["*"]
    }
  ],
  "tagOwners": {
    "tag:blex-dev": ["autogroup:admin"],
    "tag:blex-debug": ["autogroup:admin"]
  },
  "nodeAttrs": [
    {
      "target": ["tag:blex-debug"],
      "attr": ["ssh"]
    }
  ]
}
```

### Usage Workflow

For a developer debugging a customer scanner:

1. Open BleX web dashboard or Android app
2. Go to Settings > Device Management
3. Find device (e.g., "Scanner-HQTJAC-01")
4. Click "Enable Remote Debug"
5. Wait 30-60 seconds for Tailscale to connect
6. App shows: "Connected. Tailscale IP: 100.64.x.y"
7. Open terminal: `ssh root@100.64.x.y` (no password, SSH key auto-managed by Tailscale)
8. Debug, check logs, fix code
9. Click "Disable Remote Debug"
10. Tailscale shuts down, auth key is revoked

All actions logged with timestamp and user ID.

---

## Layer 3: OTA Updates

### Overview

Push firmware updates to devices via MQTT command. Devices download, verify, extract, and restart atomically. Rollback on failure.

### MQTT OTA Command

DGX publishes to `blex/cmd/{tenant_id}/{device_id}`:

```json
{
  "action": "ota",
  "params": {
    "version": "2.1.0",
    "url": "https://dgx.sigmatic.local:8443/releases/blex-scanner-2.1.0.tar.gz",
    "sha256": "abcd1234...",
    "rollout_group": "canary|stable|all",
    "metadata": {
      "release_notes": "Fix MQTT reconnect bug",
      "changelog_url": "https://..."
    }
  }
}
```

### Pi OTA Handler

```python
# current/scanner/ota_handler.py

import hashlib
import os
import subprocess
import json
import tarfile
import logging
from datetime import datetime

logger = logging.getLogger(__name__)

def handle_ota_update(params):
    """
    Download, verify, extract, and apply firmware update.
    
    Atomic update flow:
    1. Download to /opt/blex/ota_download/
    2. Verify SHA256
    3. Extract to /opt/blex/next/
    4. Run pre-update hooks
    5. Atomic rename: current -> previous, next -> current
    6. Restart service
    7. Wait 60s, if crash: rollback
    8. Report result
    """
    
    version = params["version"]
    url = params["url"]
    expected_sha256 = params["sha256"]
    
    try:
        logger.info(f"Starting OTA update to version {version}")
        
        # Step 1: Download
        logger.info(f"Downloading from {url}")
        download_path = "/opt/blex/ota_download/blex.tar.gz"
        os.makedirs("/opt/blex/ota_download", exist_ok=True)
        
        result = subprocess.run(
            ["curl", "-f", "-L", "-o", download_path, url],
            timeout=300,
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            raise Exception(f"Download failed: {result.stderr}")
        
        # Step 2: Verify SHA256
        logger.info("Verifying SHA256...")
        sha256_hash = hashlib.sha256()
        with open(download_path, "rb") as f:
            for chunk in iter(lambda: f.read(4096), b""):
                sha256_hash.update(chunk)
        
        actual_sha256 = sha256_hash.hexdigest()
        if actual_sha256 != expected_sha256:
            raise Exception(f"SHA256 mismatch. Expected {expected_sha256}, got {actual_sha256}")
        
        logger.info("SHA256 verified")
        
        # Step 3: Extract to 'next' directory
        logger.info("Extracting to /opt/blex/next/")
        os.makedirs("/opt/blex/next", exist_ok=True)
        
        with tarfile.open(download_path, "r:gz") as tar:
            tar.extractall(path="/opt/blex/next")
        
        # Step 4: Run pre-update hooks (if any)
        pre_update_hook = "/opt/blex/next/pre-update.sh"
        if os.path.exists(pre_update_hook):
            logger.info("Running pre-update hook...")
            subprocess.run(["bash", pre_update_hook], timeout=60, check=True)
        
        # Step 5: Atomic rename
        logger.info("Performing atomic rename...")
        subprocess.run([
            "bash", "-c",
            "mv /opt/blex/current /opt/blex/previous && "
            "mv /opt/blex/next /opt/blex/current"
        ], check=True)
        
        # Step 6: Restart service
        logger.info("Restarting blex-scanner service...")
        subprocess.run(
            ["sudo", "systemctl", "restart", "blex-scanner"],
            timeout=30,
            check=True
        )
        
        # Step 7: Crash detection / rollback
        logger.info("Monitoring for crashes (60s grace period)...")
        crash_detected = False
        for i in range(6):
            subprocess.run(["sleep", "10"], check=True)
            
            # Check if service is still running
            result = subprocess.run(
                ["sudo", "systemctl", "is-active", "blex-scanner"],
                capture_output=True,
                text=True
            )
            
            if result.returncode != 0:
                logger.error("Service crashed after OTA update. Rolling back...")
                crash_detected = True
                break
        
        if crash_detected:
            # Rollback
            subprocess.run([
                "bash", "-c",
                "mv /opt/blex/current /opt/blex/failed && "
                "mv /opt/blex/previous /opt/blex/current"
            ], check=True)
            
            subprocess.run(
                ["sudo", "systemctl", "restart", "blex-scanner"],
                timeout=30,
                check=True
            )
            
            logger.error("Rollback completed")
            publish_response({
                "status": "failed",
                "version": version,
                "error": "service_crash_after_update",
                "timestamp": datetime.utcnow().isoformat()
            })
            return
        
        # Step 8: Success
        logger.info(f"OTA update to {version} completed successfully")
        publish_response({
            "status": "ok",
            "version": version,
            "timestamp": datetime.utcnow().isoformat()
        })
        
        # Clean up old downloads
        os.remove(download_path)
    
    except Exception as e:
        logger.error(f"OTA update error: {e}")
        publish_response({
            "status": "failed",
            "version": version,
            "error": str(e),
            "timestamp": datetime.utcnow().isoformat()
        })

def publish_response(response_data):
    """Publish to blex/resp/{TENANT_ID}/{DEVICE_ID}"""
    response_data["device_id"] = get_device_id()
    mqtt_client.publish(
        topic=f"blex/resp/{get_tenant_id()}/{get_device_id()}",
        payload=json.dumps(response_data),
        qos=1
    )
```

### DGX OTA Push Endpoint

```python
@router.post("/api/devices/ota/push")
async def push_ota_update(
    request: OtaPushRequest,
    current_user: User = Depends(get_current_user)
):
    """
    Push OTA update to one or more devices.
    
    Target modes:
    - "device:{device_id}": Single device
    - "tenant:{tenant_id}": All devices in tenant
    - "group:{group_id}": Specific device group
    - "all": All devices globally
    
    Rollout modes:
    - "canary": Push to 5% of devices, wait 24h
    - "stable": Push to remaining 95%
    """
    
    version = request.version
    target = request.target  # "device:AA:BB", "tenant:HQTJAC", "all"
    url = request.url  # HTTPS URL to release tarball
    sha256 = request.sha256
    rollout_mode = request.rollout_mode  # "immediate" or "canary"
    
    # Authorization check
    if not current_user.is_admin:
        raise HTTPException(status_code=403, detail="Only admins can push OTA")
    
    # Resolve target devices
    target_devices = []
    if target.startswith("device:"):
        device_id = target.split(":")[1]
        target_devices = [device_id]
    elif target.startswith("tenant:"):
        tenant_id = target.split(":")[1]
        target_devices = await get_tenant_devices(tenant_id)
    elif target == "all":
        target_devices = await get_all_devices()
    
    # Create release record
    release = Release(
        version=version,
        url=url,
        sha256=sha256,
        rollout_mode=rollout_mode,
        target_count=len(target_devices),
        created_at=datetime.utcnow(),
        created_by=current_user.id
    )
    db.add(release)
    await db.commit()
    
    # If canary: only push to 5%
    if rollout_mode == "canary":
        canary_count = max(1, len(target_devices) // 20)
        target_devices = target_devices[:canary_count]
        logger.info(f"Canary rollout: {canary_count}/{len(target_devices)} devices")
    
    # Publish MQTT commands to target devices
    for device_id in target_devices:
        device = await get_device(device_id)
        tenant_id = device.tenant_id
        
        mqtt_client.publish(
            topic=f"blex/cmd/{tenant_id}/{device_id}",
            payload=json.dumps({
                "action": "ota",
                "params": {
                    "version": version,
                    "url": url,
                    "sha256": sha256,
                    "rollout_group": rollout_mode
                }
            }),
            qos=1
        )
        
        logger.info(f"OTA command sent to {device_id}")
    
    return {
        "release_id": release.id,
        "version": version,
        "target_count": len(target_devices),
        "rollout_mode": rollout_mode,
        "message": f"OTA update queued for {len(target_devices)} devices"
    }

class OtaPushRequest(BaseModel):
    version: str  # "2.1.0"
    url: str  # HTTPS URL
    sha256: str  # Hex digest
    target: str  # "device:...", "tenant:...", "all"
    rollout_mode: str = "immediate"  # "immediate" or "canary"
```

### Release Monitoring

Track OTA progress per device:

```python
# Get OTA status for all devices
@router.get("/api/releases/{release_id}/status")
async def get_release_status(release_id: int):
    """
    Return OTA status breakdown:
    - pending: Not yet attempted
    - in_progress: Downloaded, extracting
    - success: Completed
    - failed: Error or crash
    - rollback: Rolled back after crash
    """
    
    release = await db.get(Release, release_id)
    if not release:
        raise HTTPException(status_code=404)
    
    statuses = await db.execute(
        select(OtaStatus)
        .where(OtaStatus.release_id == release_id)
        .group_by(OtaStatus.status)
        .with_entities(OtaStatus.status, func.count(OtaStatus.id).label("count"))
    )
    
    return {
        "release_id": release_id,
        "version": release.version,
        "created_at": release.created_at,
        "target_count": release.target_count,
        "status_breakdown": {row.status: row.count for row in statuses}
    }
```

---

## Implementation Order

### Week 1: Foundation (Monitoring)

| Day | Task | Effort | Owner |
|-----|------|--------|-------|
| 1-2 | Add health heartbeat to scanner.py (30 lines) | 1h | Backend |
| 2-3 | Add crash report handler to scanner.py | 1h | Backend |
| 3 | Deploy Loki + Grafana + mqtt_loki_bridge on DGX | 3h | DevOps |
| 4 | Build Grafana dashboard (status, logs, crashes) | 2h | DevOps |
| 5 | Test end-to-end with Pi sending heartbeat/logs | 1h | QA |

### Week 2: On-Demand Debugging (Tailscale)

| Day | Task | Effort | Owner |
|-----|------|--------|-------|
| 1-2 | Add MQTT command channel to scanner.py | 2h | Backend |
| 2-3 | Implement tailscale_up / tailscale_down handlers | 3h | Backend |
| 3-4 | Build DGX API endpoints (enable/disable/status) | 4h | Backend |
| 4-5 | Generate ephemeral Tailscale auth keys via API | 2h | Backend |
| 5 | Set up Tailscale ACL rules | 1h | DevOps |
| 6 | Manual test: enable SSH on one Pi, SSH in | 2h | QA |
| 7 | Test disable and auth key revocation | 1h | QA |

### Week 3: OTA Updates

| Day | Task | Effort | Owner |
|-----|------|--------|-------|
| 1-2 | Build Pi OTA download/verify/extract handler | 4h | Backend |
| 2-3 | Implement atomic rename and crash detection | 3h | Backend |
| 4 | Build DGX OTA push endpoint with release tracking | 3h | Backend |
| 5 | Implement rollback on crash | 2h | Backend |
| 6-7 | Test canary rollout (5% of devices) | 2h | QA |
| 7 | Test full rollout | 1h | QA |

### Week 4: UI and Documentation

| Day | Task | Effort | Owner |
|-----|------|--------|-------|
| 1-2 | Android: Device Management screen with SSH enable/disable buttons | 4h | Mobile |
| 2-3 | Android: Display Tailscale IP, connection status | 2h | Mobile |
| 3-4 | Audit log viewer (who enabled SSH when) | 2h | Backend |
| 5 | Write runbook: How to remote debug a device | 1h | Docs |
| 6 | Write ops guide: OTA canary rollout process | 1h | Docs |
| 7 | Code review, testing | 2h | Team |

---

## Summary Table

| Feature | Always On | On Demand | Cost | Deployment |
|---------|-----------|-----------|------|-----------|
| Log shipping (MQTT -> Loki) | Yes | | Free | Week 1 |
| Health heartbeat (60s) | Yes | | Free | Week 1 |
| Offline alerts (Grafana) | Yes | | Free | Week 1 |
| Crash reports | Yes | | Free | Week 1 |
| Tailscale SSH | No | Via API call | ~$20/mo for 3-5 devs | Week 2 |
| OTA updates | No | Push via API | Free | Week 3 |
| Audit logging | Yes | | Free | Week 4 |
| Remote dashboard (Grafana) | Yes | | Free | Week 1 |

---

## Security Considerations

1. **Ephemeral Auth Keys**: Tailscale auth keys are single-use, 1-hour TTL, never persisted on device.
2. **ACL Enforcement**: Only developers in `tag:blex-dev` can SSH to debug devices. Devices cannot reach each other.
3. **Audit Trail**: Every enable/disable action logged with user ID, timestamp, IP address.
4. **MQTT ACL**: Devices can only subscribe to their own `blex/cmd/{tenant_id}/{device_id}` topic (enforced by broker).
5. **OTA Verification**: All updates verified by SHA256 before extraction.
6. **OTA Rollback**: Atomic rename + 60s crash detection = automatic rollback on failure.
7. **Network Isolation**: All MQTT communication happens on local broker first, then bridges to remote (never direct from device).

---

## Monitoring and Alerting

Grafana alerts to set up:

- Device offline for > 5 minutes -> Slack notification
- CPU > 80% for > 10 minutes -> Slack notification
- Temperature > 60C -> Slack notification
- MQTT connection lost -> Slack notification
- OTA update failure -> Slack + PagerDuty
- Crash reported -> Slack + email

---

## Future Enhancements

1. **Web Dashboard OTA UI**: Push releases from web dashboard, track per-device status
2. **Automatic Canary Promotion**: 24h without errors -> auto-promote to stable
3. **Device Firmware Comparison**: Show which devices are on old firmware versions
4. **A/B Testing**: Run two firmware versions in parallel, compare metrics
5. **Mobile App Integration**: Tailscale SSH button on Android, quick device health check
6. **Scripted Debugging**: Python API to automate diagnostic runs (collect logs, memory dumps, etc.)
7. **Predictive Alerts**: Machine learning on crash patterns, predict failures before they happen
