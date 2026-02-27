# config.py
# -------------------------------------------------
# Configuration for Zone-Based Asset Tracking System
# -------------------------------------------------

# ---------------- MQTT ----------------
MQTT_BROKER = "10.246.22.26"
MQTT_PORT = 1883

# Base topic where all scanners publish
MQTT_TOPIC_BASE = "ble/scanner"

# ---------------- REDIS ----------------
REDIS_HOST = "127.0.0.1"
REDIS_PORT = 6379
REDIS_PASSWORD = "1234"

# Redis keys
REDIS_ASSET_ZONE_KEY = "asset:zone:{}"        # asset_mac -> zone_id
REDIS_ZONE_QUEUE_KEY = "zone:movement:queue"  # FIFO queue for movements

# ---------------- DATABASE (PostgreSQL) ----------------
DB_HOST = "93.127.206.7"
DB_PORT = 5432
DB_NAME = "asset_tracking"
DB_USER = "postgres"
DB_PASSWORD = "Samartha@123"

# ---------------- ZONE DECISION LOGIC ----------------
# RSSI difference (in dBm) required to confirm a zone change
HYSTERESIS_DBM = 5

# How long (seconds) scanner data is considered valid
SCANNER_TTL = 5

# Number of consecutive confirmations required
ZONE_CONFIRM_COUNT = 2

# Dwell-time filtering
DWELL_TIME_SEC = 5.0  # seconds beacon must stay in new zone

# ---------------- API ----------------
API_URL = "http://93.127.206.7:8000/api/asset/movement"
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
LOST_TIMEOUT = 10.0   # seconds
