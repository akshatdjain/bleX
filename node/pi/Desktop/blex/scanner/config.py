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

MQTT_BROKER = "sigmatic-asc.tech"
MQTT_PORT      = int(_cfg.get("mqtt_port", 1883))
MQTT_USE_TLS   = bool(_cfg.get("use_tls", False))
TENANT_ID      = _cfg.get("tenant_id", "default")
MODE           = _cfg.get("mode", "local")

# Cloud mode credentials for DGX broker
MQTT_USERNAME  = _cfg.get("mqtt_username", "tab" if MODE == "cloud" else "")
MQTT_PASSWORD  = _cfg.get("mqtt_password", "1234" if MODE == "cloud" else "")

if TENANT_ID and TENANT_ID != "default":
    MQTT_TOPIC_BASE = f"ble/{TENANT_ID}/scanner"
else:
    MQTT_TOPIC_BASE = "ble/scanner"

PUBLISH_INTERVAL = 2.0
BEACON_TTL       = 5.0
KALMAN_Q         = 0.008
KALMAN_R         = 4.0
SERVER_NAME      = "pi-blex"
