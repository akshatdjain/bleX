#!/usr/bin/env python3
# scanner.py (Raspberry Pi)

# -------------------------------------------------
# BLE Scanner -> Kalman -> MQTT Publisher
# Beacon-only (iBeacon / Eddystone)
# -------------------------------------------------

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
    BEACON_TTL
)

# -------------------------------------------------
# CONSTANTS
# -------------------------------------------------
APPLE_COMPANY_ID = 76            # 0x004C
IBEACON_PREFIX = b"\x02\x15"
EDDYSTONE_UUID = "feaa"

# -------------------------------------------------
# SCANNER ID
# -------------------------------------------------
def get_scanner_mac():
    try:
        with open("/sys/class/net/wlan0/address") as f:
            return f.read().strip().upper()
    except Exception:
        return hex(uuid.getnode()).upper()

SCANNER_ID = get_scanner_mac()
SCANNER_TYPE = "master-scanner"

MQTT_TOPIC = f"{MQTT_TOPIC_BASE}/{SCANNER_ID}"

# -------------------------------------------------
# MQTT CALLBACKS
# -------------------------------------------------
def on_connect(client, userdata, flags, reason_code, properties):
    if reason_code == 0:
        print("[MQTT] Connected", flush=True)
    else:
        print(f"[MQTT] Connect failed rc={reason_code}", flush=True)

def on_disconnect(client, userdata, reason_code, properties):
    print(f"[MQTT] Disconnected rc={reason_code}, reconnecting...", flush=True)

# -------------------------------------------------
# MQTT CONNECT (SAFE)
# -------------------------------------------------
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

mqtt_client.on_connect = on_connect
mqtt_client.on_disconnect = on_disconnect
mqtt_client.reconnect_delay_set(min_delay=1, max_delay=30)

connect_mqtt_forever(mqtt_client, MQTT_BROKER, MQTT_PORT)

# 🔥 NON-BLOCKING MQTT THREAD
mqtt_client.loop_start()

# -------------------------------------------------
# STATE
# -------------------------------------------------
beacon_state = {}   # mac -> state
last_seen = {}      # mac -> timestamp

# -------------------------------------------------
# BEACON PARSING
# -------------------------------------------------
def parse_ibeacon(data: bytes):
    if not data:
        return None

    idx = data.find(IBEACON_PREFIX)
    if idx == -1 or len(data) < idx + 23:
        return None

    tx = data[idx + 22]
    return tx - 256 if tx > 127 else tx


def parse_eddystone(service_data: bytes):
    if not service_data or len(service_data) < 2:
        return None

    tx = service_data[1]
    return tx - 256 if tx > 127 else tx


def is_target_beacon(ad: AdvertisementData) -> bool:
    # iBeacon
    if APPLE_COMPANY_ID in (ad.manufacturer_data or {}):
        if parse_ibeacon(ad.manufacturer_data[APPLE_COMPANY_ID]) is not None:
            return True

    # Eddystone
    for uuid in (ad.service_data or {}).keys():
        if uuid and uuid.lower().replace("-", "") == EDDYSTONE_UUID:
            return True

    return False


# -------------------------------------------------
# BLE CALLBACK (NON-BLOCKING)
# -------------------------------------------------
def detection_callback(device: BLEDevice, ad: AdvertisementData):
    mac = device.address.upper()
    raw_rssi = ad.rssi   # ✅ CORRECT RSSI SOURCE

    if not is_target_beacon(ad):
        return

    tx_power = None

    # iBeacon TX
    try:
        if APPLE_COMPANY_ID in ad.manufacturer_data:
            tx_power = parse_ibeacon(ad.manufacturer_data[APPLE_COMPANY_ID])
    except Exception:
        pass

    # Eddystone TX
    if tx_power is None:
        try:
            for uuid, svc_data in ad.service_data.items():
                if uuid.lower().replace("-", "") == EDDYSTONE_UUID:
                    tx_power = parse_eddystone(svc_data)
                    break
        except Exception:
            pass

    # Init state
    if mac not in beacon_state:
        beacon_state[mac] = {
            "kalman": KalmanRSSI(),
            "kalman_rssi": raw_rssi,
            "raw_rssi": raw_rssi,
            "tx_power": tx_power
        }

    state = beacon_state[mac]
    state["raw_rssi"] = raw_rssi
    state["kalman_rssi"] = state["kalman"].update(raw_rssi)

    if tx_power is not None:
        state["tx_power"] = tx_power

    last_seen[mac] = time.time()


# -------------------------------------------------
# ASYNC PUBLISH LOOP (CRITICAL FIX)
# -------------------------------------------------
async def publish_loop():
    print(f"[SCANNER] BLE scanning started ({SCANNER_ID})")

    while True:
        now = time.time()
        ts = datetime.now(timezone.utc).isoformat()

        for mac in list(beacon_state.keys()):
            if now - last_seen.get(mac, 0) > BEACON_TTL:
                beacon_state.pop(mac, None)
                last_seen.pop(mac, None)
                continue

            state = beacon_state[mac]

            payload = {
                "timestamp": ts,
                "scanner_id": SCANNER_ID,
                "scanner_type": SCANNER_TYPE,
                "mac": mac,
                "rssi": {
                    "raw": state["raw_rssi"],
                    "kalman": round(state["kalman_rssi"], 2)
                },
                "tx_power": state["tx_power"]
            }

            print(
                f"[PUB] {mac} | raw={state['raw_rssi']} "
                f"| kalman={round(state['kalman_rssi'],2)} | tx={state['tx_power']}"
            )

            mqtt_client.publish(MQTT_TOPIC, json.dumps(payload))

        await asyncio.sleep(PUBLISH_INTERVAL)   # ✅ NON-BLOCKING


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
        print("\n👋 Scanner stopped.")
