# config.py — BleX Scanner Configuration
# Read from ~/mqtt_config.json (written by provisioner), fall back to defaults.

import os
import json

def _load_mqtt_config():
    for path in ["/etc/blex/mode.json", os.path.expanduser("~/mqtt_config.json")]:
        try:
            with open(path) as f:
                return json.load(f)
        except Exception:
            continue
    return {}

_cfg = _load_mqtt_config()

# MQTT
MQTT_BROKER = "192.168.1.9"
MQTT_PORT      = int(_cfg.get("mqtt_port", 1883))
MQTT_USE_TLS   = bool(_cfg.get("use_tls", False))
TENANT_ID      = _cfg.get("tenant_id", "default")
MODE           = _cfg.get("mode", "local")

# Topic base — always tenant-prefixed so master can subscribe to ble/{TENANT_ID}/scanner/#
# Both local and cloud modes use the same prefix; only the broker differs.
if TENANT_ID and TENANT_ID != "default":
    MQTT_TOPIC_BASE = f"ble/{TENANT_ID}/scanner"
else:
    MQTT_TOPIC_BASE = "ble/scanner"

# Scanner timing
PUBLISH_INTERVAL = 2.0
BEACON_TTL       = 5.0

# Kalman filter tuning
KALMAN_Q = 0.008
KALMAN_R = 4.0

# System identity
SERVER_NAME = "pi-blex"
