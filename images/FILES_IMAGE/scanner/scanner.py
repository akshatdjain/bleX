#!/usr/bin/env python3
# scanner.py — BleX BLE Scanner
# Scans for iBeacon + Eddystone beacons and publishes to MQTT

import os
os.environ["BLEAK_DBUS_DEEP_SCAN"] = "1"

import asyncio
import time
import json
import uuid
import socket
from datetime import datetime, timezone

import paho.mqtt.client as mqtt
from bleak import BleakScanner, BLEDevice, AdvertisementData

from kalman import KalmanRSSI
from config import (
    MQTT_BROKER,
    MQTT_PORT,
    MQTT_TOPIC_BASE,
    PUBLISH_INTERVAL,
    BEACON_TTL,
    MQTT_USE_TLS,
)

# -------------------------------------------------
# TENANT ID LOADING
# -------------------------------------------------
def _load_tenant_id() -> str:
    for path in ["/etc/blex/mode.json", os.path.expanduser("~/mqtt_config.json")]:
        try:
            with open(path) as f:
                cfg = json.load(f)
            tid = cfg.get("tenant_id", "default")
            if tid and tid != "default":
                return tid
        except Exception:
            continue
    return "default"

# -------------------------------------------------
# CONSTANTS
# -------------------------------------------------
APPLE_COMPANY_ID = 76
IBEACON_PREFIX = b"\x02\x15"
EDDYSTONE_UUID = "feaa"
BATTERY_SVC_UUID = "fff0"

# -------------------------------------------------
# SCANNER ID
# -------------------------------------------------
def get_scanner_mac():
    try:
        with open("/sys/class/net/wlan0/address") as f:
            return f.read().strip().upper()
    except Exception:
        return hex(uuid.getnode()).upper()

SCANNER_ID   = get_scanner_mac()
SCANNER_TYPE = "master-scanner"

_TENANT_ID = _load_tenant_id()

# Topic: ble/{tenant_id}/scanner/{mac} in cloud mode, ble/scanner/{mac} in local
if _TENANT_ID and _TENANT_ID != "default":
    MQTT_TOPIC = f"ble/{_TENANT_ID}/scanner/{SCANNER_ID}"
else:
    MQTT_TOPIC = f"{MQTT_TOPIC_BASE}/{SCANNER_ID}"

print(f"[SCANNER] tenant_id={_TENANT_ID} topic={MQTT_TOPIC}", flush=True)

# -------------------------------------------------
# MQTT CLIENT
# -------------------------------------------------
def on_connect(client, userdata, flags, reason_code, properties):
    if reason_code == 0:
        print("[MQTT] Connected", flush=True)
    else:
        print(f"[MQTT] Connect failed rc={reason_code}", flush=True)

def on_disconnect(client, userdata, disconnect_flags, reason_code, properties):
    print(f"[MQTT] Disconnected rc={reason_code}, reconnecting...", flush=True)

def connect_mqtt_forever(client, broker, port):
    while True:
        try:
            print(f"[MQTT] Connecting to {broker}:{port}", flush=True)
            client.connect(broker, port, keepalive=60)
            return
        except (socket.timeout, OSError) as e:
            print(f"[MQTT] Connect failed: {e}. Retrying in 5s...", flush=True)
            time.sleep(5)

mqtt_client = mqtt.Client(
    client_id=f"scanner-{SCANNER_ID.replace(':','')[-6:]}",
    protocol=mqtt.MQTTv311,
    callback_api_version=mqtt.CallbackAPIVersion.VERSION2
)

mqtt_client.on_connect    = on_connect
mqtt_client.on_disconnect = on_disconnect
mqtt_client.reconnect_delay_set(min_delay=1, max_delay=30)

# TLS for cloud/direct mode
if MQTT_USE_TLS:
    import ssl
    mqtt_client.tls_set(cert_reqs=ssl.CERT_REQUIRED)
    print("[MQTT] TLS enabled", flush=True)

connect_mqtt_forever(mqtt_client, MQTT_BROKER, MQTT_PORT)
mqtt_client.loop_start()

# -------------------------------------------------
# STATE
# -------------------------------------------------
beacon_state = {}
last_seen    = {}

# -------------------------------------------------
# BEACON PARSING
# -------------------------------------------------
def parse_ibeacon(data: bytes):
    if not data: return None
    idx = data.find(IBEACON_PREFIX)
    if idx == -1 or len(data) < idx + 23: return None
    tx = data[idx + 22]
    return tx - 256 if tx > 127 else tx

def parse_eddystone(service_data: bytes):
    if not service_data or len(service_data) < 2: return None
    tx = service_data[1]
    return tx - 256 if tx > 127 else tx

def is_target_beacon(ad: AdvertisementData) -> bool:
    if APPLE_COMPANY_ID in (ad.manufacturer_data or {}):
        if parse_ibeacon(ad.manufacturer_data[APPLE_COMPANY_ID]) is not None:
            return True
    for u in (ad.service_data or {}).keys():
        if u and u.lower().replace("-", "") == EDDYSTONE_UUID:
            return True
    return False

def parse_battery(ad: AdvertisementData):
    for svc_uuid, data in (ad.service_data or {}).items():
        if svc_uuid.lower().startswith("0000fff0"):
            if data and len(data) >= 1:
                return int(data[-1])
    return None

# -------------------------------------------------
# BLE CALLBACK
# -------------------------------------------------
def detection_callback(device: BLEDevice, ad: AdvertisementData):
    mac      = device.address.upper()
    raw_rssi = ad.rssi

    if not is_target_beacon(ad):
        return

    tx_power = None
    battery  = None

    try:
        if APPLE_COMPANY_ID in ad.manufacturer_data:
            tx_power = parse_ibeacon(ad.manufacturer_data[APPLE_COMPANY_ID])
    except Exception:
        pass

    if tx_power is None:
        try:
            for u, svc_data in ad.service_data.items():
                if u.lower().replace("-", "") == EDDYSTONE_UUID:
                    tx_power = parse_eddystone(svc_data)
                    break
        except Exception:
            pass

    try:
        battery = parse_battery(ad)
    except Exception:
        pass

    if mac not in beacon_state:
        beacon_state[mac] = {
            "kalman": KalmanRSSI(),
            "kalman_rssi": raw_rssi,
            "raw_rssi": raw_rssi,
            "tx_power": tx_power,
            "battery": battery,
        }

    state = beacon_state[mac]
    state["raw_rssi"]    = raw_rssi
    state["kalman_rssi"] = state["kalman"].update(raw_rssi)

    if tx_power is not None:
        state["tx_power"] = tx_power
    if battery is not None:
        state["battery"] = battery

    last_seen[mac] = time.time()

# -------------------------------------------------
# PUBLISH LOOP
# -------------------------------------------------
async def publish_loop():
    print(f"[SCANNER] BLE scanning started ({SCANNER_ID})")

    while True:
        now = time.time()
        ts  = datetime.now(timezone.utc).isoformat()

        for mac in list(beacon_state.keys()):
            if now - last_seen.get(mac, 0) > BEACON_TTL:
                beacon_state.pop(mac, None)
                last_seen.pop(mac, None)
                continue

            state = beacon_state[mac]
            payload = {
                "timestamp":    ts,
                "scanner_id":   SCANNER_ID,
                "scanner_type": SCANNER_TYPE,
                "tenant_id":    _TENANT_ID,
                "mac":          mac,
                "rssi": {
                    "raw":    state["raw_rssi"],
                    "kalman": round(state["kalman_rssi"], 2)
                },
                "tx_power": state["tx_power"],
                "battery":  state.get("battery"),
            }

            print(
                f"[PUB] {mac} | raw={state['raw_rssi']} "
                f"| kalman={round(state['kalman_rssi'],2)} | tx={state['tx_power']}"
            )

            mqtt_client.publish(MQTT_TOPIC, json.dumps(payload))

        # Heartbeat: always publish scanner alive ping even when no beacons detected
        heartbeat = {
            "timestamp":    ts,
            "scanner_id":   SCANNER_ID,
            "scanner_type": SCANNER_TYPE,
            "tenant_id":    _TENANT_ID,
            "type":         "heartbeat",
            "beacon_count": len([m for m in beacon_state if time.time() - last_seen.get(m, 0) <= BEACON_TTL]),
        }
        mqtt_client.publish(MQTT_TOPIC, json.dumps(heartbeat))

        await asyncio.sleep(PUBLISH_INTERVAL)

# -------------------------------------------------
# MAIN
# -------------------------------------------------
async def main():
    scanner = BleakScanner(detection_callback)
    async with scanner:
        await publish_loop()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[SCANNER] Stopped.")
