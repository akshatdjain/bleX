# master.py
# -------------------------------------------------
# Zone-based BLE Asset Movement Engine (MASTER)
# With Dwell-Time Filtering
# -------------------------------------------------
print("[MASTER] Script started", flush=True)

import json
import time
from datetime import datetime, timezone
from collections import defaultdict

import paho.mqtt.client as mqtt
import redis
import threading

from config import (
    MQTT_BROKER,
    MQTT_PORT,
    MQTT_TOPIC_BASE,

    REDIS_HOST,
    REDIS_PORT,
    REDIS_PASSWORD,
    REDIS_ASSET_ZONE_KEY,
    REDIS_ZONE_QUEUE_KEY,

    HYSTERESIS_DBM,
    SCANNER_TTL,
    ZONE_CONFIRM_COUNT,
    DWELL_TIME_SEC,
    LOST_TIMEOUT,
    ENABLE_DEBUG_LOGS,
)

# -------------------------------------------------
# GLOBAL LAST-SEEN REGISTRY (FOR LOST / EXIT)
# -------------------------------------------------
last_seen_registry = {}  # asset_mac -> last_seen_timestamp

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
# LOAD SCANNER → ZONE MAP (FASTAPI DRIVEN)
# -------------------------------------------------
SCANNER_ZONE_MAP = {}
SCANNER_ZONE_LOCK = threading.Lock()
MAP_VERSION = 0

import requests

def load_scanner_zone_map():
    global MAP_VERSION
    try:
        url = f"http://93.127.206.7:8000/api/runtime/scanner-zone-map/watch?version={MAP_VERSION}"
        resp = requests.get(url, timeout=65) # Long-poll timeout
        
        if resp.status_code == 200:
            data = resp.json()
            new_map = data.get("scanner_zone_map", {})
            new_version = data.get("version", MAP_VERSION)
            
            # The API returns string keys (MAC addresses) and int zone IDs.
            # Make sure keys are uppercase for consistent matching.
            new_map_upper = {k.upper(): v for k, v in new_map.items()}
            
            with SCANNER_ZONE_LOCK:
                global SCANNER_ZONE_MAP
                if SCANNER_ZONE_MAP != new_map_upper:
                    SCANNER_ZONE_MAP = new_map_upper
                    print(f"[MASTER] Scanner → Zone map reloaded via API (Version {new_version}):", flush=True)
                    for mac, zid in new_map_upper.items():
                        print(f"  {mac} → ZONE {zid}", flush=True)
                
                MAP_VERSION = new_version
            return True
        else:
            print(f"[MASTER] API returned non-200 for zone map: {resp.status_code}", flush=True)
            return False
            
    except requests.exceptions.ReadTimeout:
        # Expected behavior for long-polling when no changes occur
        return True
    except Exception as e:
        print(f"[MASTER] Failed to fetch scanner-zone map via API: {e}", flush=True)
        return False

# 🔒 INITIAL SAFE LOAD
print("[MASTER] Fetching initial scanner-zone map...", flush=True)
while not load_scanner_zone_map():
    print("[MASTER] API not ready... retrying in 5s", flush=True)
    time.sleep(5)

# -------------------------------------------------
# RELOAD LOOP (LONG-POLLING)
# -------------------------------------------------
def scanner_zone_reload_loop():
    print("[MASTER] Started zone map watcher thread (long-polling)", flush=True)
    while True:
        if not load_scanner_zone_map():
            time.sleep(5) # Delay on error


# -------------------------------------------------
# IN-MEMORY STATE (PER ASSET)
# -------------------------------------------------
ASSET_STATE = defaultdict(lambda: {
    "zones": defaultdict(dict),
    "confirm": defaultdict(int),
    "pending_move": None,
})

# -------------------------------------------------
# HELPERS
# -------------------------------------------------
def now_iso():
    return datetime.now(timezone.utc).isoformat()


def redis_get_last_zone(asset_mac):
    try:
        return redis_client.get(REDIS_ASSET_ZONE_KEY.format(asset_mac))
    except Exception as e:
        print(f"[REDIS-ERROR] get_last_zone failed for {asset_mac}: {e}", flush=True)
        return None


def redis_set_last_zone(asset_mac, zone_id):
    try:
        redis_client.set(REDIS_ASSET_ZONE_KEY.format(asset_mac), zone_id)
    except Exception as e:
        print(f"[REDIS-ERROR] set_last_zone failed for {asset_mac}: {e}", flush=True)


def push_fifo(event: dict):
    try:
        redis_client.rpush(REDIS_ZONE_QUEUE_KEY, json.dumps(event))
    except Exception:
        pass

# -------------------------------------------------
# LOST / EXIT HANDLER (UNCHANGED LOGIC)
# -------------------------------------------------
def handle_lost_assets():
    now = time.time()

    for asset_mac, last_seen in list(last_seen_registry.items()):
        if now - last_seen < LOST_TIMEOUT:
            continue

        last_zone = redis_get_last_zone(asset_mac)

        if last_zone == "EXIT":
            continue

        redis_set_last_zone(asset_mac, "EXIT")

        event = {
            "asset_mac": asset_mac,
            "from_zone_id": int(last_zone) if last_zone and last_zone != "EXIT" else None,
            "to_zone_id": None,
            "state": "EXIT",
            "deciding_rssi": -999,
            "timestamp": now_iso(),
        }

        push_fifo(event)
        print(f"[EXIT] {asset_mac} marked as EXIT", flush=True)

# -------------------------------------------------
# ZONE SCORE AGGREGATION
# -------------------------------------------------
def compute_zone_scores(zones: dict):
    scores = {}
    now = time.time()

    with SCANNER_ZONE_LOCK:
        # 🛡️ Empty Zones Rule: Only consider zones that currently have >=1 mapped scanner
        active_zones = set(SCANNER_ZONE_MAP.values())

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
# CORE ZONE DECISION (CONFIRM + DWELL)
# -------------------------------------------------
def process_asset(asset_mac: str):
    state = ASSET_STATE[asset_mac]
    zones = state["zones"]

    handle_lost_assets()

    zone_scores = compute_zone_scores(zones)
    if not zone_scores:
        return

    proposed_zone = max(zone_scores, key=zone_scores.get)
    proposed_rssi = zone_scores[proposed_zone]

    # 🛡️ FRESHNESS GUARD: A new zone movement can only START if we have data 
    # from that zone within the last 5 seconds.
    # This prevents moving into "phantom" or "stale" zones when a scanner dies.
    FRESH_THRESHOLD = 5.0
    is_fresh = any(
        (now - s["last_seen"]) <= FRESH_THRESHOLD 
        for s in zones[proposed_zone].values()
    )

    last_zone_raw = redis_get_last_zone(asset_mac)

    if last_zone_raw is None:
        last_zone = None
        last_rssi = None
    elif last_zone_raw == "EXIT":
        last_zone = "EXIT"
        last_rssi = None
    else:
        try:
            # Handle potential bytes from Redis
            last_zone_str = last_zone_raw.decode('utf-8') if isinstance(last_zone_raw, bytes) else str(last_zone_raw)
            last_zone = int(last_zone_str) if last_zone_str.lstrip('-').isnumeric() and last_zone_str != "EXIT" else last_zone_str
            last_rssi = zone_scores.get(last_zone) if isinstance(last_zone, int) else None
        except Exception:
            last_zone = last_zone_raw
            last_rssi = None

    if proposed_zone == last_zone:
        state["confirm"].clear()
        state["pending_move"] = None
        return

    # Enforce Hysteresis
    if last_rssi is not None and proposed_rssi <= last_rssi + HYSTERESIS_DBM:
        return

    # If proposing a NEW zone (not currently pending it), it MUST BE FRESH
    pending = state.get("pending_move")
    if (pending is None or pending["zone_id"] != proposed_zone) and not is_fresh:
        if ENABLE_DEBUG_LOGS and state["confirm"][proposed_zone] > 0:
             print(f"[STALE-SKIP] {asset_mac} → ZONE {proposed_zone} (not fresh enough)", flush=True)
        return

    state["confirm"][proposed_zone] += 1

    if ENABLE_DEBUG_LOGS:
        print(
            f"[CONFIRM] {asset_mac} → ZONE {proposed_zone} "
            f"({state['confirm'][proposed_zone]}/{ZONE_CONFIRM_COUNT}) "
            f"RSSI: {proposed_rssi:.1f} (Last: {f'{last_rssi:.1f}' if last_rssi is not None else 'N/A'})",
            flush=True
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
            print(f"[DWELL-START] {asset_mac} → ZONE {proposed_zone}", flush=True)
        return

    if now - pending["start_time"] < DWELL_TIME_SEC:
        if ENABLE_DEBUG_LOGS:
            print(f"[DWELL-WAIT] {asset_mac} → ZONE {proposed_zone}", flush=True)
        return

    redis_set_last_zone(asset_mac, proposed_zone)
    state["confirm"].clear()
    state["pending_move"] = None

    event = {
        "asset_mac": asset_mac,
        "from_zone_id": last_zone if isinstance(last_zone, int) else None,
        "to_zone_id": proposed_zone,
        "state": "ZONE",
        "deciding_rssi": round(proposed_rssi, 2),
        "timestamp": pending["movement_ts"],
    }

    push_fifo(event)
    print(f"[ZONE] {asset_mac}: {last_zone} → {proposed_zone}", flush=True)

# -------------------------------------------------
# START RELOAD THREAD
# -------------------------------------------------
threading.Thread(
    target=scanner_zone_reload_loop,
    daemon=True
).start()

# -------------------------------------------------
# MQTT CALLBACK (COMPATIBLE WITH CALLBACK API v2)
# -------------------------------------------------
def normalize_id(scanner_id):
    """Normalize ID by removing colons and converting to lowercase."""
    if not scanner_id: return ""
    return str(scanner_id).replace(":", "").lower()

def process_single_beacon(payload_dict, scanner_id):
    if not scanner_id:
        return

    asset_mac = payload_dict.get("mac") or payload_dict.get("beacon_mac")
    kalman_rssi = payload_dict.get("rssi")
    
    # Handle flat RSSI (tab) vs nested ESP32 format
    if isinstance(kalman_rssi, dict):
        kalman_rssi = kalman_rssi.get("kalman")
        
    tx_power = payload_dict.get("tx_power")
    # Support both timestamp formats
    scanner_ts = payload_dict.get("timestamp") or payload_dict.get("timestamp_utc")

    if not asset_mac or kalman_rssi is None:
        return

    asset_mac = asset_mac.upper()
    last_seen_registry[asset_mac] = time.time()

    norm_scanner_id = normalize_id(scanner_id)
    zone_id = None

    with SCANNER_ZONE_LOCK:
        # 1. Try exact match (after normalization)
        for registered_mac, zid in SCANNER_ZONE_MAP.items():
            if normalize_id(registered_mac) == norm_scanner_id:
                zone_id = zid
                # Update scanner_id to the registered one for consistency in ASSET_STATE
                scanner_id = registered_mac
                break
        
        # 2. Try prefix match if not found (e.g. bfccc566 matches BF:CC:C5:66:19:86)
        if zone_id is None:
            for registered_mac, zid in SCANNER_ZONE_MAP.items():
                norm_reg = normalize_id(registered_mac)
                if norm_reg.startswith(norm_scanner_id) or norm_scanner_id.startswith(norm_reg):
                    zone_id = zid
                    scanner_id = registered_mac
                    break
        
    if zone_id is None:
        # If still not found, we don't know which zone this scanner belongs to
        return

    ASSET_STATE[asset_mac]["zones"][zone_id][scanner_id] = {
        "rssi": kalman_rssi,
        "last_seen": time.time(),
        "tx_power": tx_power,
        "scanner_ts": scanner_ts,
    }

    process_asset(asset_mac)

def on_message(client, userdata, msg):
    try:
        content = msg.payload.decode()
        payload = json.loads(content)
    except Exception:
        return

    # 1. Batch / Tab Payload (contains "beacons" array)
    if "beacons" in payload and isinstance(payload["beacons"], list):
        scanner_id = payload.get("scanner_mac") or payload.get("scanner_id")
        for beacon in payload["beacons"]:
            # If beacon dict doesn't have scanner_id, use the parent's
            b_scanner_id = beacon.get("scanner_mac") or beacon.get("scanner_id") or scanner_id
            process_single_beacon(beacon, b_scanner_id)
            
    # 2. Standard / ESP32 Flat Payload
    else:
        scanner_id = payload.get("scanner_id") or payload.get("scanner_mac")
        process_single_beacon(payload, scanner_id)

# -------------------------------------------------
# MQTT START (CALLBACK API v2)
# -------------------------------------------------
mqtt_client = mqtt.Client(
    client_id="master-zone-engine",
    protocol=mqtt.MQTTv311,
    callback_api_version=mqtt.CallbackAPIVersion.VERSION2
)

mqtt_client.on_message = on_message

print("[MASTER] Zone-based master starting MQTT loop", flush=True)

while True:
    try:
        mqtt_client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
        mqtt_client.subscribe(f"{MQTT_TOPIC_BASE}/#")
        print("[MASTER] MQTT connected", flush=True)
        mqtt_client.loop_forever()
    except Exception as e:
        print(f"[MASTER] MQTT error: {e}", flush=True)
        time.sleep(5)
