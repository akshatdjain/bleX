# scanner_boot.py
# ---------------------------------------------
# Scanner boot orchestrator
# P1 → register
# P2 → receive master IP
# P3 → update config.py
# Then start scanner.py
# Also watches master IP changes
# ---------------------------------------------

import os
import time
import json
import socket
import uuid
import requests
import subprocess
import sys
from datetime import datetime, timezone

# ---------------------------------------------
# PATHS / CONFIG
# ---------------------------------------------

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(BASE_DIR, "config.py")
LOG_DIR = os.path.join(BASE_DIR, "logs")

os.makedirs(LOG_DIR, exist_ok=True)

# Base API URL
API_BASE_URL = "http://93.127.206.7:8000/api/runtime"
SERVER_URL = f"{API_BASE_URL}/scanner"
WATCH_URL = f"{API_BASE_URL}/master/watch"

scanner_process = None
discovery_process = None
provisioner_process = None
current_master_ip = None

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
        "scanner_type": "pi3",
        "timestamp": utc_iso(),
    }

    print("[SCANNER] Registering:", payload, flush=True)

    resp = requests.post(SERVER_URL, json=payload, timeout=5)
    resp.raise_for_status()

    data = resp.json()
    master_ip = data.get("master_ip")

    if not master_ip:
        raise RuntimeError("No master_ip in response")

    print("[SCANNER] Master IP:", master_ip, flush=True)
    return master_ip

# Alias to preserve semantics
def register_and_get_master_ip():
    return register_scanner()

# ---------------------------------------------
# P3 → Write master IP into config.py
# ---------------------------------------------

def update_config(master_ip):
    lines = []

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

        if not written:
            f.write(f'\nMQTT_BROKER = "{master_ip}"\n')

    print(f"[SCANNER] config.py updated → {CONFIG_FILE}", flush=True)

# ---------------------------------------------
# SUBPROCESS CONTROL
# ---------------------------------------------

def start_all_processes():
    global scanner_process, discovery_process, provisioner_process

    # 1. Start scanner.py
    if not (scanner_process and scanner_process.poll() is None):
        print("[SCANNER_BOOT] Starting scanner.py", flush=True)
        scanner_process = subprocess.Popen(
            [sys.executable, os.path.join(BASE_DIR, "scanner.py")],
            cwd=BASE_DIR
        )

    # 2. Start discovery_broadcast.py
    disc_script = os.path.join(BASE_DIR, "discovery_broadcast.py")
    if os.path.exists(disc_script):
        if not (discovery_process and discovery_process.poll() is None):
            print("[SCANNER_BOOT] Starting discovery_broadcast.py", flush=True)
            discovery_process = subprocess.Popen(
                [sys.executable, disc_script],
                cwd=BASE_DIR
            )
    else:
        print(f"[SCANNER_BOOT] Skipping discovery_broadcast (file missing: {disc_script})", flush=True)

    # 3. Start provisioner_service.py
    prov_script = os.path.join(BASE_DIR, "provisioner_service.py")
    if os.path.exists(prov_script):
        if not (provisioner_process and provisioner_process.poll() is None):
            print("[SCANNER_BOOT] Starting provisioner_service.py", flush=True)
            provisioner_process = subprocess.Popen(
                [sys.executable, prov_script],
                cwd=BASE_DIR
            )
    else:
        print(f"[SCANNER_BOOT] Skipping provisioner_service (file missing: {prov_script})", flush=True)


def stop_all_processes():
    global scanner_process, discovery_process, provisioner_process

    processes = [
        ("scanner.py", scanner_process),
        ("discovery_broadcast.py", discovery_process),
        ("provisioner_service.py", provisioner_process)
    ]

    for name, proc in processes:
        if proc and proc.poll() is None:
            print(f"[SCANNER_BOOT] Stopping {name}", flush=True)
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()

    scanner_process = None
    discovery_process = None
    provisioner_process = None

# ---------------------------------------------
# MASTER IP WATCHER LOOP
# ---------------------------------------------

def watch_master_ip(current_ip):
    print(f"[SCANNER] Master-IP watcher started (long-polling) for changes from {current_ip}", flush=True)

    while True:
        try:
            # Long-polling request (will hang until IP changes or timeout occurs)
            resp = requests.get(f"{WATCH_URL}?current_ip={current_ip}", timeout=65)
            
            if resp.status_code == 200:
                data = resp.json()
                new_ip = data.get("master_ip")
                
                if new_ip and new_ip != current_ip:
                    print(f"[SCANNER] Master IP changed! {current_ip} → {new_ip}", flush=True)
                    stop_all_processes()
                    update_config(new_ip)
                    start_all_processes()
                    current_ip = new_ip
            else:
                print(f"[SCANNER] Watcher returned non-200: {resp.status_code}", flush=True)
                time.sleep(5) # wait before retrying on error

        except requests.exceptions.ReadTimeout:
            # Expected behavior if 60 seconds pass without a DB change. Just loop again.
            pass
        except Exception as e:
            print(f"[SCANNER] Watcher error (non-fatal): {e}", flush=True)
            time.sleep(5) # Avoid rapid-fire errors if server is totally down

# ---------------------------------------------
# sanity check
# ---------------------------------------------

def sanity_check():
    required = [
        os.path.join(BASE_DIR, "scanner.py"),
        CONFIG_FILE,
    ]
    for f in required:
        if not os.path.exists(f):
            raise RuntimeError(f"Missing required file: {f}")

# ---------------------------------------------
# MAIN
# ---------------------------------------------

def main():
    global current_master_ip

    while True:
        try:
            current_master_ip = register_scanner()
            update_config(current_master_ip)
            sanity_check()

            start_all_processes()
            watch_master_ip(current_master_ip)

        except Exception as e:
            print("[SCANNER] Boot failed:", e, flush=True)
            print("[SCANNER] Retrying in 5s...", flush=True)
            time.sleep(5)

if __name__ == "__main__":
    main()
