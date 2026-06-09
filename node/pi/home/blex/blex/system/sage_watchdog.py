"""
sage_watchdog.py — Periodic SAGE health checker.
Runs as blex-sage-watch.service

Every 5 minutes: quick targeted checks — only heals what's broken.
At midnight UTC: full sweep + daily report.
"""

import os
import sys
import time
import json
from datetime import datetime, timezone

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE_DIR)
import sage

WATCH_INTERVAL_SEC  = 300   # 5 minutes
DAILY_REPORT_HOUR   = 0     # midnight UTC

def _read_identity():
    tenant_id = os.getenv("TENANT_ID", "")
    if not tenant_id:
        tenant_id = sage._read_mode().get("tenant_id", "")
    pi_mac = ""
    try:
        with open("/sys/class/net/wlan0/address") as f:
            pi_mac = f.read().strip().upper()
    except:
        pass
    return tenant_id, pi_mac

def _quick_checks(tenant_id, pi_mac, master_ip):
    """
    Lightweight sweep — only checks the most likely failure points.
    Skips slow checks (MQTT connect test, full API check).
    """
    mode = sage._read_mode()
    is_local = mode.get("mode") == "local"

    sage.heal_scanner_process(tenant_id, pi_mac)
    sage.heal_service("blex-discovery", tenant_id, pi_mac)
    sage.heal_service("blex-provisioner", tenant_id, pi_mac)
    sage.heal_mqtt_auth(tenant_id, pi_mac)

    if is_local:
        sage.heal_broker(master_ip, tenant_id, pi_mac)
        sage.heal_redis(tenant_id, pi_mac)
        sage.heal_master_process(tenant_id, pi_mac)
        sage.heal_fifo_process(tenant_id, pi_mac)

def main():
    print("[SAGE-WATCH] Watchdog started", flush=True)
    last_daily = -1

    while True:
        tenant_id, pi_mac = _read_identity()
        cfg       = sage._read_mode()
        master_ip = cfg.get("mqtt_host", "")

        now_hour = datetime.now(timezone.utc).hour
        if now_hour == DAILY_REPORT_HOUR and last_daily != datetime.now(timezone.utc).date():
            print("[SAGE-WATCH] Running daily report...", flush=True)
            sage.daily_report(master_ip, tenant_id, pi_mac)
            last_daily = datetime.now(timezone.utc).date()
        else:
            _quick_checks(tenant_id, pi_mac, master_ip)

        time.sleep(WATCH_INTERVAL_SEC)

if __name__ == "__main__":
    main()
