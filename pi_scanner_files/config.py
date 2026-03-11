# config.py
# -------------------------------------------------
# Central configuration for BLE scanner & master
# -------------------------------------------------

# -----------------------------
# MQTT CONFIG
# -----------------------------
MQTT_BROKER = "100.125.23.80"
MQTT_PORT = 1883
MQTT_TOPIC_BASE = "ble/emp"

# -----------------------------
# SCANNER TIMING
# -----------------------------
PUBLISH_INTERVAL = 1.5   # seconds
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
