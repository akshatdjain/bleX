# config.py — BleX Master Configuration
# All values read from environment or /etc/blex/env first, then fall back to defaults.

import os

def _read_blex_env():
    """Read /etc/blex/env if it exists and return as dict."""
    env = {}
    try:
        with open("/etc/blex/env") as f:
            for line in f:
                line = line.strip()
                if "=" in line and not line.startswith("#"):
                    k, v = line.split("=", 1)
                    env[k.strip()] = v.strip()
    except FileNotFoundError:
        pass
    return env

_blex_env = _read_blex_env()

def _get(key, default=""):
    return os.getenv(key) or _blex_env.get(key) or default

# ---------------- TENANT ----------------
TENANT_ID       = _get("TENANT_ID", "")
MQTT_TOPIC_BASE = f"ble/{TENANT_ID}/scanner" if TENANT_ID else "ble/scanner"

# ---------------- MQTT ----------------
MQTT_BROKER = "192.168.1.5"
MQTT_PORT   = int(_get("MQTT_PORT", "1883"))

# ---------------- REDIS ----------------
REDIS_HOST     = _get("REDIS_HOST", "127.0.0.1")
REDIS_PORT     = int(_get("REDIS_PORT", "6379"))
REDIS_PASSWORD = _get("REDIS_PASSWORD", "1234")

# Redis keys — namespaced by tenant when set
REDIS_ASSET_ZONE_KEY = f"asset:zone:{TENANT_ID}:{{}}" if TENANT_ID else "asset:zone:{}"
REDIS_ZONE_QUEUE_KEY = f"zone:movement:queue:{TENANT_ID}" if TENANT_ID else "zone:movement:queue"

# ---------------- API ----------------
SCANNER_ZONE_API = _get("SCANNER_ZONE_API", "https://sigmatic-asc.tech/asset/api/runtime/scanner-zone-map")
API_URL          = _get("API_URL", "https://sigmatic-asc.tech/asset/api/asset/movement")
API_TIMEOUT      = 5

HEALTH_API_BASE  = _get("HEALTH_API_BASE", "https://sigmatic-asc.tech/asset/api/health")

# ---------------- ZONE DECISION LOGIC ----------------
HYSTERESIS_DBM    = 8
SCANNER_TTL       = 8
ZONE_CONFIRM_COUNT = 3
DWELL_TIME_SEC    = 8.0
LOST_TIMEOUT      = 30.0

# ---------------- LOGGING ----------------
ENABLE_DEBUG_LOGS     = True
CONSUMER_SLEEP_SEC    = 1
SCANNER_ZONE_REFRESH_SEC = 600
SCANNER_HEALTH_TIMEOUT   = 90
HEALTH_PUSH_INTERVAL     = 60
