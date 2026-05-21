import os
import time
import json
import socket
import requests
import subprocess
import sys
from datetime import datetime, timezone

BASE_DIR    = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(BASE_DIR, "config.py")
LOG_DIR     = os.path.join(BASE_DIR, "logs")
MQTT_CONFIG = "/etc/blex/mode.json"
if not os.path.exists(MQTT_CONFIG):
    MQTT_CONFIG = os.path.expanduser("~/mqtt_config.json")

os.makedirs(LOG_DIR, exist_ok=True)

API_BASE_URL = "https://sigmatic-asc.tech/asset/api/runtime"

scanner_process   = None
discovery_process = None

def read_mqtt_config():
    try:
        with open(MQTT_CONFIG) as f:
            return json.load(f)
    except Exception:
        return {}

def fetch_master_ip(tenant_id: str) -> str:
    """Ask DGX for the master Pi's IP — this is where master_register.py posts on boot."""
    try:
        headers = {"X-Tenant-ID": tenant_id} if tenant_id else {}
        resp = requests.get(f"{API_BASE_URL}/master", headers=headers, timeout=10)
        if resp.status_code == 200:
            ip = resp.json().get("master_ip", "")
            if ip:
                print(f"[SCANNER_BOOT] Got master IP from DGX: {ip}", flush=True)
                return ip
    except Exception as e:
        print(f"[SCANNER_BOOT] Could not fetch master IP from DGX: {e}", flush=True)
    return ""

def update_config(master_ip):
    lines = []
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE) as f:
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
    print(f"[SCANNER] config.py updated → MQTT_BROKER={master_ip}", flush=True)

def start_all_processes():
    global scanner_process, discovery_process
    if not (scanner_process and scanner_process.poll() is None):
        print("[SCANNER_BOOT] Starting scanner.py", flush=True)
        scanner_process = subprocess.Popen(
            [sys.executable, os.path.join(BASE_DIR, "scanner.py")], cwd=BASE_DIR
        )
    disc_script = os.path.join(BASE_DIR, "discovery_broadcast.py")
    if os.path.exists(disc_script):
        if not (discovery_process and discovery_process.poll() is None):
            print("[SCANNER_BOOT] Starting discovery_broadcast.py", flush=True)
            discovery_process = subprocess.Popen(
                [sys.executable, disc_script], cwd=BASE_DIR
            )

def stop_all_processes():
    global scanner_process, discovery_process
    for name, proc in [("scanner.py", scanner_process), ("discovery_broadcast.py", discovery_process)]:
        if proc and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()
    scanner_process = discovery_process = None

def main():
    mqtt_cfg  = read_mqtt_config()
    mode      = mqtt_cfg.get("mode", "cloud")
    tenant_id = mqtt_cfg.get("tenant_id", "")
    print(f"[SCANNER_BOOT] mode={mode} tenant={tenant_id}", flush=True)

    if mode == "local":
        # In local mode: master Pi registered its IP to DGX on boot.
        # Fetch that IP so scanner always connects to the right broker,
        # regardless of what mqtt_host was written during provisioning.
        master_ip = ""
        retries = 0
        while not master_ip and retries < 5:
            master_ip = fetch_master_ip(tenant_id)
            if not master_ip:
                print(f"[SCANNER_BOOT] Master IP not ready yet, retrying in 5s... ({retries+1}/5)", flush=True)
                time.sleep(5)
                retries += 1

        if not master_ip:
            # Fallback to whatever provisioner wrote — better than nothing
            master_ip = mqtt_cfg.get("mqtt_host", "127.0.0.1")
            print(f"[SCANNER_BOOT] Falling back to mode.json mqtt_host: {master_ip}", flush=True)

        update_config(master_ip)
        start_all_processes()

        # Keep alive + watchdog — also re-check master IP every 60s in case it changes
        while True:
            time.sleep(60)
            start_all_processes()
            # Re-fetch master IP and restart scanner if it changed
            new_ip = fetch_master_ip(tenant_id)
            if new_ip and new_ip != master_ip:
                print(f"[SCANNER_BOOT] Master IP changed: {master_ip} → {new_ip}", flush=True)
                stop_all_processes()
                master_ip = new_ip
                update_config(master_ip)
                start_all_processes()

    else:
        # Cloud mode: scanner publishes directly to DGX MQTT
        mqtt_host = mqtt_cfg.get("mqtt_host", "sigmatic-asc.tech")
        print(f"[SCANNER_BOOT] Cloud mode — MQTT broker = {mqtt_host}", flush=True)
        update_config(mqtt_host)
        start_all_processes()
        while True:
            time.sleep(60)
            start_all_processes()

if __name__ == "__main__":
    main()
