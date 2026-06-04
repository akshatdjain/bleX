# master.py
# -------------------------------------------------
# Zone-based BLE Asset Movement Engine (MASTER)
# With Dwell-Time Filtering
# -------------------------------------------------

import json
import time
import os
import sys
from datetime import datetime, timezone
from collections import defaultdict

# cypher: shared structured logger
_BLEX_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _BLEX_DIR not in sys.path:
    sys.path.insert(0, _BLEX_DIR)
from cypher import get_logger
log = get_logger("master")

import paho.mqtt.client as mqtt
import redis
import threading
import requests

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
    SCANNER_HEALTH_TIMEOUT,
    HEALTH_PUSH_INTERVAL,
    HEALTH_API_BASE,
)

log.info("master started")

last_seen_registry = {}
scanner_last_seen   = {}
scanner_last_active = {}
asset_battery_cache = {}

redis_kwargs = {
    "host": REDIS_HOST,
    "port": REDIS_PORT,
    "decode_responses": True,
}
if REDIS_PASSWORD:
    redis_kwargs["password"] = REDIS_PASSWORD

redis_client = redis.Redis(**redis_kwargs)

SCANNER_ZONE_MAP = {}
SCANNER_ZONE_LOCK = threading.Lock()
MAP_VERSION = 0


def load_scanner_zone_map():
    global MAP_VERSION
    try:
        from config import SCANNER_ZONE_API, TENANT_ID
        url = f"{SCANNER_ZONE_API}/watch?version={MAP_VERSION}"
        headers = {"X-Tenant-ID": TENANT_ID} if TENANT_ID else {}
        resp = requests.get(url, headers=headers, timeout=65)

        if resp.status_code == 200:
            data = resp.json()
            new_map = data.get("scanner_zone_map", {})
            new_version = data.get("version", MAP_VERSION)
            new_map_upper = {k.upper(): v for k, v in new_map.items()}

            with SCANNER_ZONE_LOCK:
                global SCANNER_ZONE_MAP
                if SCANNER_ZONE_MAP != new_map_upper:
                    SCANNER_ZONE_MAP = new_map_upper
                    log.info("zone map reloaded", extra={
                        "version": new_version,
                        "scanner_count": len(new_map_upper),
                    })
                MAP_VERSION = new_version
            return True
        else:
            log.warning("zone map api non-200", extra={"status": resp.status_code})
            return False

    except requests.exceptions.ReadTimeout:
        return True
    except Exception:
        log.error("zone map fetch failed", exc_info=True)
        return False


log.info("fetching initial zone map")
while not load_scanner_zone_map():
    log.warning("api not ready, retrying in 5s")
    time.sleep(5)


def scanner_zone_reload_loop():
    log.info("zone map watcher thread started")
    while True:
        if not load_scanner_zone_map():
            time.sleep(5)


ASSET_STATE = defaultdict(lambda: {
    "zones": defaultdict(dict),
    "confirm": defaultdict(int),
    "pending_move": None,
})


def now_iso():
    return datetime.now(timezone.utc).isoformat()


def redis_get_last_zone(asset_mac):
    try:
        return redis_client.get(REDIS_ASSET_ZONE_KEY.format(asset_mac))
    except Exception:
        log.error("redis get_last_zone failed", extra={"asset_mac": asset_mac}, exc_info=True)
        return None


def redis_set_last_zone(asset_mac, zone_id):
    try:
        redis_client.set(REDIS_ASSET_ZONE_KEY.format(asset_mac), zone_id)
    except Exception:
        log.error("redis set_last_zone failed", extra={"asset_mac": asset_mac}, exc_info=True)


def push_fifo(event: dict):
    try:
        redis_client.rpush(REDIS_ZONE_QUEUE_KEY, json.dumps(event))
    except Exception:
        pass


def handle_lost_assets():
    now = time.time()
    for asset_mac, last_seen in list(last_seen_registry.items()):
        if now - last_seen < LOST_TIMEOUT:
            continue
        last_zone = redis_get_last_zone(asset_mac)
        if last_zone == "EXIT":
            continue
        redis_set_last_zone(asset_mac, "EXIT")
        from config import TENANT_ID
        event = {
            "tenant_id": TENANT_ID,
            "asset_mac": asset_mac,
            "from_zone_id": int(last_zone) if last_zone and last_zone != "EXIT" else None,
            "to_zone_id": None,
            "state": "EXIT",
            "deciding_rssi": -999,
            "timestamp": now_iso(),
        }
        push_fifo(event)
        log.info("asset exit", extra={
            "asset_mac": asset_mac,
            "from_zone": int(last_zone) if last_zone and last_zone != "EXIT" else None,
        })


def compute_zone_scores(zones: dict):
    scores = {}
    now = time.time()
    with SCANNER_ZONE_LOCK:
        active_zones = set(SCANNER_ZONE_MAP.values())
    for zone_id, scanners in zones.items():
        if zone_id not in active_zones:
            continue
        values = [s["rssi"] for s in scanners.values() if now - s["last_seen"] <= SCANNER_TTL]
        if values:
            scores[zone_id] = max(values)
    return scores


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
    elif isinstance(last_zone_raw, bytes):
        last_zone_str = last_zone_raw.decode('utf-8')
        last_zone = int(last_zone_str) if last_zone_str.lstrip('-').isnumeric() else None
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
        log.debug("zone confirm tick", extra={
            "asset_mac": asset_mac,
            "proposed_zone": proposed_zone,
            "count": state["confirm"][proposed_zone],
            "needed": ZONE_CONFIRM_COUNT,
        })
    if state["confirm"][proposed_zone] < ZONE_CONFIRM_COUNT:
        return
    now = time.time()
    pending = state["pending_move"]
    if pending is None or pending["zone_id"] != proposed_zone:
        earliest_ts = min(
            s.get("scanner_ts") for s in zones[proposed_zone].values() if s.get("scanner_ts")
        )
        state["pending_move"] = {"zone_id": proposed_zone, "start_time": now, "movement_ts": earliest_ts}
        if ENABLE_DEBUG_LOGS:
            log.debug("dwell started", extra={"asset_mac": asset_mac, "zone": proposed_zone})
        return
    if now - pending["start_time"] < DWELL_TIME_SEC:
        if ENABLE_DEBUG_LOGS:
            log.debug("dwell waiting", extra={"asset_mac": asset_mac, "zone": proposed_zone})
        return
    redis_set_last_zone(asset_mac, proposed_zone)
    state["confirm"].clear()
    state["pending_move"] = None
    from config import TENANT_ID
    event = {
        "tenant_id": TENANT_ID,
        "asset_mac": asset_mac,
        "from_zone_id": last_zone if isinstance(last_zone, int) else None,
        "to_zone_id": proposed_zone,
        "state": "ZONE",
        "deciding_rssi": round(proposed_rssi, 2),
        "timestamp": pending["movement_ts"],
    }
    push_fifo(event)
    log.info("zone change", extra={
        "asset_mac": asset_mac,
        "from_zone": last_zone,
        "to_zone": proposed_zone,
        "rssi": round(proposed_rssi, 2),
    })


def check_scanner_health():
    from config import PUBLISH_INTERVAL
    now = time.time()
    report = []
    with SCANNER_ZONE_LOCK:
        registered = dict(SCANNER_ZONE_MAP)
    for scanner_mac, zone_id in registered.items():
        mac_upper = scanner_mac.upper()
        last_hb   = scanner_last_seen.get(mac_upper, 0)
        last_act  = scanner_last_active.get(mac_upper, 0)
        elapsed   = now - last_hb
        if last_hb > 0 and elapsed < SCANNER_HEALTH_TIMEOUT:
            scanner_status = "active" if last_act > 0 and (now - last_act) < (PUBLISH_INTERVAL * 3) else "idle"
        else:
            scanner_status = "offline"
        report.append({
            "scanner_mac":        scanner_mac,
            "zone_id":            zone_id,
            "is_online":          scanner_status != "offline",
            "scanner_status":     scanner_status,
            "last_seen_ago_sec":  round(elapsed) if last_hb > 0 else None,
        })
    return report


def health_push_loop():
    log.info("health push loop started")
    while True:
        time.sleep(HEALTH_PUSH_INTERVAL)
        now = time.time()
        from config import TENANT_ID
        headers = {"X-Tenant-ID": TENANT_ID} if TENANT_ID else {}
        scanner_health = check_scanner_health()
        try:
            resp = requests.post(f"{HEALTH_API_BASE}/scanners/bulk", json=scanner_health, headers=headers, timeout=5)
            log.debug("scanner health pushed", extra={"status": resp.status_code, "count": len(scanner_health)})
        except Exception:
            log.error("scanner health push failed", exc_info=True)
        beacon_health = []
        for asset_mac, last_ts in list(last_seen_registry.items()):
            beacon_health.append({
                "asset_mac": asset_mac,
                "battery": asset_battery_cache.get(asset_mac),
                "last_seen_ago_sec": round(now - last_ts),
                "is_alive": (now - last_ts) < LOST_TIMEOUT,
            })
        try:
            resp = requests.post(f"{HEALTH_API_BASE}/beacons/bulk", json=beacon_health, headers=headers, timeout=5)
            log.debug("beacon health pushed", extra={"status": resp.status_code, "count": len(beacon_health)})
        except Exception:
            log.error("beacon health push failed", exc_info=True)


threading.Thread(target=scanner_zone_reload_loop, daemon=True).start()
threading.Thread(target=health_push_loop, daemon=True, name="health-push").start()


def normalize_id(scanner_id):
    if not scanner_id: return ""
    return str(scanner_id).replace(":", "").lower()


def process_single_beacon(payload_dict, scanner_id):
    if not scanner_id:
        return
    asset_mac = payload_dict.get("mac") or payload_dict.get("beacon_mac")
    kalman_rssi = payload_dict.get("rssi")
    if isinstance(kalman_rssi, dict):
        kalman_rssi = kalman_rssi.get("kalman")
    tx_power = payload_dict.get("tx_power")
    scanner_ts = payload_dict.get("timestamp") or payload_dict.get("timestamp_utc")
    battery = payload_dict.get("battery")
    if not asset_mac or kalman_rssi is None:
        return
    asset_mac = asset_mac.upper()
    last_seen_registry[asset_mac] = time.time()
    if battery is not None:
        asset_battery_cache[asset_mac] = battery
    norm_scanner_id = normalize_id(scanner_id)
    zone_id = None
    with SCANNER_ZONE_LOCK:
        for registered_mac, zid in SCANNER_ZONE_MAP.items():
            if normalize_id(registered_mac) == norm_scanner_id:
                zone_id = zid
                scanner_id = registered_mac
                break
        if zone_id is None:
            for registered_mac, zid in SCANNER_ZONE_MAP.items():
                norm_reg = normalize_id(registered_mac)
                if norm_reg.startswith(norm_scanner_id) or norm_scanner_id.startswith(norm_reg):
                    zone_id = zid
                    scanner_id = registered_mac
                    break
    if zone_id is None:
        return
    scanner_last_seen[scanner_id.upper()] = time.time()
    ASSET_STATE[asset_mac]["zones"][zone_id][scanner_id] = {
        "rssi": kalman_rssi,
        "last_seen": time.time(),
        "tx_power": tx_power,
        "scanner_ts": scanner_ts,
    }
    process_asset(asset_mac)


def on_message(client, userdata, msg):
    try:
        topic_parts = msg.topic.split("/")
        topic_scanner_mac = topic_parts[-1].upper() if len(topic_parts) >= 1 else None
        if topic_scanner_mac and ":" in topic_scanner_mac:
            scanner_last_seen[topic_scanner_mac] = time.time()
    except Exception:
        pass
    try:
        content = msg.payload.decode()
        payload = json.loads(content)
    except Exception:
        return
    if payload.get("type") == "heartbeat":
        hb_scanner = payload.get("scanner_id") or payload.get("scanner_mac")
        if hb_scanner:
            hb_upper = hb_scanner.upper()
            scanner_last_seen[hb_upper] = time.time()
            if payload.get("beacon_count", 0) > 0:
                scanner_last_active[hb_upper] = time.time()
        return
    if "beacons" in payload and isinstance(payload["beacons"], list):
        scanner_id = payload.get("scanner_mac") or payload.get("scanner_id")
        for beacon in payload["beacons"]:
            b_scanner_id = beacon.get("scanner_mac") or beacon.get("scanner_id") or scanner_id
            process_single_beacon(beacon, b_scanner_id)
    else:
        scanner_id = payload.get("scanner_id") or payload.get("scanner_mac")
        process_single_beacon(payload, scanner_id)


mqtt_client = mqtt.Client(
    client_id="master-zone-engine",
    protocol=mqtt.MQTTv311,
    callback_api_version=mqtt.CallbackAPIVersion.VERSION2
)
mqtt_client.on_message = on_message

log.info("starting mqtt loop", extra={"broker": MQTT_BROKER, "port": MQTT_PORT})

while True:
    try:
        mqtt_client.connect(MQTT_BROKER, MQTT_PORT, keepalive=60)
        mqtt_client.subscribe(f"{MQTT_TOPIC_BASE}/#")
        log.info("mqtt connected", extra={"broker": MQTT_BROKER, "topic": f"{MQTT_TOPIC_BASE}/#"})
        mqtt_client.loop_forever()
    except Exception:
        log.error("mqtt error, retrying in 5s", exc_info=True)
        time.sleep(5)
