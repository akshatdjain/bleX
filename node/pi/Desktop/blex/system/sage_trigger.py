"""
sage_trigger.py — Called by systemd OnFailure= when a blex-* service crashes.
Usage: python3 sage_trigger.py <failed_service_name>

Each service maps to the targeted heal function for that failure.
"""

import sys
import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PI_DIR   = os.path.dirname(BASE_DIR)
sys.path.insert(0, BASE_DIR)

import json
import sage

def _read_identity():
    """Read tenant_id and pi_mac for log context."""
    tenant_id = ""
    pi_mac    = ""
    for path in ["/etc/blex/mode.json", os.path.expanduser("~/mqtt_config.json")]:
        try:
            with open(path) as f:
                cfg = json.load(f)
            tenant_id = cfg.get("tenant_id", "")
            break
        except:
            pass
    try:
        with open("/sys/class/net/wlan0/address") as f:
            pi_mac = f.read().strip().upper()
    except:
        pass
    return tenant_id, pi_mac

def _get_master_ip(tenant_id):
    cfg = sage._read_mode()
    return cfg.get("mqtt_host", "")

SERVICE_HEAL_MAP = {
    "blex-scanner.service":     lambda t, p, ip: sage.heal_service("blex-scanner", t, p) and sage.heal_scanner_process(t, p),
    "blex-master.service":      lambda t, p, ip: sage.heal_service("blex-master", t, p) and sage.heal_master_process(t, p),
    "blex-provisioner.service": lambda t, p, ip: sage.heal_service("blex-provisioner", t, p),
    "blex-discovery.service":   lambda t, p, ip: sage.heal_service("blex-discovery", t, p),
    "blex-mode.service":        lambda t, p, ip: sage.heal_service("blex-mode", t, p),
    "mosquitto.service":        lambda t, p, ip: sage.heal_broker(ip, t, p),
    "redis-server.service":     lambda t, p, ip: sage.heal_redis(t, p),
}

if __name__ == "__main__":
    failed_svc = sys.argv[1] if len(sys.argv) > 1 else "unknown"
    tenant_id, pi_mac = _read_identity()
    master_ip = _get_master_ip(tenant_id)

    print(f"[SAGE] Triggered by OnFailure: {failed_svc}", flush=True)

    heal_fn = SERVICE_HEAL_MAP.get(failed_svc)
    if heal_fn:
        result = heal_fn(tenant_id, pi_mac, master_ip)
        print(f"[SAGE] {'Healed' if result else 'Could not heal'}: {failed_svc}", flush=True)
    else:
        # Unknown service — run full sweep
        print(f"[SAGE] Unknown service {failed_svc} — running full sweep", flush=True)
        sage.full_sweep(master_ip, tenant_id, pi_mac, source=f"onfailure:{failed_svc}")
