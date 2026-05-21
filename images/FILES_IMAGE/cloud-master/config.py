# config.py — Cloud Master Configuration
import os

# ---------------- MQTT ----------------
MQTT_BROKER    = os.getenv("MQTT_BROKER", "sigmatic-asc.tech")
MQTT_PORT      = int(os.getenv("MQTT_PORT", "8883"))
MQTT_USERNAME  = os.getenv("MQTT_USERNAME", "tab")
MQTT_PASSWORD  = os.getenv("MQTT_PASSWORD", "1234")
MQTT_TRANSPORT = os.getenv("MQTT_TRANSPORT", "tcp")
MQTT_TLS       = os.getenv("MQTT_TLS", "true").lower() == "true"

TENANT_ID       = os.getenv("TENANT_ID", "")
MQTT_TOPIC_BASE = os.getenv("MQTT_TOPIC_BASE", f"ble/{TENANT_ID}/scanner" if TENANT_ID else "ble/scanner")

# ---------------- REDIS ----------------
REDIS_HOST     = os.getenv("REDIS_HOST", "master_redis")
REDIS_PORT     = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "")

# Redis keys — tenant-namespaced so each tenant's data is isolated
REDIS_ASSET_ZONE_KEY = f"asset:zone:{TENANT_ID}:{{}}" if TENANT_ID else "asset:zone:{}"
REDIS_ZONE_QUEUE_KEY = f"zone:movement:queue:{TENANT_ID}" if TENANT_ID else "zone:movement:queue"

# ---------------- API ----------------
SCANNER_ZONE_API     = os.getenv("SCANNER_ZONE_API", "http://asset_tracking-asset_api-1:8000/api/runtime/scanner-zone-map")
API_URL              = os.getenv("API_URL", "http://asset_tracking-asset_api-1:8000/api/asset/movement")
HEALTH_API_BASE      = os.getenv("HEALTH_API_BASE", "http://asset_tracking-asset_api-1:8000/api/health")
API_TIMEOUT          = 5
SCANNER_ZONE_REFRESH_SEC = 600

# ---------------- ZONE DECISION LOGIC ----------------
HYSTERESIS_DBM     = 8     # dBm difference required to trigger zone change
SCANNER_TTL        = 25    # seconds scanner data is valid (higher for cloud — tablet batches)
ZONE_CONFIRM_COUNT = 3     # consecutive confirmations required
DWELL_TIME_SEC     = 8.0   # seconds beacon must stay in proposed zone before commit
LOST_TIMEOUT       = 30.0  # seconds before asset marked EXIT

# ---------------- LOGGING ----------------
ENABLE_DEBUG_LOGS  = True
CONSUMER_SLEEP_SEC = 1
