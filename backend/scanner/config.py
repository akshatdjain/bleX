# config.py
# -------------------------------------------------
# Central configuration for BLE scanner & master
# -------------------------------------------------

# -----------------------------
# MQTT CONFIG
# -----------------------------
MQTT_BROKER = "10.1.2.223"
MQTT_PORT = 1883
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
