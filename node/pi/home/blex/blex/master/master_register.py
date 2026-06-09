import socket
import json
import re
import time
import os
import subprocess
import requests
from datetime import datetime, timezone

SERVER_URL = "https://sigmatic-asc.tech/asset/api/runtime/master"
TIMEOUT    = 5

def _api_headers(tenant_id: str = "") -> dict:
    h = {}
    if tenant_id:
        h["X-Tenant-ID"] = tenant_id
    token = os.getenv("BLEX_API_TOKEN", "")
    if token:
        h["Authorization"] = f"Bearer {token}"
    return h

def get_mac():
    for iface in ["wlan0", "eth0", "wlan1"]:
        try:
            with open(f"/sys/class/net/{iface}/address") as f:
                mac = f.read().strip().upper()
                if mac and mac != "00:00:00:00:00:00":
                    return mac
        except FileNotFoundError:
            continue
    return "00:00:00:00:00:00"

def get_ip():
    try:
        result = subprocess.run(["ip", "-4", "addr", "show", "wlan0"], capture_output=True, text=True)
        match = re.search(r'inet (\d+\.\d+\.\d+\.\d+)', result.stdout)
        if match:
            return match.group(1)
    except Exception:
        pass
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "0.0.0.0"
    finally:
        s.close()

def utc_now():
    return datetime.now(timezone.utc).isoformat()

def get_broker_ip(mode, pi_ip):
    """
    Local mode: Pi IS the broker — use Pi's own IP so other scanners can connect.
    Cloud mode: use what provisioner set in MQTT_BROKER env var.
    """
    if mode == "local":
        return pi_ip
    return os.getenv("MQTT_BROKER", "sigmatic-asc.tech")

def main():
    mode      = os.getenv("MODE", "cloud")
    pi_ip     = get_ip()
    pi_mac    = get_mac()
    tenant_id = os.getenv("TENANT_ID", "default")
    broker_ip = get_broker_ip(mode, pi_ip)

    print(f"[MASTER-REGISTER] mode={mode} mac={pi_mac} pi_ip={pi_ip} broker={broker_ip} tenant={tenant_id}", flush=True)

    if pi_mac == "00:00:00:00:00:00":
        print("[MASTER-REGISTER] WARNING: Could not read real MAC — skipping DGX registration", flush=True)
        return

    payload = {
        "role":      "master",
        "mac":       pi_mac,
        "ip":        pi_ip,
        "tenant_id": tenant_id,
        "timestamp": utc_now(),
    }

    try:
        headers = _api_headers(tenant_id)
        r = requests.post(SERVER_URL, json=payload, headers=headers, timeout=TIMEOUT)
        print(f"[MASTER-REGISTER] Registered → status={r.status_code}", flush=True)
    except Exception as e:
        print(f"[MASTER-REGISTER] Failed (non-fatal): {e}", flush=True)

if __name__ == "__main__":
    main()
