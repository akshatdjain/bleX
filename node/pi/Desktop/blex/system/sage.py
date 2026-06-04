"""
SAGE — Self-healing Automated Guardian Engine
BleX Pi — Targeted heal functions + full sweep
"""

import os
import json
import time
import socket
import subprocess
import requests
from datetime import datetime, timezone

BASE_DIR   = os.path.dirname(os.path.abspath(__file__))
PI_DIR     = os.path.dirname(BASE_DIR)
LOG_DIR    = os.path.join(PI_DIR, "logs")
MQTT_CFG   = "/etc/blex/mode.json"
SCANNER_CFG = os.path.join(PI_DIR, "scanner", "config.py")
os.makedirs(LOG_DIR, exist_ok=True)

import sys
import logging as _logging
if PI_DIR not in sys.path:
    sys.path.insert(0, PI_DIR)
from cypher import get_logger
_log = get_logger("sage")

API_BASE   = "https://sigmatic-asc.tech/asset"
CLOUD_HOST = "sigmatic-asc.tech"
CLOUD_PORT = 8883

_STATUS_LEVEL = {
    "pass": _logging.DEBUG, "skip": _logging.DEBUG,
    "healed": _logging.INFO, "warn": _logging.WARNING, "fail": _logging.ERROR,
}

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
    if service_name == "blex-master.service":
        return os.path.exists("/run/blex/mode-local")
    if service_name == "blex-scanner.service":
        return not os.path.exists("/run/blex/unprovisioned")
    return True

def _proc_running(name):
    rc, out, _ = _run(["pgrep", "-f", name])
    return rc == 0 and bool(out)

def _read_mode():
    for p in [MQTT_CFG, os.path.expanduser("~/mqtt_config.json")]:
        try:
            with open(p) as f:
                return json.load(f)
        except:
            pass
    return {}

def _read_scanner_config():
    vals = {}
    try:
        with open(SCANNER_CFG) as f:
            for line in f:
                line = line.strip()
                for key in ["MQTT_BROKER", "MQTT_PORT", "MQTT_USE_TLS", "MQTT_USERNAME", "MQTT_PASSWORD"]:
                    if line.startswith(key):
                        parts = line.split("=", 1)
                        if len(parts) == 2:
                            vals[key] = parts[1].strip().strip('"').strip("'")
    except:
        pass
    return vals

def _write_scanner_config(broker, port, use_tls=False, username="", password=""):
    try:
        with open(SCANNER_CFG) as f:
            lines = f.readlines()
        updates = {
            "MQTT_BROKER":   f'MQTT_BROKER = "{broker}"',
            "MQTT_PORT":     f"MQTT_PORT   = {port}",
            "MQTT_USE_TLS":  f"MQTT_USE_TLS = {use_tls}",
            "MQTT_USERNAME": f'MQTT_USERNAME = "{username}"',
            "MQTT_PASSWORD": f'MQTT_PASSWORD = "{password}"',
        }
        new, written = [], {k: False for k in updates}
        for line in lines:
            matched = False
            for k, v in updates.items():
                if line.strip().startswith(k):
                    new.append(v + "\n"); written[k] = True; matched = True; break
            if not matched:
                new.append(line)
        for k, v in updates.items():
            if not written[k]:
                new.append(v + "\n")
        with open(SCANNER_CFG, "w") as f:
            f.writelines(new)
        return True
    except Exception as e:
        _log_event("write_scanner_config", "fail", str(e))
        return False

def _get_own_ip():
    try:
        import re
        r = subprocess.run(["ip", "-4", "addr", "show", "wlan0"], capture_output=True, text=True)
        m = re.search(r"inet (\d+\.\d+\.\d+\.\d+)", r.stdout)
        return m.group(1) if m else ""
    except:
        return ""

def _log_event(check, status, detail="", heal="", tenant_id="", pi_mac=""):
    entry = {
        "timestamp": _ts(), "check": check, "status": status,
        "tenant_id": tenant_id, "pi_mac": pi_mac,
    }
    if detail: entry["detail"] = detail
    if heal:   entry["heal"]   = heal

    level = _STATUS_LEVEL.get(status, _logging.INFO)
    _log.log(level, f"{check}: {status}", extra={
        "check": check, "status": status,
        "detail": detail or None, "heal": heal or None,
    })

    # Keep sage JSONL for daily_report heal/fail counter
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
    if not _should_be_active(service_name):
        _log_event(f"heal_service:{service_name}", "skip", "inactive by design (mode/state)", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    if _svc_active(service_name):
        _log_event(f"heal_service:{service_name}", "pass", "active", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    _svc_restart(service_name)
    time.sleep(3)
    now_active = _svc_active(service_name)
    _log_event(f"heal_service:{service_name}", "healed" if now_active else "fail",
               heal=f"systemctl restart {service_name}", tenant_id=tenant_id, pi_mac=pi_mac)
    return now_active

def heal_broker(master_ip: str, tenant_id="", pi_mac="") -> bool:
    if _tcp(master_ip, 1883):
        _log_event("heal_broker", "pass", f"{master_ip}:1883 reachable", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    my_ip = _get_own_ip()
    if my_ip != master_ip:
        _log_event("heal_broker", "fail", f"Master {master_ip} is remote — cannot heal remotely", tenant_id=tenant_id, pi_mac=pi_mac)
        return False
    if not _svc_active("mosquitto"):
        _svc_start("mosquitto")
        time.sleep(3)
    if _tcp(master_ip, 1883):
        _log_event("heal_broker", "healed", heal="systemctl start mosquitto", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    _svc_restart("mosquitto")
    time.sleep(3)
    ok = _tcp(master_ip, 1883)
    _log_event("heal_broker", "healed" if ok else "fail", heal="systemctl restart mosquitto", tenant_id=tenant_id, pi_mac=pi_mac)
    return ok

def heal_master_ip(tenant_id: str, current_ip: str, pi_mac="") -> str:
    try:
        headers = {"X-Tenant-ID": tenant_id} if tenant_id else {}
        resp = requests.get(f"{API_BASE}/api/runtime/master", headers=headers, timeout=8)
        if resp.status_code == 200:
            new_ip = resp.json().get("master_ip", "")
            if new_ip and new_ip != current_ip:
                _write_scanner_config(new_ip, 1883)
                _log_event("heal_master_ip", "healed", f"{current_ip} → {new_ip}", tenant_id=tenant_id, pi_mac=pi_mac)
                return new_ip
            elif new_ip:
                _log_event("heal_master_ip", "pass", f"IP unchanged: {current_ip}", tenant_id=tenant_id, pi_mac=pi_mac)
                return current_ip
    except Exception as e:
        _log_event("heal_master_ip", "fail", str(e)[:80], tenant_id=tenant_id, pi_mac=pi_mac)
    return ""

def heal_redis(tenant_id="", pi_mac="") -> bool:
    if _tcp("127.0.0.1", 6379, timeout=2):
        _log_event("heal_redis", "pass", "127.0.0.1:6379 reachable", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    _svc_restart("redis-server")
    time.sleep(3)
    ok = _tcp("127.0.0.1", 6379, timeout=2)
    _log_event("heal_redis", "healed" if ok else "fail", heal="systemctl restart redis-server", tenant_id=tenant_id, pi_mac=pi_mac)
    return ok

def heal_mqtt_auth(tenant_id="", pi_mac="") -> bool:
    mode = _read_mode()
    cfg  = _read_scanner_config()
    if not mode or not cfg:
        _log_event("heal_mqtt_auth", "fail", "Cannot read configs", tenant_id=tenant_id, pi_mac=pi_mac)
        return False
    is_cloud   = mode.get("mode") == "cloud"
    expected_u = "tab" if is_cloud else ""
    expected_p = "1234" if is_cloud else ""
    expected_tls = is_cloud
    actual_u   = cfg.get("MQTT_USERNAME", "").strip('"')
    actual_p   = cfg.get("MQTT_PASSWORD", "").strip('"')
    actual_tls = cfg.get("MQTT_USE_TLS", "False") == "True"
    if actual_u == expected_u and actual_p == expected_p and actual_tls == expected_tls:
        _log_event("heal_mqtt_auth", "pass", "Credentials OK", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    broker = mode.get("mqtt_host", CLOUD_HOST)
    port   = int(mode.get("mqtt_port", CLOUD_PORT))
    ok = _write_scanner_config(broker, port, expected_tls, expected_u, expected_p)
    _log_event("heal_mqtt_auth", "healed" if ok else "fail",
               f"Fixed credentials for {'cloud' if is_cloud else 'local'} mode",
               heal="update config.py credentials", tenant_id=tenant_id, pi_mac=pi_mac)
    return ok

def heal_mqtt_port(master_ip: str, tenant_id="", pi_mac="") -> bool:
    if heal_broker(master_ip, tenant_id, pi_mac):
        return True
    rc, out, _ = _run(["sudo", "iptables", "-L", "OUTPUT", "-n", "--line-numbers"])
    blocked = "1883" in out and "DROP" in out
    if blocked:
        _run(["sudo", "iptables", "-D", "OUTPUT", "-p", "tcp", "--dport", "1883", "-j", "DROP"])
        time.sleep(1)
        ok = _tcp(master_ip, 1883)
        _log_event("heal_mqtt_port", "healed" if ok else "fail",
                   "Removed iptables DROP rule for port 1883", heal="iptables -D OUTPUT",
                   tenant_id=tenant_id, pi_mac=pi_mac)
        return ok
    _log_event("heal_mqtt_port", "fail", "Port blocked, no known fix", tenant_id=tenant_id, pi_mac=pi_mac)
    return False

def heal_scanner_process(tenant_id="", pi_mac="") -> bool:
    if os.path.exists("/run/blex/unprovisioned"):
        _log_event("heal_scanner_process", "skip", "unprovisioned — scanner should not run", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    if _proc_running("scanner.py"):
        _log_event("heal_scanner_process", "pass", "scanner.py running", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    return heal_service("blex-scanner", tenant_id, pi_mac)

def heal_master_process(tenant_id="", pi_mac="") -> bool:
    mode = _read_mode()
    if mode.get("mode") != "local":
        _log_event("heal_master_process", "skip", "cloud mode", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    if _proc_running("master.py"):
        _log_event("heal_master_process", "pass", "master.py running", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    return heal_service("blex-master", tenant_id, pi_mac)

def heal_fifo_process(tenant_id="", pi_mac="") -> bool:
    mode = _read_mode()
    if mode.get("mode") != "local":
        return True
    if _proc_running("fifo_consumer.py"):
        _log_event("heal_fifo_process", "pass", "fifo_consumer.py running", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    stack = os.path.join(PI_DIR, "master", "master_stack.sh")
    if os.path.exists(stack):
        subprocess.Popen(["bash", stack], cwd=os.path.join(PI_DIR, "master"))
        time.sleep(3)
        ok = _proc_running("fifo_consumer.py")
        _log_event("heal_fifo_process", "healed" if ok else "fail", heal="master_stack.sh", tenant_id=tenant_id, pi_mac=pi_mac)
        return ok
    return False

def heal_network(tenant_id="", pi_mac="") -> bool:
    ok = _tcp("8.8.8.8", 53, timeout=3)
    _log_event("heal_network", "pass" if ok else "fail",
               "internet reachable" if ok else "no internet — manual fix needed",
               tenant_id=tenant_id, pi_mac=pi_mac)
    return ok

def heal_mode_json(tenant_id="", pi_mac="") -> bool:
    cfg = _read_mode()
    required = {"mode", "tenant_id", "mqtt_host"}
    missing  = required - set(cfg.keys())
    if not missing:
        _log_event("heal_mode_json", "pass", f"mode={cfg.get('mode')}", tenant_id=tenant_id, pi_mac=pi_mac)
        return True
    _log_event("heal_mode_json", "fail", f"missing fields: {missing}", tenant_id=tenant_id, pi_mac=pi_mac)
    return False

def heal_api(tenant_id="", pi_mac="") -> bool:
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

# ── Full Sweep ────────────────────────────────────────────────────────────────

def full_sweep(master_ip: str, tenant_id: str, pi_mac="", source="watchdog") -> dict:
    _log.info("full sweep started", extra={"source": source, "tenant_id": tenant_id})
    t0       = time.time()
    mode     = _read_mode()
    is_local = mode.get("mode") == "local"
    results  = {}

    results["api"]             = heal_api(tenant_id, pi_mac)
    results["network"]         = heal_network(tenant_id, pi_mac)
    results["mode_json"]       = heal_mode_json(tenant_id, pi_mac)
    results["mqtt_auth"]       = heal_mqtt_auth(tenant_id, pi_mac)

    if is_local:
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
        "pi_mac": pi_mac, "mode": mode.get("mode"), "master_ip": master_ip,
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

    level = _logging.INFO if all_ok else _logging.WARNING
    _log.log(level, "sweep complete", extra={
        "source": source, "status": summary["status"],
        "checks_total": len(results), "checks_failed": len(failed),
        "failed": failed, "duration_sec": elapsed,
    })
    return summary

def daily_report(master_ip: str, tenant_id: str, pi_mac="") -> dict:
    summary = full_sweep(master_ip, tenant_id, pi_mac, source="daily")
    day  = datetime.now(timezone.utc).strftime("%Y%m%d")
    logf = os.path.join(LOG_DIR, f"sage_{day}.jsonl")
    heals, fails = 0, 0
    try:
        with open(logf) as f:
            for line in f:
                e = json.loads(line)
                if e.get("status") == "healed": heals += 1
                if e.get("status") == "fail":   fails += 1
    except:
        pass
    summary["daily_heals"]  = heals
    summary["daily_fails"]  = fails
    summary["daily_status"] = "SYSTEM HEALTHY" if summary["checks_failed"] == 0 else f"DEGRADED — {summary['checks_failed']} unresolved"
    _log.info("daily report", extra={
        "daily_status": summary["daily_status"], "heals": heals, "fails": fails,
    })
    return summary
