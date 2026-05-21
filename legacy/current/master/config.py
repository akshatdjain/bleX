# config.py
# -------------------------------------------------
# Configuration for Zone-Based Asset Tracking System
# -------------------------------------------------

import os

# ---------------- MQTT ----------------
MQTT_BROKER = os.getenv("MQTT_BROKER", "192.168.1.158")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))

# Base topic where all scanners publish
TENANT_ID       = os.getenv("TENANT_ID", "")
MQTT_TOPIC_BASE = f"ble/{TENANT_ID}/scanner" if TENANT_ID else "ble/scanner"

# ---------------- REDIS ----------------
REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "1234")

# Redis keys
REDIS_ASSET_ZONE_KEY = f"asset:zone:{TENANT_ID}:{{}}" if TENANT_ID else "asset:zone:{}"
REDIS_ZONE_QUEUE_KEY = f"zone:movement:queue:{TENANT_ID}" if TENANT_ID else "zone:movement:queue"

# ---------------- DATABASE (PostgreSQL) ----------------
#DB_HOST = "93.127.206.7"
#DB_PORT = 5432
#DB_NAME = "asset_tracking"
#DB_USER = "postgres"
#DB_PASSWORD = "Samartha@123"
SCANNER_ZONE_API = os.getenv("SCANNER_ZONE_API", "https://sigmatic-asc.tech/asset/api/runtime/scanner-zone-map")
SCANNER_ZONE_REFRESH_SEC = 600   # 10 minutes

# ---------------- ZONE DECISION LOGIC ----------------
# RSSI difference (in dBm) required to confirm a zone change
HYSTERESIS_DBM = 5

# How long (seconds) scanner data is considered valid
SCANNER_TTL = 5

# Number of consecutive confirmations required
ZONE_CONFIRM_COUNT = 2

# Dwell-time filtering
DWELL_TIME_SEC = 2.0  # seconds beacon must stay in new zone

# ---------------- API ----------------
API_URL = os.getenv("API_URL", "https://sigmatic-asc.tech/asset/api/asset/movement")
API_TIMEOUT = 5  # seconds

# ---------------- LOGGING / DEBUG ----------------
ENABLE_DEBUG_LOGS = True

# Consumer
CONSUMER_SLEEP_SEC = 1

# -------------------------------------------------
# LOST / EXIT DETECTION
# -------------------------------------------------

# If a beacon is NOT seen by ANY scanner for this many seconds,
# it is considered to have EXITED all zones.
LOST_TIMEOUT = 30.0   # seconds

# -------------------------------------------------
# HEALTH MONITORING
# -------------------------------------------------

# How long (seconds) before a scanner is considered offline.
# Master checks every 60s; push health data to API every 5 min.
SCANNER_HEALTH_TIMEOUT = 300   # 5 minutes
HEALTH_PUSH_INTERVAL  = 120   # 5 minutes

# Base URL for asset_api health endpoints (port 8000 = asset_api, port 8001 = ui_api)
HEALTH_API_BASE = os.getenv("HEALTH_API_BASE", "https://sigmatic-asc.tech/asset/api/health")
