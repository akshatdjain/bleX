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

def read_mode_config():
    for path in ["/etc/blex/mode.json", os.path.expanduser("~/mqtt_config.json")]:
        try:
            with open(path) as f:
                return json.load(f)
        except Exception:
            continue
    return {}

def read_tenant_id():
    try:
        with open("/etc/blex/env") as f:
            for line in f:
                if line.startswith("TENANT_ID="):
                    return line.strip().split("=", 1)[1]
    except FileNotFoundError:
        pass
    return read_mode_config().get("tenant_id", "default")

def get_broker_ip(mode, pi_ip):
    """
    Local mode: Pi IS the broker — use Pi's own IP so other scanners can connect.
    Cloud mode: use what provisioner set in mode.json.
    """
    if mode == "local":
        return pi_ip
    else:
        cfg = read_mode_config()
        return cfg.get("mqtt_host", "sigmatic-asc.tech")

def update_config(broker_ip):
    config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "config.py")
    lines = []
    if os.path.exists(config_path):
        with open(config_path) as f:
            lines = f.readlines()
    wrote = False
    with open(config_path, "w") as f:
        for line in lines:
            if line.startswith("MQTT_BROKER"):
                f.write(f'MQTT_BROKER = "{broker_ip}"\n')
                wrote = True
            else:
                f.write(line)
        if not wrote:
            f.write(f'\nMQTT_BROKER = "{broker_ip}"\n')
    print(f"[MASTER-REGISTER] config.py: MQTT_BROKER={broker_ip}", flush=True)

def main():
    cfg       = read_mode_config()
    mode      = cfg.get("mode", "cloud")
    pi_ip     = get_ip()
    pi_mac    = get_mac()
    tenant_id = read_tenant_id()
    broker_ip = get_broker_ip(mode, pi_ip)

    update_config(broker_ip)

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
        headers = {"X-Tenant-ID": tenant_id} if tenant_id else {}
        r = requests.post(SERVER_URL, json=payload, headers=headers, timeout=TIMEOUT)
        print(f"[MASTER-REGISTER] Registered → status={r.status_code}", flush=True)
    except Exception as e:
        print(f"[MASTER-REGISTER] Failed (non-fatal): {e}", flush=True)

if __name__ == "__main__":
    main()
