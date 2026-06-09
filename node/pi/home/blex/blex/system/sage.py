"""
SAGE — Self-healing Automated Guardian Engine
BleX Pi — Targeted heal functions + full sweep

Each heal_* function:
  - Diagnoses one specific problem
  - Attempts to fix it
  - Logs the result
  - Returns True (healed) or False (needs human)

Trigger sources:
  - systemd OnFailure= → sage_trigger.py calls heal_service(name)
  - scanner_boot.py   → heal_broker() + heal_master_ip() on MQTT failure
  - master.py         → heal_redis() on Redis connection error
  - watchdog          → full_sweep() every 5 min
  - cron              → daily_report() at midnight
"""

import os
import logging
import json
import time
import socket
import subprocess
import requests
from datetime import datetime, timezone

BASE_DIR   = os.path.dirname(os.path.abspath(__file__))
PI_DIR     = os.path.dirname(BASE_DIR)
import sys as _sys
if PI_DIR not in _sys.path:
    _sys.path.insert(0, PI_DIR)
from cypher import get_logger
_log = get_logger("sage")
LOG_DIR  = os.path.join(PI_DIR, "logs")
ENV_FILE = "/etc/blex/blex.env"
os.makedirs(LOG_DIR, exist_ok=True)

API_BASE   = "https://sigmatic-asc.tech/asset"
CLOUD_HOST = "sigmatic-asc.tech"
CLOUD_PORT = 8883


def _api_headers(tenant_id: str = "") -> dict:
    h = {}
    if tenant_id:
        h["X-Tenant-ID"] = tenant_id
    token = os.getenv("BLEX_API_TOKEN", "")
    if token:
        h["Authorization"] = f"Bearer {token}"
    return h

# ── Utilities ────────────────────────────────────────────────────────────────

def _ts():
    return datetime.now(timezone.utc).isoformat()

def _run(cmd, timeout=10):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return r.returncode, r.stdout.strip(), r.stderr.strip()
    except Exception as e:
        return 1, "", str(e)

def _tcp(host, port, timeout=5):
    try:
        s = socket.create_connection((host, port), timeout=timeout)
        s.close()
        return True
    except:
        return False

def _svc_active(name):
    _, out, _ = _run(["systemctl", "is-active", name])
    return out == "active"

def _svc_restart(name):
    rc, _, _ = _run(["sudo", "systemctl", "restart", name])
    return rc == 0

def _svc_start(name):
    rc, _, _ = _run(["sudo", "systemctl", "start", name])
    return rc == 0

def _should_be_active(service_name) -> bool:
    """
    Returns True if this service SHOULD be running given current mode/state.
    Prevents SAGE from trying to restart services that are correctly inactive.
    """
    # blex-master only runs in local mode (flag set by blex-mode.service)
    if service_name == "blex-master.service":
        return os.path.exists("/run/blex/mode-local") and os.path.exists("/run/blex/role-master")

    # blex-scanner only runs when provisioned
    if service_name == "blex-scanner.service":
        return not os.path.exists("/run/blex/unprovisioned")

    # All other services (provisioner, discovery, mode) always run
    return True

def _proc_running(name):
    rc, out, _ = _run(["pgrep", "-f", name])
    return rc == 0 and bool(out)

def _read_mode():
    """Read /etc/blex/blex.env into a dict using mode.json-style keys.
    Translates MODE/ROLE/MQTT_BROKER/etc. into the lowercase keys old callers expect."""
    raw = {}
    try:
        with open(ENV_FILE) as f:
            for line in f:
                line = line.strip()
                if "=" in line and not line.startswith("#"):
                    k, v = line.split("=", 1)
                    raw[k.strip()] = v.strip()
    except Exception:
        return {}
    return {
        "mode":       raw.get("MODE", ""),
        "role":       raw.get("ROLE", ""),
        "tenant_id":  raw.get("TENANT_ID", ""),
        "mqtt_host":  raw.get("MQTT_BROKER", ""),
        "mqtt_port":  int(raw["MQTT_PORT"]) if raw.get("MQTT_PORT", "").isdigit() else 0,
        "use_tls":    raw.get("MQTT_USE_TLS", "false").lower() == "true",
    }

def _read_scanner_config():
    vals = {}
    try:
        with open("/etc/blex/blex.env") as f:
            for line in f:
                line = line.strip()
                for key in ["MQTT_BROKER", "MQTT_PORT", "MQTT_USE_TLS", "MQTT_USERNAME", "MQTT_PASSWORD"]:
                    if line.startswith(key + "="):
                        vals[key] = line.split("=", 1)[1].strip().strip('"').strip("'")
    except Exception:
        pass
    return vals

def _write_scanner_config(broker, port, use_tls=False, username="", password=""):
    """Write MQTT config to /etc/blex/blex.env (no more config.py patching)."""
    updates = {
        "MQTT_BROKER": str(broker),
        "MQTT_PORT": str(port),
        "MQTT_USE_TLS": "true" if use_tls else "false",
        "MQTT_USERNAME": str(username),
        "MQTT_PASSWORD": str(password),
    }
    env_file = "/etc/blex/blex.env"
    try:
        lines = []
        if os.path.exists(env_file):
            with open(env_file) as f:
                lines = f.readlines()
        written = {k: False for k in updates}
        out = []
        for line in lines:
            s = line.strip()
            matched = False
            for k, v in updates.items():
                if s.startswith(k + "="):
                    out.append(f"{k}={v}\n"); written[k] = True; matched = True; break
            if not matched:
                out.append(line)
        for k, v in updates.items():
            if not written[k]:
                out.append(f"{k}={v}\n")
        with open(env_file, "w") as f:
            f.writelines(out)
        return True
    except Exception as e:
        _log_event("write_scanner_config", "fail", str(e))
        return False

def _get_own_ip():
    try:
        import re
        r = subprocess.run(["ip", "-4", "addr", "show", "wlan0"],
                           capture_output=True, text=True)
        m = re.search(r"inet (\d+\.\d+\.\d+\.\d+)", r.stdout)
        return m.group(1) if m else ""
    except:
        return ""

_STATUS_LEVEL = {
    "pass":   logging.DEBUG,
    "skip":   logging.DEBUG,
    "healed": logging.INFO,
    "warn":   logging.WARNING,
    "fail":   logging.ERROR,
}

def _log_event(check, status, detail="", heal="", tenant_id="", pi_mac=""):
    entry = {
        "timestamp": _ts(), "check": check, "status": status,
        "tenant_id": tenant_id, "pi_mac": pi_mac,
    }
    if detail: entry["detail"] = detail
    if heal:   entry["heal"]   = heal
    icon = "✓" if status in ("pass","healed") else "⚠" if status == "warn" else "✗"
    print(f"[SAGE] {icon} {check}: {status}" + (f" — {detail}" if detail else ""), flush=True)
    # Route to cypher → blex.log
    level = _STATUS_LEVEL.get(status, logging.INFO)
    _log.log(level, check, extra={"status": status, "detail": detail, "heal": heal,
                                   "tenant_id": tenant_id, "pi_mac": pi_mac})
    # Append to daily JSONL (for daily_report counters)
    day = datetime.now(timezone.utc).strftime("%Y%m%d")
    path = os.path.join(LOG_DIR, f"sage_{day}.jsonl")
    try:
        with open(path, "a") as f:
            f.write(json.dumps(entry) + "\n")
    except:
        pass
    return entry

# ── Individual Heal Functions ─────────────────────────────────────────────────

def heal_service(service_name: str, tenant_id="", pi_mac="") -> bool:
    """Restart a crashed blex-* service — only if it should be running in current mode."""
    if not _should_be_active(service_name):
        _log_event(f"heal_service:{service_name}", "skip",
                   "inactive by design (mode/state)", tenant_id=tenant_id, pi_mac=pi_mac)
        return True  # correctly inactive — not a failure

    if _svc_active(service_name):
        _log_event(f"heal_service:{service_name}", "pass", "active", tenant_id=tenant_id, pi_mac=pi_mac)
        return True

    healed = _svc_restart(service_name)
    time.sleep(3)
    now_active = _svc_active(service_name)
    _log_event(f"heal_service:{service_name}", "healed" if now_active else "fail",
               heal=f"systemctl restart {service_name}", tenant_id=tenant_id, pi_mac=pi_mac)
    return now_active


def heal_broker(master_ip: str, tenant_id="", pi_mac="") -> bool:
    """Fix local MQTT broker (port 1883). Heals mosquitto if this Pi is master."""
    if _tcp(master_ip, 1883):
        _log_event("heal_broker", "pass", f"{master_ip}:1883 reachable",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True

    my_ip = _get_own_ip()
    if my_ip != master_ip:
        _log_event("heal_broker", "fail",
                   f"Master {master_ip} is remote — cannot heal remotely",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return False

    # This Pi is the master — try to restart mosquitto
    if not _svc_active("mosquitto"):
        _svc_start("mosquitto")
        time.sleep(3)

    if _tcp(master_ip, 1883):
        _log_event("heal_broker", "healed", heal="systemctl start mosquitto",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True

    # Try full restart
    _svc_restart("mosquitto")
    time.sleep(3)
    ok = _tcp(master_ip, 1883)
    _log_event("heal_broker", "healed" if ok else "fail",
               heal="systemctl restart mosquitto", tenant_id=tenant_id, pi_mac=pi_mac)
    return ok


def heal_master_ip(tenant_id: str, current_ip: str, pi_mac="") -> str:
    """Fetch fresh master IP from DGX. Returns new IP or empty string."""
    try:
        headers = _api_headers(tenant_id)
        resp = requests.get(f"{API_BASE}/api/runtime/master", headers=headers, timeout=8)
        if resp.status_code == 200:
            new_ip = resp.json().get("master_ip", "")
            if new_ip and new_ip != current_ip:
                _write_scanner_config(new_ip, 1883)
                _log_event("heal_master_ip", "healed",
                           f"{current_ip} → {new_ip}", tenant_id=tenant_id, pi_mac=pi_mac)
                return new_ip
            elif new_ip:
                _log_event("heal_master_ip", "pass",
                           f"IP unchanged: {current_ip}", tenant_id=tenant_id, pi_mac=pi_mac)
                return current_ip
    except Exception as e:
        _log_event("heal_master_ip", "fail", str(e)[:80], tenant_id=tenant_id, pi_mac=pi_mac)
    return ""


def heal_redis(tenant_id="", pi_mac="") -> bool:
    """Restart Redis if unreachable."""
    if _tcp("127.0.0.1", 6379, timeout=2):
        _log_event("heal_redis", "pass", "127.0.0.1:6379 reachable", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    _svc_restart("redis-server")
    time.sleep(3)
    ok = _tcp("127.0.0.1", 6379, timeout=2)
    _log_event("heal_redis", "healed" if ok else "fail",
               heal="systemctl restart redis-server", tenant_id=tenant_id, pi_mac=pi_mac)
    return ok


def heal_mqtt_auth(tenant_id="", pi_mac="") -> bool:
    """Fix MQTT credentials mismatch between config.py and mode.json."""
    mode = _read_mode()
    cfg  = _read_scanner_config()
    if not mode or not cfg:
        _log_event("heal_mqtt_auth", "fail", "Cannot read configs",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return False

    is_cloud   = mode.get("mode") == "cloud"
    expected_u = os.getenv("MQTT_USERNAME", "") if is_cloud else ""
    expected_p = os.getenv("MQTT_PASSWORD", "") if is_cloud else ""
    expected_tls = is_cloud

    actual_u = cfg.get("MQTT_USERNAME", "").strip('"')
    actual_p = cfg.get("MQTT_PASSWORD", "").strip('"')
    actual_tls = cfg.get("MQTT_USE_TLS", "false").lower() == "true"

    if actual_u == expected_u and actual_p == expected_p and actual_tls == expected_tls:
        _log_event("heal_mqtt_auth", "pass", "Credentials OK",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True

    broker = mode.get("mqtt_host", CLOUD_HOST)
    port   = int(mode.get("mqtt_port", CLOUD_PORT))
    ok = _write_scanner_config(broker, port, expected_tls, expected_u, expected_p)
    _log_event("heal_mqtt_auth", "healed" if ok else "fail",
               f"Fixed credentials for {'cloud' if is_cloud else 'local'} mode",
               heal="update config.py credentials", tenant_id=tenant_id, pi_mac=pi_mac)
    return ok


def heal_mqtt_port(master_ip: str, tenant_id="", pi_mac="") -> bool:
    """Check if port 1883 is blocked by firewall rules and attempt to unblock."""
    # First try broker heal (mosquitto restart)
    if heal_broker(master_ip, tenant_id, pi_mac):
        return True

    # Check iptables for DROP rules on 1883
    rc, out, _ = _run(["sudo", "iptables", "-L", "OUTPUT", "-n", "--line-numbers"])
    blocked = "1883" in out and "DROP" in out
    if blocked:
        # Remove DROP rule for port 1883
        _run(["sudo", "iptables", "-D", "OUTPUT", "-p", "tcp", "--dport", "1883", "-j", "DROP"])
        time.sleep(1)
        ok = _tcp(master_ip, 1883)
        _log_event("heal_mqtt_port", "healed" if ok else "fail",
                   "Removed iptables DROP rule for port 1883",
                   heal="iptables -D OUTPUT", tenant_id=tenant_id, pi_mac=pi_mac)
        return ok

    _log_event("heal_mqtt_port", "fail", "Port blocked, no known fix",
               tenant_id=tenant_id, pi_mac=pi_mac)
    return False


def heal_scanner_process(tenant_id="", pi_mac="") -> bool:
    """Restart blex-scanner if scanner.py is not running — skip if unprovisioned."""
    if os.path.exists("/run/blex/unprovisioned"):
        _log_event("heal_scanner_process", "skip", "unprovisioned — scanner should not run",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    if _proc_running("scanner.py"):
        _log_event("heal_scanner_process", "pass", "scanner.py running",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    return heal_service("blex-scanner", tenant_id, pi_mac)


def heal_master_process(tenant_id="", pi_mac="") -> bool:
    """Restart blex-master if master.py is not running (local mode only)."""
    mode = _read_mode()
    if mode.get("mode") != "local":
        _log_event("heal_master_process", "skip", "cloud mode",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    if mode.get("role", "master") != "master":
        _log_event("heal_master_process", "skip", "scanner role",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    if _proc_running("master.py"):
        _log_event("heal_master_process", "pass", "master.py running",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    return heal_service("blex-master", tenant_id, pi_mac)


def heal_fifo_process(tenant_id="", pi_mac="") -> bool:
    """Restart fifo_consumer if not running (local mode only)."""
    mode = _read_mode()
    if mode.get("mode") != "local":
        return True
    if mode.get("role", "master") != "master":
        return True
    if _proc_running("fifo_consumer.py"):
        _log_event("heal_fifo_process", "pass", "fifo_consumer.py running",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    # Only run master_stack.sh if master.py is also not running — avoid duplicate instances
    if _proc_running("master.py"):
        _log_event("heal_fifo_process", "skip", "master.py running, fifo will recover",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    stack = os.path.join(PI_DIR, "master", "master_stack.sh")
    if os.path.exists(stack):
        subprocess.Popen(["bash", stack], cwd=os.path.join(PI_DIR, "master"))
        time.sleep(3)
        ok = _proc_running("fifo_consumer.py")
        _log_event("heal_fifo_process", "healed" if ok else "fail",
                   heal="master_stack.sh", tenant_id=tenant_id, pi_mac=pi_mac)
        return ok
    return False


def heal_network(tenant_id="", pi_mac="") -> bool:
    """Check internet connectivity. Cannot self-heal but logs the issue."""
    ok = _tcp("8.8.8.8", 53, timeout=3)
    _log_event("heal_network", "pass" if ok else "fail",
               "internet reachable" if ok else "no internet — manual fix needed",
               tenant_id=tenant_id, pi_mac=pi_mac)
    return ok


def heal_mode_json(tenant_id="", pi_mac="") -> bool:
    """Validate mode.json has all required fields."""
    cfg = _read_mode()
    required = {"mode", "tenant_id", "mqtt_host"}
    missing  = required - set(cfg.keys())
    if not missing:
        _log_event("heal_mode_json", "pass", f"mode={cfg.get('mode')}",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    _log_event("heal_mode_json", "fail", f"missing fields: {missing}",
               tenant_id=tenant_id, pi_mac=pi_mac)
    return False


def heal_api(tenant_id="", pi_mac="") -> bool:
    """Check DGX API is reachable and healthy."""
    try:
        resp = requests.get(f"{API_BASE}/api/system/health", timeout=8)
        if resp.status_code == 200:
            data = resp.json()
            all_ok = data.get("status") == "ok"
            _log_event("heal_api", "pass" if all_ok else "warn",
                       f"v{data.get('version','?')} db={data.get('checks',{}).get('db',{}).get('status')} redis={data.get('checks',{}).get('redis',{}).get('status')}",
                       tenant_id=tenant_id, pi_mac=pi_mac)
            return all_ok
    except Exception as e:
        _log_event("heal_api", "fail", str(e)[:80], tenant_id=tenant_id, pi_mac=pi_mac)
    return False


# ── Full Sweep (watchdog / EOD) ───────────────────────────────────────────────

def full_sweep(master_ip: str, tenant_id: str, pi_mac="", source="watchdog") -> dict:
    """
    Run all targeted heals. Only fixes what's broken.
    Used by: watchdog every 5 min, daily_report at midnight.
    """
    print(f"[SAGE] ━━ full_sweep source={source} tenant={tenant_id} ━━", flush=True)
    t0      = time.time()
    mode    = _read_mode()
    is_local = mode.get("mode") == "local"
    is_master_role = mode.get("role", "master") == "master"
    results = {}

    results["api"]             = heal_api(tenant_id, pi_mac)
    results["network"]         = heal_network(tenant_id, pi_mac)
    results["mode_json"]       = heal_mode_json(tenant_id, pi_mac)
    results["mqtt_auth"]       = heal_mqtt_auth(tenant_id, pi_mac)

    if is_local and is_master_role:
        new_ip = heal_master_ip(tenant_id, master_ip, pi_mac)
        if new_ip:
            master_ip = new_ip
        results["master_ip"]   = bool(new_ip or master_ip)
        results["broker"]      = heal_broker(master_ip, tenant_id, pi_mac)
        results["mqtt_port"]   = results["broker"] or heal_mqtt_port(master_ip, tenant_id, pi_mac)
        results["redis"]       = heal_redis(tenant_id, pi_mac)
        results["master_proc"] = heal_master_process(tenant_id, pi_mac)
        results["fifo_proc"]   = heal_fifo_process(tenant_id, pi_mac)

    results["scanner_svc"]     = heal_service("blex-scanner", tenant_id, pi_mac)
    results["provisioner_svc"] = heal_service("blex-provisioner", tenant_id, pi_mac)
    results["discovery_svc"]   = heal_service("blex-discovery", tenant_id, pi_mac)
    results["scanner_proc"]    = heal_scanner_process(tenant_id, pi_mac)

    failed  = [k for k, v in results.items() if not v]
    elapsed = round(time.time() - t0, 1)
    all_ok  = not failed

    summary = {
        "timestamp": _ts(), "source": source, "tenant_id": tenant_id,
        "pi_mac": pi_mac, "mode": mode.get("mode"), "role": mode.get("role", "master"), "master_ip": master_ip,
        "checks_total": len(results), "checks_failed": len(failed),
        "failed": failed, "duration_sec": elapsed,
        "status": "healthy" if all_ok else f"degraded ({len(failed)} issues)",
    }

    day  = datetime.now(timezone.utc).strftime("%Y%m%d")
    path = os.path.join(LOG_DIR, f"sage_sweep_{day}.jsonl")
    try:
        with open(path, "a") as f:
            f.write(json.dumps(summary) + "\n")
    except:
        pass

    _hms = datetime.now(timezone.utc).strftime("%H:%M:%S")
    if all_ok:
        print(f"[SAGE {_hms}] ✓ All checks passed ({elapsed}s)", flush=True)
    else:
        print(f"[SAGE {_hms}] ⚠ {len(failed)} issue(s): {failed} ({elapsed}s)", flush=True)

    return summary


def daily_report(master_ip: str, tenant_id: str, pi_mac="") -> dict:
    """Full sweep at midnight — also reads today's event log for a summary."""
    summary = full_sweep(master_ip, tenant_id, pi_mac, source="daily")

    # Count today's heals from event log
    day  = datetime.now(timezone.utc).strftime("%Y%m%d")
    log  = os.path.join(LOG_DIR, f"sage_{day}.jsonl")
    heals, fails = 0, 0
    try:
        with open(log) as f:
            for line in f:
                e = json.loads(line)
                if e.get("status") == "healed": heals += 1
                if e.get("status") == "fail":   fails += 1
    except:
        pass

    summary["daily_heals"]  = heals
    summary["daily_fails"]  = fails
    summary["daily_status"] = "SYSTEM HEALTHY" if summary["checks_failed"] == 0 else f"DEGRADED — {summary['checks_failed']} unresolved"

    print(f"[SAGE] Daily report: {summary['daily_status']} | heals={heals} fails={fails}", flush=True)
    return summary
