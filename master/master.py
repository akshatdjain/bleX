# master.py
# -------------------------------------------------
# Zone-based BLE Asset Movement Engine — MULTI-TENANT
# -------------------------------------------------
print("[MASTER] Script started", flush=True)

import json
import time
from datetime import datetime, timezone
from collections import defaultdict

import paho.mqtt.client as mqtt
import redis
import threading
import requests
import ssl
import os

BLEX_API_TOKEN = os.getenv("BLEX_API_TOKEN", "")

from config import (
    MQTT_BROKER,
    MQTT_PORT,
    MQTT_TOPIC_BASE,

    REDIS_HOST,
    REDIS_PORT,
    REDIS_PASSWORD,
    REDIS_ASSET_ZONE_KEY,
    REDIS_ZONE_QUEUE_KEY,
    SCANNER_ZONE_API,
    ACTIVE_TENANTS_API,
    SCANNER_ZONE_REFRESH_SEC,

    MQTT_USERNAME,
    MQTT_PASSWORD,
    MQTT_TRANSPORT,
    MQTT_TLS,

    HYSTERESIS_DBM,
    SCANNER_TTL,
    ZONE_CONFIRM_COUNT,
    DWELL_TIME_SEC,
    LOST_TIMEOUT,
    ENABLE_DEBUG_LOGS,
)

# -------------------------------------------------
# REDIS
# -------------------------------------------------
redis_kwargs = {
    "host": REDIS_HOST,
    "port": REDIS_PORT,
    "decode_responses": True,
}
if REDIS_PASSWORD:
    redis_kwargs["password"] = REDIS_PASSWORD

redis_client = redis.Redis(**redis_kwargs)

# -------------------------------------------------
# MULTI-TENANT IN-MEMORY STATE
# -------------------------------------------------
# SCANNER_ZONE_MAP[tenant] = {scanner_mac_upper: zone_id}
SCANNER_ZONE_MAP: dict = {}
SCANNER_ZONE_LOCK = threading.Lock()

# ASSET_STATE[tenant][asset_mac] = {"zones": ..., "confirm": ..., "pending_move": ...}
ASSET_STATE: dict = defaultdict(lambda: defaultdict(lambda: {
    "zones": defaultdict(dict),
    "confirm": defaultdict(int),
    "pending_move": None,
}))

# last_seen_registry[tenant][asset_mac] = timestamp
last_seen_registry: dict = defaultdict(dict)

# -------------------------------------------------
# HELPERS
# -------------------------------------------------
def now_iso():
    return datetime.now(timezone.utc).isoformat()


def redis_get_last_zone(tenant: str, asset_mac: str):
    try:
        key = REDIS_ASSET_ZONE_KEY.format(tenant=tenant, mac=asset_mac)
        return redis_client.get(key)
    except Exception as e:
        print(f"[REDIS-ERROR] get_last_zone {tenant}/{asset_mac}: {e}", flush=True)
        return None


def redis_set_last_zone(tenant: str, asset_mac: str, zone_id):
    try:
        key = REDIS_ASSET_ZONE_KEY.format(tenant=tenant, mac=asset_mac)
        redis_client.set(key, zone_id)
    except Exception as e:
        print(f"[REDIS-ERROR] set_last_zone {tenant}/{asset_mac}: {e}", flush=True)


def push_fifo(event: dict):
    try:
        redis_client.rpush(REDIS_ZONE_QUEUE_KEY, json.dumps(event))
    except Exception:
        pass

# -------------------------------------------------
# ZONE MAP FETCHING — PER TENANT
# -------------------------------------------------
def _service_headers(tenant: str) -> dict:
    h = {"X-Tenant-ID": tenant}
    if BLEX_API_TOKEN:
        h["Authorization"] = f"Bearer {BLEX_API_TOKEN}"
    return h


def fetch_active_tenants() -> list:
    """GET /api/tenants/active → list of tenant_id strings."""
    try:
        resp = requests.get(ACTIVE_TENANTS_API,
                            headers={"Authorization": f"Bearer {BLEX_API_TOKEN}"} if BLEX_API_TOKEN else {},
                            timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            return data.get("tenants", [])
        else:
            print(f"[MASTER] /api/tenants/active returned {resp.status_code}", flush=True)
            return []
    except Exception as e:
        print(f"[MASTER] fetch_active_tenants error: {e}", flush=True)
        return []


def fetch_zone_map_for_tenant(tenant: str) -> dict:
    """GET scanner-zone-map for a single tenant. Returns mac→zone_id dict (uppercase keys)."""
    try:
        resp = requests.get(SCANNER_ZONE_API, headers=_service_headers(tenant), timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            raw = data.get("scanner_zone_map", {})
            return {k.upper(): v for k, v in raw.items()}
        else:
            print(f"[MASTER] zone-map {tenant}: HTTP {resp.status_code}", flush=True)
            return {}
    except Exception as e:
        print(f"[MASTER] zone-map fetch {tenant}: {e}", flush=True)
        return {}


def zone_map_refresh_loop():
    """Background thread: periodically refreshes zone maps for all active tenants."""
    print("[MASTER] Zone-map refresh thread started", flush=True)

    # Initial load — retry until at least one tenant is loaded
    while True:
        tenants = fetch_active_tenants()
        if tenants:
            for t in tenants:
                m = fetch_zone_map_for_tenant(t)
                with SCANNER_ZONE_LOCK:
                    SCANNER_ZONE_MAP[t] = m
                print(f"[MASTER] Loaded zone map for {t}: {len(m)} scanners", flush=True)
            break
        print("[MASTER] No active tenants / API not ready — retrying in 5s", flush=True)
        time.sleep(5)

    print("[MASTER] Initial zone maps loaded, entering refresh loop", flush=True)

    while True:
        time.sleep(SCANNER_ZONE_REFRESH_SEC)
        tenants = fetch_active_tenants()
        for t in tenants:
            m = fetch_zone_map_for_tenant(t)
            with SCANNER_ZONE_LOCK:
                old = SCANNER_ZONE_MAP.get(t, {})
                if old != m:
                    SCANNER_ZONE_MAP[t] = m
                    print(f"[MASTER] Zone map refreshed for {t}: {len(m)} scanners", flush=True)
        # Clean up tenants no longer active
        with SCANNER_ZONE_LOCK:
            for gone in set(SCANNER_ZONE_MAP) - set(tenants):
                del SCANNER_ZONE_MAP[gone]
                print(f"[MASTER] Removed zone map for inactive tenant {gone}", flush=True)


# -------------------------------------------------
# LOST / EXIT HANDLER — PER TENANT
# -------------------------------------------------
def handle_lost_assets():
    now = time.time()
    with SCANNER_ZONE_LOCK:
        tenants = list(last_seen_registry.keys())

    for tenant in tenants:
        for asset_mac, last_seen in list(last_seen_registry[tenant].items()):
            if now - last_seen < LOST_TIMEOUT:
                continue

            last_zone = redis_get_last_zone(tenant, asset_mac)
            if last_zone == "EXIT":
                continue

            redis_set_last_zone(tenant, asset_mac, "EXIT")

            event = {
                "tenant_id": tenant,
                "asset_mac": asset_mac,
                "from_zone_id": int(last_zone) if last_zone and last_zone != "EXIT" else None,
                "to_zone_id": None,
                "state": "EXIT",
                "deciding_rssi": -999,
                "timestamp": now_iso(),
            }
            push_fifo(event)
            print(f"[EXIT] {tenant}/{asset_mac} marked as EXIT", flush=True)


# -------------------------------------------------
# ZONE SCORE AGGREGATION — TENANT-AWARE
# -------------------------------------------------
def compute_zone_scores(tenant: str, zones: dict) -> dict:
    scores = {}
    now = time.time()

    with SCANNER_ZONE_LOCK:
        tenant_map = SCANNER_ZONE_MAP.get(tenant, {})
        active_zones = set(tenant_map.values())

    for zone_id, scanners in zones.items():
        if zone_id not in active_zones:
            continue
        values = [
            s["rssi"]
            for s in scanners.values()
            if now - s["last_seen"] <= SCANNER_TTL
        ]
        if values:
            scores[zone_id] = sum(values) / len(values)

    return scores


# -------------------------------------------------
# CORE ZONE DECISION — TENANT-AWARE
# -------------------------------------------------
def process_asset(tenant: str, asset_mac: str):
    state = ASSET_STATE[tenant][asset_mac]
    zones = state["zones"]

    handle_lost_assets()

    zone_scores = compute_zone_scores(tenant, zones)
    if not zone_scores:
        return

    proposed_zone = max(zone_scores, key=zone_scores.get)
    proposed_rssi = zone_scores[proposed_zone]

    last_zone_raw = redis_get_last_zone(tenant, asset_mac)

    if last_zone_raw is None:
        last_zone = None
        last_rssi = None
    elif last_zone_raw == "EXIT":
        last_zone = "EXIT"
        last_rssi = None
    elif isinstance(last_zone_raw, bytes):
        last_zone_str = last_zone_raw.decode("utf-8")
        last_zone = int(last_zone_str) if last_zone_str.lstrip("-").isnumeric() else None
        last_rssi = zone_scores.get(last_zone) if last_zone is not None else None
    else:
        last_zone = int(last_zone_raw)
        last_rssi = zone_scores.get(last_zone)

    if proposed_zone == last_zone:
        state["confirm"].clear()
        state["pending_move"] = None
        return

    if last_rssi is not None and proposed_rssi <= last_rssi + HYSTERESIS_DBM:
        return

    state["confirm"][proposed_zone] += 1

    if ENABLE_DEBUG_LOGS:
        print(
            f"[CONFIRM] {tenant}/{asset_mac} → ZONE {proposed_zone} "
            f"({state['confirm'][proposed_zone]}/{ZONE_CONFIRM_COUNT})",
            flush=True,
        )

    if state["confirm"][proposed_zone] < ZONE_CONFIRM_COUNT:
        return

    now = time.time()
    pending = state["pending_move"]

    if pending is None or pending["zone_id"] != proposed_zone:
        earliest_ts = min(
            s.get("scanner_ts")
            for s in zones[proposed_zone].values()
            if s.get("scanner_ts")
        )
        state["pending_move"] = {
            "zone_id": proposed_zone,
            "start_time": now,
            "movement_ts": earliest_ts,
        }
        if ENABLE_DEBUG_LOGS:
            print(f"[DWELL-START] {tenant}/{asset_mac} → ZONE {proposed_zone}", flush=True)
        return

    if now - pending["start_time"] < DWELL_TIME_SEC:
        if ENABLE_DEBUG_LOGS:
            print(f"[DWELL-WAIT] {tenant}/{asset_mac} → ZONE {proposed_zone}", flush=True)
        return

    redis_set_last_zone(tenant, asset_mac, proposed_zone)
    state["confirm"].clear()
    state["pending_move"] = None

    event = {
        "tenant_id": tenant,
        "asset_mac": asset_mac,
        "from_zone_id": last_zone if isinstance(last_zone, int) else None,
        "to_zone_id": proposed_zone,
        "state": "ZONE",
        "deciding_rssi": round(proposed_rssi, 2),
        "timestamp": pending["movement_ts"],
    }

    push_fifo(event)
    print(f"[ZONE] {tenant}/{asset_mac}: {last_zone} → {proposed_zone}", flush=True)


# -------------------------------------------------
# MQTT CALLBACKS
# -------------------------------------------------
def normalize_id(scanner_id):
    if not scanner_id:
        return ""
    return str(scanner_id).replace(":", "").lower()


def process_single_beacon(tenant: str, payload_dict: dict, scanner_id):
    if not scanner_id:
        return

    asset_mac = payload_dict.get("mac") or payload_dict.get("beacon_mac")
    kalman_rssi = payload_dict.get("rssi")

    if isinstance(kalman_rssi, dict):
        kalman_rssi = kalman_rssi.get("kalman")

    tx_power = payload_dict.get("tx_power")
    scanner_ts = payload_dict.get("timestamp") or payload_dict.get("timestamp_utc")

    if not asset_mac or kalman_rssi is None:
        return

    asset_mac = asset_mac.upper()
    last_seen_registry[tenant][asset_mac] = time.time()

    norm_scanner_id = normalize_id(scanner_id)
    zone_id = None

    with SCANNER_ZONE_LOCK:
        tenant_map = SCANNER_ZONE_MAP.get(tenant, {})
        # 1. Exact match after normalization
        for registered_mac, zid in tenant_map.items():
            if normalize_id(registered_mac) == norm_scanner_id:
                zone_id = zid
                scanner_id = registered_mac
                break
        # 2. Prefix match fallback
        if zone_id is None:
            for registered_mac, zid in tenant_map.items():
                norm_reg = normalize_id(registered_mac)
                if norm_reg.startswith(norm_scanner_id) or norm_scanner_id.startswith(norm_reg):
                    zone_id = zid
                    scanner_id = registered_mac
                    break

    if zone_id is None:
        return

    ASSET_STATE[tenant][asset_mac]["zones"][zone_id][scanner_id] = {
        "rssi": kalman_rssi,
        "last_seen": time.time(),
        "tx_power": tx_power,
        "scanner_ts": scanner_ts,
    }

    process_asset(tenant, asset_mac)


def on_message(client, userdata, msg):
    try:
        content = msg.payload.decode()
        payload = json.loads(content)
    except Exception:
        return

    # Parse tenant from topic: ble/<TENANT>/scanner/<scanner_mac>
    parts = msg.topic.split("/")
    if len(parts) >= 2:
        tenant = parts[1]
    else:
        tenant = payload.get("tenant_id", "")

    if not tenant:
        return

    # Batch payload (contains "beacons" array)
    if "beacons" in payload and isinstance(payload["beacons"], list):
        scanner_id = payload.get("scanner_mac") or payload.get("scanner_id")
        for beacon in payload["beacons"]:
            b_scanner_id = beacon.get("scanner_mac") or beacon.get("scanner_id") or scanner_id
            process_single_beacon(tenant, beacon, b_scanner_id)
    else:
        scanner_id = payload.get("scanner_id") or payload.get("scanner_mac")
        process_single_beacon(tenant, payload, scanner_id)


# -------------------------------------------------
# START ZONE MAP REFRESH THREAD
# -------------------------------------------------
threading.Thread(target=zone_map_refresh_loop, daemon=True).start()

# -------------------------------------------------
# MQTT START
# -------------------------------------------------
mqtt_client = mqtt.Client(
    client_id="master-zone-engine",
    protocol=mqtt.MQTTv311,
    callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
    transport=MQTT_TRANSPORT,
)

if MQTT_USERNAME:
    mqtt_client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

if MQTT_TLS:
    mqtt_client.tls_set(cert_reqs=ssl.CERT_REQUIRED)

mqtt_client.on_message = on_message

print("[MASTER] Multi-tenant zone engine starting MQTT loop", flush=True)

while True:
    try:
        mqtt_client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
        mqtt_client.subscribe(f"{MQTT_TOPIC_BASE}/#")
        print(f"[MASTER] MQTT connected, subscribed to {MQTT_TOPIC_BASE}/#", flush=True)
        mqtt_client.loop_forever()
    except Exception as e:
        print(f"[MASTER] MQTT error: {e}", flush=True)
        time.sleep(5)
