import os
import time
import json
import socket
import requests
import subprocess
import sys
from datetime import datetime, timezone

BASE_DIR    = os.path.dirname(os.path.abspath(__file__))

import sys as _sys
_sys.path.insert(0, os.path.join(os.path.dirname(BASE_DIR), "system"))
import sage as sage_module
CONFIG_FILE = os.path.join(BASE_DIR, "config.py")
LOG_DIR     = os.path.join(BASE_DIR, "logs")
MQTT_CONFIG = "/etc/blex/mode.json"
if not os.path.exists(MQTT_CONFIG):
    MQTT_CONFIG = os.path.expanduser("~/mqtt_config.json")

os.makedirs(LOG_DIR, exist_ok=True)

API_BASE_URL      = "https://sigmatic-asc.tech/asset/api/runtime"
CLOUD_MQTT_HOST   = "sigmatic-asc.tech"
CLOUD_MQTT_PORT   = 8883
WATCHDOG_INTERVAL = 60   # seconds between health checks
MAX_MQTT_FAILURES = 3    # consecutive failures before fallback

scanner_process   = None
discovery_process = None
_consecutive_mqtt_failures = 0

def get_pi_mac() -> str:
    try:
        with open("/sys/class/net/wlan0/address") as f:
            return f.read().strip().upper()
    except Exception:
        return ""

def read_mqtt_config():
    try:
        with open(MQTT_CONFIG) as f:
            return json.load(f)
    except Exception:
        return {}

def fetch_master_ip(tenant_id: str) -> str:
    """Ask DGX for the master Pi's IP."""
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

def can_reach_mqtt(host: str, port: int, timeout: int = 5) -> bool:
    """Quick TCP probe to check if MQTT broker is reachable."""
    try:
        s = socket.create_connection((host, port), timeout=timeout)
        s.close()
        return True
    except Exception:
        return False

def update_config(broker_host: str, broker_port: int = 1883,
                  use_tls: bool = False,
                  username: str = "tab", password: str = "1234"):
    lines = []
    if os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE) as f:
            lines = f.readlines()

    updates = {
        "MQTT_BROKER":   f'MQTT_BROKER = "{broker_host}"',
        "MQTT_PORT":     f'MQTT_PORT   = {broker_port}',
        "MQTT_USE_TLS":  f'MQTT_USE_TLS = {use_tls}',
        "MQTT_USERNAME": f'MQTT_USERNAME = "{username if use_tls else ""}"',
        "MQTT_PASSWORD": f'MQTT_PASSWORD = "{password if use_tls else ""}"',
    }

    new_lines = []
    written = {k: False for k in updates}
    for line in lines:
        matched = False
        for key, new_val in updates.items():
            if line.strip().startswith(key):
                new_lines.append(new_val + "\n")
                written[key] = True
                matched = True
                break
        if not matched:
            new_lines.append(line)

    # append any keys that weren't already in the file
    for key, new_val in updates.items():
        if not written[key]:
            new_lines.append(new_val + "\n")

    with open(CONFIG_FILE, "w") as f:
        f.writelines(new_lines)
    print(f"[SCANNER_BOOT] config.py → broker={broker_host}:{broker_port} tls={use_tls}", flush=True)

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

def switch_to_cloud(tenant_id: str):
    """Emergency fallback: switch to cloud MQTT, update mode.json."""
    print("[SCANNER_BOOT] Falling back to CLOUD mode", flush=True)
    update_config(CLOUD_MQTT_HOST, CLOUD_MQTT_PORT, use_tls=True,
                  username="tab", password="1234")
    # Update mode.json so next boot also uses cloud
    try:
        cfg = read_mqtt_config()
        cfg["mode"]      = "cloud"
        cfg["mqtt_host"] = CLOUD_MQTT_HOST
        cfg["mqtt_port"] = CLOUD_MQTT_PORT
        cfg["use_tls"]   = True
        with open(MQTT_CONFIG, "w") as f:
            json.dump(cfg, f, indent=2)
        print("[SCANNER_BOOT] mode.json updated → cloud mode", flush=True)
    except Exception as e:
        print(f"[SCANNER_BOOT] Could not update mode.json: {e}", flush=True)

def main():
    global _consecutive_mqtt_failures

    mqtt_cfg  = read_mqtt_config()
    mode      = mqtt_cfg.get("mode", "cloud")
    tenant_id = mqtt_cfg.get("tenant_id", "")
    print(f"[SCANNER_BOOT] mode={mode} tenant={tenant_id}", flush=True)

    if mode == "local":
        # ── Local mode: Pi-to-Pi hub ─────────────────────────────────────────
        # Fetch master IP from DGX; retry up to 5 times then fall back to
        # whatever was written in mode.json during provisioning.
        master_ip = ""
        retries = 0
        while not master_ip and retries < 5:
            master_ip = fetch_master_ip(tenant_id)
            if not master_ip:
                print(f"[SCANNER_BOOT] Master IP not ready, retrying in 5s... ({retries+1}/5)", flush=True)
                time.sleep(5)
                retries += 1

        if not master_ip:
            master_ip = mqtt_cfg.get("mqtt_host", "127.0.0.1")
            print(f"[SCANNER_BOOT] Using mode.json fallback: {master_ip}", flush=True)

        # Verify reachability before starting
        if not can_reach_mqtt(master_ip, 1883):
            print(f"[SCANNER_BOOT] Master {master_ip}:1883 unreachable — falling back to cloud", flush=True)
            switch_to_cloud(tenant_id)
            update_config(CLOUD_MQTT_HOST, CLOUD_MQTT_PORT, use_tls=True)
            start_all_processes()
            # Run watchdog in cloud mode
            mode = "cloud"
        else:
            update_config(master_ip, 1883, use_tls=False, username="", password="")
            start_all_processes()

        if mode == "local":
            # ── Local watchdog loop ──────────────────────────────────────────
            while True:
                time.sleep(WATCHDOG_INTERVAL)
                start_all_processes()  # restart if crashed

                # Re-check config in case provisioner changed mode
                current_cfg = read_mqtt_config()
                if current_cfg.get("mode") != "local":
                    print("[SCANNER_BOOT] Mode changed externally — restarting", flush=True)
                    stop_all_processes()
                    main()
                    return

                # Re-fetch master IP
                new_ip = fetch_master_ip(tenant_id)
                if new_ip and new_ip != master_ip:
                    print(f"[SCANNER_BOOT] Master IP changed: {master_ip} → {new_ip}", flush=True)
                    stop_all_processes()
                    master_ip = new_ip
                    update_config(master_ip, 1883, use_tls=False, username="", password="")
                    start_all_processes()
                    _consecutive_mqtt_failures = 0

                # Always probe broker reachability regardless of DGX response
                if not can_reach_mqtt(master_ip, 1883):
                    _consecutive_mqtt_failures += 1
                    print(f"[SCANNER_BOOT] Broker {master_ip}:1883 unreachable ({_consecutive_mqtt_failures}/{MAX_MQTT_FAILURES})", flush=True)
                    if _consecutive_mqtt_failures >= MAX_MQTT_FAILURES:
                        print("[SCANNER_BOOT] Calling SAGE...", flush=True)
                        sage_result = sage_module.full_sweep(
                            master_ip, tenant_id, get_pi_mac(),
                            source=f"mqtt_failure_{_consecutive_mqtt_failures}"
                        )
                        if sage_result.get("checks_failed", 1) == 0:
                            print("[SAGE] Healed — staying in local mode", flush=True)
                            _consecutive_mqtt_failures = 0
                        else:
                            print("[SAGE] Could not heal — falling back to cloud", flush=True)
                            stop_all_processes()
                            switch_to_cloud(tenant_id)
                            start_all_processes()
                            _consecutive_mqtt_failures = 0
                            mode = "cloud"
                            break
                else:
                    if _consecutive_mqtt_failures > 0:
                        print(f"[SCANNER_BOOT] Broker recovered — resetting failure count", flush=True)
                    _consecutive_mqtt_failures = 0

    # ── Cloud watchdog loop ──────────────────────────────────────────────────
    if mode == "cloud":
        mqtt_host = mqtt_cfg.get("mqtt_host", CLOUD_MQTT_HOST) if mode == "cloud" else CLOUD_MQTT_HOST
        print(f"[SCANNER_BOOT] Cloud mode — broker={mqtt_host}", flush=True)
        update_config(CLOUD_MQTT_HOST, CLOUD_MQTT_PORT, use_tls=True)
        start_all_processes()

        while True:
            time.sleep(WATCHDOG_INTERVAL)
            start_all_processes()  # restart if crashed

            # Check if mode changed back to local
            current_cfg = read_mqtt_config()
            if current_cfg.get("mode") == "local":
                print("[SCANNER_BOOT] Mode changed to local — restarting", flush=True)
                stop_all_processes()
                main()
                return

if __name__ == "__main__":
    main()
