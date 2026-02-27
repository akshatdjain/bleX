# scanner_boot.py
# ---------------------------------------------
# Scanner boot orchestrator
# P1 → register
# P2 → receive master IP
# P3 → update config.py
# Then run scanner.py
# ---------------------------------------------

import os
import time
import json
import socket
import uuid
import requests
from datetime import datetime, timezone
import subprocess
import sys

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(BASE_DIR, "config.py")

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
LOG_DIR = os.path.join(BASE_DIR, "logs")

os.makedirs(LOG_DIR, exist_ok=True)

SERVER_URL = "http://93.127.206.7:8000/api/runtime/scanner"
CONFIG_FILE = "config.py"

# ---------------------------------------------
# Utils
# ---------------------------------------------

def get_mac():
    mac = uuid.getnode()
    return ":".join(f"{(mac >> i) & 0xff:02X}" for i in range(40, -1, -8))


def get_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    finally:
        s.close()


def utc_iso():
    return datetime.now(timezone.utc).isoformat()


# ---------------------------------------------
# P1 + P2 → Register scanner & fetch master IP
# ---------------------------------------------

def register_scanner():
    payload = {
        "role": "scanner",
        "mac": get_mac(),
        "ip": get_ip(),
        "scanner_type": "pi3",   # change to "esp32" on ESP
        "timestamp": utc_iso(),
    }

    print("[SCANNER] Registering:", payload)

    resp = requests.post(SERVER_URL, json=payload, timeout=5)
    resp.raise_for_status()

    data = resp.json()
    master_ip = data.get("master_ip")

    if not master_ip:
        raise RuntimeError("No master_ip in response")

    print("[SCANNER] Master IP:", master_ip)
    return master_ip


# ---------------------------------------------
# P3 → Write master IP into config.py
# ---------------------------------------------
def update_config(master_ip):
    lines = []

    # Read existing config (if any)
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, "r") as f:
            lines = f.readlines()

    written = False
    with open(CONFIG_FILE, "w") as f:

        for line in lines:
            if line.strip().startswith("MQTT_BROKER"):
                f.write(f'MQTT_BROKER = "{master_ip}"\n')
                written = True
            else:
                f.write(line)

        # If MQTT_BROKER was not present, append it
        if not written:
            f.write(f'\nMQTT_BROKER = "{master_ip}"\n')

    print(f"[SCANNER] config.py updated → {CONFIG_FILE}")

# -------------------------------------------------
# MASTER IP WATCHER LOOP
# -------------------------------------------------
def watch_master_ip(current_ip):
    print("[SCANNER] Master-IP watcher started", flush=True)

    while True:
        time.sleep(POLL_INTERVAL)

        try:
            new_ip = register_and_get_master_ip()

            if new_ip != current_ip:
                print(
                    f"[SCANNER] Master IP changed {current_ip} → {new_ip}",
                    flush=True
                )
                stop_scanner()
                update_config(new_ip)
                start_scanner()
                current_ip = new_ip

        except Exception as e:
            print(f"[SCANNER] Watcher error (non-fatal): {e}", flush=True)
# ---------------------------------------------
# sanity check
# ---------------------------------------------
def sanity_check():
   required = [
    os.path.join(BASE_DIR, "scanner.py"),
    os.path.join(BASE_DIR, "config.py"),
   ]
   for f in required:
        if not os.path.exists(f):
            raise RuntimeError(f"Missing required file: {f}")


# ---------------------------------------------
# MAIN
# ---------------------------------------------

def main():
    while True:
        try:
            master_ip = register_scanner()
            update_config(master_ip)
            sanity_check()

            print("[SCANNER] Handing over to scanner.py", flush=True)

            os.execv(
                sys.executable,
                [sys.executable, os.path.join(BASE_DIR, "scanner.py")]
            )
            watch_master_ip(master_ip)
        except Exception as e:
            print("[SCANNER] Boot failed:", e, flush=True)
            print("[SCANNER] Retrying in 5s...", flush=True)
            time.sleep(5)

if __name__ == "__main__":
    main()
