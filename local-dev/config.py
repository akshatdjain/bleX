# config.py
# -------------------------------------------------
# Configuration for Zone-Based Asset Tracking System
# -------------------------------------------------
import os

# ---------------- MQTT ----------------
MQTT_BROKER = os.getenv("MQTT_BROKER", "10.1.2.223")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_USERNAME = os.getenv("MQTT_USERNAME", "")
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "")
MQTT_TRANSPORT = os.getenv("MQTT_TRANSPORT", "tcp") # 'websockets' or 'tcp'
MQTT_TLS = os.getenv("MQTT_TLS", "false").lower() == "true"

# Base topic where all scanners publish
MQTT_TOPIC_BASE = "ble/scanner"

# ---------------- REDIS ----------------
REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "1234")

# Redis keys
REDIS_ASSET_ZONE_KEY = "asset:zone:{}"        # asset_mac -> zone_id
REDIS_ZONE_QUEUE_KEY = "zone:movement:queue"  # FIFO queue for movements

# ---------------- DATABASE (PostgreSQL) ----------------
SCANNER_ZONE_API = os.getenv("SCANNER_ZONE_API", "http://100.125.23.80:8000/api/runtime/scanner-zone-map")
SCANNER_ZONE_REFRESH_SEC = 600   # 10 minutes

# ---------------- ZONE DECISION LOGIC ----------------
# RSSI difference (in dBm) required to confirm a zone change
HYSTERESIS_DBM = 5

# How long (seconds) scanner data is considered valid
SCANNER_TTL = 10

# Number of consecutive confirmations required
ZONE_CONFIRM_COUNT = 2

# Dwell-time filtering
DWELL_TIME_SEC = 5.0  # seconds beacon must stay in new zone

# ---------------- API ----------------
API_URL = os.getenv("API_URL", "http://100.125.23.80:8000/api/asset/movement")
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
