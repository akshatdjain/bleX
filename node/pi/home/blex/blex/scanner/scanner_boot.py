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
LOG_DIR = os.path.join(os.path.dirname(BASE_DIR), "logs")
os.makedirs(LOG_DIR, exist_ok=True)

API_BASE_URL      = "https://sigmatic-asc.tech/asset/api/runtime"
CLOUD_MQTT_HOST   = "sigmatic-asc.tech"
CLOUD_MQTT_PORT   = 8883
WATCHDOG_INTERVAL = 60   # seconds between health checks
MAX_MQTT_FAILURES = 3    # consecutive failures before fallback
BLEX_ENV_FILE     = "/etc/blex/blex.env"

def _api_headers(tenant_id: str = "") -> dict:
    h = {}
    if tenant_id:
        h["X-Tenant-ID"] = tenant_id
    token = os.getenv("BLEX_API_TOKEN", "")
    if token:
        h["Authorization"] = f"Bearer {token}"
    return h

scanner_process   = None
discovery_process = None
_consecutive_mqtt_failures = 0

def get_pi_mac() -> str:
    try:
        with open("/sys/class/net/wlan0/address") as f:
            return f.read().strip().upper()
    except Exception:
        return ""

def read_blex_env():
    """Read /etc/blex/blex.env into a dict (also already in os.environ)."""
    cfg = {}
    try:
        with open(BLEX_ENV_FILE) as f:
            for line in f:
                line = line.strip()
                if "=" in line and not line.startswith("#"):
                    k, v = line.split("=", 1)
                    cfg[k.strip()] = v.strip()
    except Exception:
        pass
    return cfg

def fetch_master_ip(tenant_id: str) -> str:
    """Ask DGX for the master Pi's IP."""
    try:
        headers = _api_headers(tenant_id)
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

def cloud_reachable(attempts: int = 3, timeout: int = 2) -> bool:
    """Probe cloud broker via TCP. Pass if at least 2 of 3 attempts succeed."""
    ok = 0
    for _ in range(attempts):
        if can_reach_mqtt(CLOUD_MQTT_HOST, CLOUD_MQTT_PORT, timeout=timeout):
            ok += 1
    return ok >= 2

def pick_cloud_broker():
    """Returns env dict for the active cloud-mode broker target.
    Cloud reachable -> direct cloud broker.
    Cloud down + TABLET_HOST set -> tablet's embedded Moquette (it bridges to cloud).
    Else None -> caller keeps current broker (no change)."""
    if cloud_reachable():
        return {
            "MQTT_BROKER":   CLOUD_MQTT_HOST,
            "MQTT_PORT":     str(CLOUD_MQTT_PORT),
            "MQTT_USE_TLS":  "true",
            "MQTT_USERNAME": os.getenv("MQTT_USERNAME", ""),
            "MQTT_PASSWORD": os.getenv("MQTT_PASSWORD", ""),
        }
    tablet_host = os.getenv("TABLET_HOST", "")
    if tablet_host:
        return {
            "MQTT_BROKER":   tablet_host,
            "MQTT_PORT":     os.getenv("TABLET_PORT", "1883"),
            "MQTT_USE_TLS":  os.getenv("TABLET_USE_TLS", "false"),
            "MQTT_USERNAME": os.getenv("TABLET_USERNAME", ""),
            "MQTT_PASSWORD": os.getenv("TABLET_PASSWORD", ""),
        }
    return None

def update_blex_env(updates: dict):
    """Update keys in /etc/blex/blex.env AND os.environ so child processes inherit them.
    updates: {"MQTT_BROKER": "1.2.3.4", "MQTT_PORT": "1883", ...} (values as strings)"""
    lines = []
    try:
        if os.path.exists(BLEX_ENV_FILE):
            with open(BLEX_ENV_FILE) as f:
                lines = f.readlines()
    except Exception:
        lines = []
    written = {k: False for k in updates}
    out = []
    for line in lines:
        stripped = line.strip()
        matched = False
        for k, v in updates.items():
            if stripped.startswith(k + "="):
                out.append(f"{k}={v}\n"); written[k] = True; matched = True; break
        if not matched:
            out.append(line)
    for k, v in updates.items():
        if not written[k]:
            out.append(f"{k}={v}\n")
    try:
        with open(BLEX_ENV_FILE, "w") as f:
            f.writelines(out)
    except Exception as e:
        print(f"[SCANNER_BOOT] Could not write {BLEX_ENV_FILE}: {e}", flush=True)
    # Make spawned children (scanner.py) inherit immediately
    for k, v in updates.items():
        os.environ[k] = v

def start_all_processes():
    global scanner_process, discovery_process
    if not (scanner_process and scanner_process.poll() is None):
        print("[SCANNER_BOOT] Starting scanner.py", flush=True)
        scanner_process = subprocess.Popen(
            [sys.executable, os.path.join(BASE_DIR, "scanner.py")], cwd=BASE_DIR
        )
    disc_script = os.path.join(os.path.dirname(BASE_DIR), "provisioner", "discovery_broadcast.py")
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
    """Emergency fallback: switch broker to cloud and persist via blex.env."""
    print("[SCANNER_BOOT] Falling back to CLOUD mode", flush=True)
    update_blex_env({
        "MODE": "cloud",
        "MQTT_BROKER": CLOUD_MQTT_HOST,
        "MQTT_PORT": str(CLOUD_MQTT_PORT),
        "MQTT_USE_TLS": "true",
        "MQTT_USERNAME": os.getenv("MQTT_USERNAME", ""),
        "MQTT_PASSWORD": os.getenv("MQTT_PASSWORD", ""),
    })

def main():
    global _consecutive_mqtt_failures

    mqtt_cfg  = read_blex_env()
    mode      = os.getenv("MODE", mqtt_cfg.get("MODE", "cloud"))
    tenant_id = os.getenv("TENANT_ID", mqtt_cfg.get("TENANT_ID", ""))
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
            master_ip = mqtt_cfg.get("MQTT_BROKER", "127.0.0.1")
            print(f"[SCANNER_BOOT] Using mode.json fallback: {master_ip}", flush=True)

        # Verify reachability before starting
        if not can_reach_mqtt(master_ip, 1883):
            print(f"[SCANNER_BOOT] Master {master_ip}:1883 unreachable — falling back to cloud", flush=True)
            switch_to_cloud(tenant_id)
            update_blex_env({"MQTT_BROKER": CLOUD_MQTT_HOST, "MQTT_PORT": str(CLOUD_MQTT_PORT), "MQTT_USE_TLS": "true", "MQTT_USERNAME": os.getenv("MQTT_USERNAME", ""), "MQTT_PASSWORD": os.getenv("MQTT_PASSWORD", "")})
            start_all_processes()
            # Run watchdog in cloud mode
            mode = "cloud"
        else:
            update_blex_env({"MQTT_BROKER": master_ip, "MQTT_PORT": "1883", "MQTT_USE_TLS": "false", "MQTT_USERNAME": "", "MQTT_PASSWORD": ""})
            start_all_processes()

        if mode == "local":
            # ── Local watchdog loop ──────────────────────────────────────────
            while True:
                time.sleep(WATCHDOG_INTERVAL)
                start_all_processes()  # restart if crashed

                # Re-check config in case provisioner changed mode
                current_cfg = read_blex_env()
                if current_cfg.get("MODE") != "local":
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
                    update_blex_env({"MQTT_BROKER": master_ip, "MQTT_PORT": "1883", "MQTT_USE_TLS": "false", "MQTT_USERNAME": "", "MQTT_PASSWORD": ""})
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
        # Pick initial target: cloud if reachable, else tablet fallback if set.
        target = pick_cloud_broker()
        if target is None:
            # No cloud, no tablet — fall back to whatever blex.env had.
            target = {
                "MQTT_BROKER":   mqtt_cfg.get("MQTT_BROKER", CLOUD_MQTT_HOST) or CLOUD_MQTT_HOST,
                "MQTT_PORT":     str(mqtt_cfg.get("MQTT_PORT", CLOUD_MQTT_PORT)),
                "MQTT_USE_TLS":  mqtt_cfg.get("MQTT_USE_TLS", "true"),
                "MQTT_USERNAME": mqtt_cfg.get("MQTT_USERNAME", ""),
                "MQTT_PASSWORD": mqtt_cfg.get("MQTT_PASSWORD", ""),
            }
        print(f"[SCANNER_BOOT] Cloud mode — broker={target['MQTT_BROKER']}:{target['MQTT_PORT']}", flush=True)
        update_blex_env(target)
        start_all_processes()
        current_broker = target["MQTT_BROKER"]

        while True:
            time.sleep(WATCHDOG_INTERVAL)
            start_all_processes()  # restart if crashed

            # Mode change check
            current_cfg = read_blex_env()
            if current_cfg.get("MODE") == "local":
                print("[SCANNER_BOOT] Mode changed to local — restarting", flush=True)
                stop_all_processes()
                main()
                return

            # Internet-aware fallback: re-evaluate broker target.
            new_target = pick_cloud_broker()
            if new_target is None:
                continue  # cloud down + no tablet — keep current
            if new_target["MQTT_BROKER"] != current_broker:
                print(f"[SCANNER_BOOT] Broker switching: {current_broker} → {new_target['MQTT_BROKER']}", flush=True)
                stop_all_processes()
                update_blex_env(new_target)
                current_broker = new_target["MQTT_BROKER"]
                start_all_processes()

if __name__ == "__main__":
    main()
