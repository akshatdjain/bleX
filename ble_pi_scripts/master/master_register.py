# master_register.py
# Runs once on boot to register master with server (JSON registry)

import socket
import uuid
import json
import time
import os
import requests
from datetime import datetime, timezone

# -------------------------
# CONFIG
# -------------------------
SERVER_URL = "http://93.127.206.7:8000/api/runtime/master"
TIMEOUT = 3  # seconds

# -------------------------
# UTILS
# -------------------------
def get_mac():
    mac = uuid.getnode()
    return ":".join(f"{(mac >> ele) & 0xff:02X}" for ele in range(40, -1, -8))

def get_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "0.0.0.0"
    finally:
        s.close()
    return ip

def utc_now():
    return datetime.now(timezone.utc).isoformat()

# -------------------------
# UPDATE CONFIG.PY WITH OWN IP
# -------------------------
def update_config_with_own_ip(master_ip):
    config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.py")

    lines = []
    if os.path.exists(config_path):
        with open(config_path, "r") as f:
            lines = f.readlines()

    with open(config_path, "w") as f:
        written = False
        for line in lines:
            if line.startswith("MQTT_BROKER"):
                f.write(f'MQTT_BROKER = "{master_ip}"\n')
                written = True
            else:
                f.write(line)

        if not written:
            f.write(f'\nMQTT_BROKER = "{master_ip}"\n')

    print(f"[MASTER-REGISTER] config.py updated with MQTT_BROKER={master_ip}")

# -------------------------
# MAIN
# -------------------------
def main():
    master_ip = get_ip()
    payload = {
        "role": "master",
        "mac": get_mac(),
        "ip": get_ip(),
        "timestamp": utc_now()
    }

    update_config_with_own_ip(master_ip)

    try:
        r = requests.post(SERVER_URL, json=payload, timeout=TIMEOUT)
        print(f"[MASTER-REGISTER] Sent → {payload}")
        print(f"[MASTER-REGISTER] Server response: {r.status_code}")
    except Exception as e:
        print(f"[MASTER-REGISTER] Failed (non-fatal): {e}")

if __name__ == "__main__":
    main()

