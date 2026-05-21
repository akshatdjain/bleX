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
import psycopg2

from config import (
    MQTT_BROKER,
    MQTT_PORT,
    MQTT_TOPIC_BASE,

    REDIS_HOST,
    REDIS_PORT,
    REDIS_PASSWORD,
    REDIS_ASSET_ZONE_KEY,
    REDIS_ZONE_QUEUE_KEY,

    DB_HOST,
    DB_PORT,
    DB_NAME,
    DB_USER,
    DB_PASSWORD,

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
redis_client = redis.Redis(
    host=REDIS_HOST,
    port=REDIS_PORT,
    password=REDIS_PASSWORD,
    decode_responses=True,
)

# -------------------------------------------------
# LOAD SCANNER → ZONE MAP (UNCHANGED LOGIC)
# -------------------------------------------------
def load_scanner_zone_map():
    conn = psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD,
    )
    cur = conn.cursor()

    cur.execute("""
        SELECT s.mac_id, z.id
        FROM mst_zone_scanner zs
        JOIN mst_scanner s ON s.id = zs.mst_scanner_id
        JOIN mst_zone z ON z.id = zs.mst_zone_id
    """)

    mapping = {mac.upper(): zid for mac, zid in cur.fetchall()}
    cur.close()
    conn.close()
    return mapping


# 🔒 SAFE LOAD (NO CHANGE IN RESULT)
while True:
    try:
        SCANNER_ZONE_MAP = load_scanner_zone_map()
        break
    except Exception as e:
        print(f"[MASTER] DB not ready: {e}", flush=True)
        time.sleep(5)

print("[MASTER] Scanner → Zone map:")
for mac, zid in SCANNER_ZONE_MAP.items():
    print(f"  {mac} → ZONE {zid}")

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
    except Exception:
        return None


def redis_set_last_zone(asset_mac, zone_id):
    try:
        redis_client.set(REDIS_ASSET_ZONE_KEY.format(asset_mac), zone_id)
    except Exception:
        pass


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

    for zone_id, scanners in zones.items():
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

    last_zone_raw = redis_get_last_zone(asset_mac)

    if last_zone_raw is None:
        last_zone = None
        last_rssi = None
    elif last_zone_raw == "EXIT":
        last_zone = "EXIT"
        last_rssi = None
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
            f"[CONFIRM] {asset_mac} → ZONE {proposed_zone} "
            f"({state['confirm'][proposed_zone]}/{ZONE_CONFIRM_COUNT})",
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
# MQTT CALLBACK (COMPATIBLE WITH CALLBACK API v2)
# -------------------------------------------------
def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
    except Exception:
        return

    asset_mac = payload.get("mac")
    scanner_id = payload.get("scanner_id")
    kalman_rssi = payload.get("rssi", {}).get("kalman")
    tx_power = payload.get("tx_power")
    scanner_ts = payload.get("timestamp")

    if not asset_mac or kalman_rssi is None:
        return

    asset_mac = asset_mac.upper()
    last_seen_registry[asset_mac] = time.time()

    zone_id = SCANNER_ZONE_MAP.get(scanner_id)
    if zone_id is None:
        return

    ASSET_STATE[asset_mac]["zones"][zone_id][scanner_id] = {
        "rssi": kalman_rssi,
        "last_seen": time.time(),
        "tx_power": tx_power,
        "scanner_ts": scanner_ts,
    }

    process_asset(asset_mac)

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
