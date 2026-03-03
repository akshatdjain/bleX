# config.py
# -------------------------------------------------
# Central configuration for BLE scanner & master
# -------------------------------------------------

import json
import os

# -----------------------------
# MQTT CONFIG
# -----------------------------
MQTT_BROKER = "10.1.2.223"
MQTT_PORT = 1883

# Load dynamic config if pushed via provisioner
mqtt_json = os.path.expanduser("~/mqtt_config.json")
if os.path.exists(mqtt_json):
    try:
        with open(mqtt_json, "r") as f:
            data = json.load(f)
            MQTT_BROKER = data.get("mqtt_host", MQTT_BROKER)
            MQTT_PORT = data.get("mqtt_port", MQTT_PORT)
    except Exception:
        pass

MQTT_TOPIC_BASE = "ble/scanner"

# -----------------------------
# SCANNER TIMING
# -----------------------------
PUBLISH_INTERVAL = 3.0   # seconds
BEACON_TTL = 5.0         # seconds

# -----------------------------
# KALMAN FILTER TUNING
# -----------------------------
# Tuned for indoor RSSI smoothing
KALMAN_Q = 0.008   # process noise
KALMAN_R = 4.0     # measurement noise

# -----------------------------
# SYSTEM IDENTITY (MASTER USE)
# -----------------------------
SERVER_NAME = "pi5-master"
