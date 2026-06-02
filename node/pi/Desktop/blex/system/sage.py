"""
SAGE — Self-healing Automated Guardian Engine
BleX Pi System Health Checker & Self-Healer

Checks (in order):
  0.  API reachability (DGX backend + DB + Redis)
  1.  Network / internet
  2.  Master IP from DGX
  3.  Broker TCP reachability
  4.  Mosquitto service (if this Pi is master)
  5.  MQTT connect test
  6.  Scanner-zone map (is this Pi registered?)
  7.  mode.json validity
  8.  blex-mode.service
  9.  blex-scanner.service
  10. blex-master.service (local mode only)
  11. blex-provisioner.service
  12. blex-discovery.service
  13. scanner.py process
  14. master.py process (local mode only)
  15. fifo_consumer.py process (local mode only)
  16. master_register last run log
  17. Redis on Pi (local mode only)
"""

import os
import json
import time
import socket
import subprocess
import requests
from datetime import datetime, timezone

BASE_DIR   = os.path.dirname(os.path.abspath(__file__))
PI_DIR     = os.path.dirname(BASE_DIR)          # /home/blex/Desktop/blex
LOG_DIR    = os.path.join(PI_DIR, "logs")
MQTT_CFG   = "/etc/blex/mode.json"
os.makedirs(LOG_DIR, exist_ok=True)

API_BASE_URL = "https://sigmatic-asc.tech/asset"
CLOUD_HOST   = "sigmatic-asc.tech"
CLOUD_PORT   = 8883

BLEX_SERVICES = [
    "blex-mode.service",
    "blex-scanner.service",
    "blex-master.service",
    "blex-provisioner.service",
    "blex-discovery.service",
]
LOCAL_ONLY_SERVICES  = {"blex-master.service"}
LOCAL_ONLY_PROCESSES = ["master.py", "fifo_consumer.py"]


def _ts() -> str:
    return datetime.now(timezone.utc).isoformat()


def _tcp_probe(host: str, port: int, timeout: int = 5) -> bool:
    try:
        s = socket.create_connection((host, port), timeout=timeout)
        s.close()
        return True
    except Exception:
        return False


def _run(cmd: list, timeout: int = 10) -> tuple:
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return r.returncode, r.stdout.strip(), r.stderr.strip()
    except Exception as e:
        return 1, "", str(e)


def _systemctl_active(service: str) -> bool:
    rc, out, _ = _run(["systemctl", "is-active", service])
    return out == "active"


def _systemctl_start(service: str) -> bool:
    rc, _, _ = _run(["sudo", "systemctl", "start", service])
    return rc == 0


def _systemctl_restart(service: str) -> bool:
    rc, _, _ = _run(["sudo", "systemctl", "restart", service])
    return rc == 0


def _is_process_running(script_name: str) -> bool:
    rc, out, _ = _run(["pgrep", "-f", script_name])
    return rc == 0 and bool(out)


def _get_own_ip() -> str:
    try:
        import re
        r = subprocess.run(["ip", "-4", "addr", "show", "wlan0"],
                           capture_output=True, text=True)
        m = re.search(r"inet (\d+\.\d+\.\d+\.\d+)", r.stdout)
        return m.group(1) if m else ""
    except Exception:
        return ""


def _read_mode_json() -> dict:
    for path in [MQTT_CFG, os.path.expanduser("~/mqtt_config.json")]:
        try:
            with open(path) as f:
                return json.load(f)
        except Exception:
            continue
    return {}


def _test_mqtt_connect(host: str, port: int, timeout: int = 5) -> bool:
    try:
        import paho.mqtt.client as mqtt
        connected = [False]
        def on_connect(c, u, f, rc, p=None):
            connected[0] = (rc == 0)
        cl = mqtt.Client(client_id="sage-probe", protocol=mqtt.MQTTv311,
                         callback_api_version=mqtt.CallbackAPIVersion.VERSION2)
        cl.on_connect = on_connect
        cl.connect(host, port, keepalive=10)
        cl.loop_start()
        time.sleep(timeout)
        cl.loop_stop()
        try: cl.disconnect()
        except: pass
        return connected[0]
    except Exception:
        return False


def _update_config_broker(host: str, port: int):
    config_file = os.path.join(PI_DIR, "scanner", "config.py")
    try:
        with open(config_file) as f:
            lines = f.readlines()
        with open(config_file, "w") as f:
            for line in lines:
                if line.strip().startswith("MQTT_BROKER"):
                    f.write(f'MQTT_BROKER = "{host}"\n')
                elif line.strip().startswith("MQTT_PORT"):
                    f.write(f'MQTT_PORT   = {port}\n')
                else:
                    f.write(line)
        print(f"[SAGE] config.py updated → {host}:{port}", flush=True)
    except Exception as e:
        print(f"[SAGE] Could not update config.py: {e}", flush=True)


class SageResult:
    def __init__(self):
        self.checks  = []
        self.healed  = False
        self.outcome = "unknown"
        self._id     = 0

    def add(self, name: str, status: str, detail: str = "", heal: str = ""):
        self._id += 1
        entry = {"id": self._id, "name": name, "status": status}
        if detail: entry["detail"] = detail
        if heal:   entry["heal"]   = heal
        self.checks.append(entry)
        icon = ("✓" if status in ("pass", "healed") else
                "⚠" if status in ("warn", "skip") else "✗")
        print(f"[SAGE] {icon} [{self._id:02d}] {name}: {status}"
              + (f" — {detail}" if detail else ""), flush=True)

    def to_dict(self, tenant_id, pi_mac, trigger, duration) -> dict:
        return {
            "timestamp":    _ts(),
            "tenant_id":    tenant_id,
            "pi_mac":       pi_mac,
            "trigger":      trigger,
            "checks":       self.checks,
            "outcome":      self.outcome,
            "duration_sec": round(duration, 2),
        }


def run_diagnostics(master_ip: str, tenant_id: str,
                    pi_mac: str = "", trigger: str = "mqtt_failure") -> SageResult:
    print("[SAGE] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", flush=True)
    print(f"[SAGE] Starting diagnostics | trigger={trigger}", flush=True)
    print("[SAGE] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", flush=True)

    result      = SageResult()
    start       = time.time()
    mode_cfg    = _read_mode_json()
    mode        = mode_cfg.get("mode", "cloud")
    is_local    = (mode == "local")
    my_ip       = _get_own_ip()
    i_am_master = is_local and bool(my_ip) and my_ip == master_ip

    # ── 0. API reachability ──────────────────────────────────────────────────
    api_ok = False
    try:
        resp   = requests.get(f"{API_BASE_URL}/api/system/health", timeout=8)
        api_ok = resp.status_code == 200
        if api_ok:
            data     = resp.json()
            db_ok    = data.get("checks", {}).get("db", {}).get("status") == "ok"
            redis_ok = data.get("checks", {}).get("redis", {}).get("status") == "ok"
            result.add("api_reachable", "pass", f"v{data.get('version','?')}")
            result.add("api_db",    "pass" if db_ok    else "warn")
            result.add("api_redis", "pass" if redis_ok else "warn")
        else:
            result.add("api_reachable", "fail", f"HTTP {resp.status_code}")
    except Exception as e:
        result.add("api_reachable", "fail", str(e)[:80])

    if not api_ok:
        result.outcome = "cloud_fallback"
        _save_log(result, tenant_id, pi_mac, trigger, time.time() - start)
        return result

    # ── 1. Network ───────────────────────────────────────────────────────────
    net_ok = _tcp_probe("8.8.8.8", 53, timeout=3)
    result.add("network", "pass" if net_ok else "fail",
               "internet ok" if net_ok else "no internet")
    if not net_ok:
        result.outcome = "cloud_fallback"
        _save_log(result, tenant_id, pi_mac, trigger, time.time() - start)
        return result

    # ── 2. Master IP from DGX ────────────────────────────────────────────────
    fresh_ip = ""
    try:
        headers  = {"X-Tenant-ID": tenant_id} if tenant_id else {}
        resp     = requests.get(f"{API_BASE_URL}/api/runtime/master",
                                headers=headers, timeout=8)
        if resp.status_code == 200:
            fresh_ip = resp.json().get("master_ip", "")
    except Exception as e:
        result.add("master_ip_fetch", "fail", str(e)[:80])

    if fresh_ip:
        changed     = fresh_ip != master_ip
        if changed:
            master_ip   = fresh_ip
            i_am_master = is_local and bool(my_ip) and my_ip == master_ip
        result.add("master_ip_fetch", "pass",
                   f"ip={master_ip}" + (" (updated)" if changed else ""))
    else:
        result.add("master_ip_fetch", "warn", "no master registered for tenant")

    # ── 3. Broker TCP probe ──────────────────────────────────────────────────
    if is_local:
        broker_ok = _tcp_probe(master_ip, 1883)
        result.add("broker_tcp", "pass" if broker_ok else "fail", f"{master_ip}:1883")
    else:
        broker_ok = _tcp_probe(CLOUD_HOST, CLOUD_PORT)
        result.add("broker_tcp", "pass" if broker_ok else "fail", f"{CLOUD_HOST}:{CLOUD_PORT}")

    # ── 4. Mosquitto (if this Pi is the master) ──────────────────────────────
    if i_am_master:
        mosq_active = _systemctl_active("mosquitto")
        if mosq_active:
            result.add("mosquitto", "pass", "active")
        else:
            healed = _systemctl_start("mosquitto")
            time.sleep(3)
            broker_ok = _tcp_probe(master_ip, 1883)
            result.add("mosquitto",
                       "healed" if (healed and broker_ok) else "fail",
                       heal="systemctl start mosquitto")
    elif is_local:
        result.add("mosquitto", "skip", f"master is remote ({master_ip})")

    # ── 5. MQTT connect test ─────────────────────────────────────────────────
    if broker_ok:
        host    = master_ip if is_local else CLOUD_HOST
        port    = 1883 if is_local else CLOUD_PORT
        mqtt_ok = _test_mqtt_connect(host, port)
        result.add("mqtt_connect", "pass" if mqtt_ok else "fail", f"{host}:{port}")
    else:
        result.add("mqtt_connect", "skip", "broker unreachable")
        mqtt_ok = False

    # ── 6. Scanner-zone map ──────────────────────────────────────────────────
    try:
        headers  = {"X-Tenant-ID": tenant_id} if tenant_id else {}
        resp     = requests.get(f"{API_BASE_URL}/api/runtime/scanner-zone-map",
                                headers=headers, timeout=8)
        if resp.status_code == 200:
            zone_map = resp.json().get("scanner_zone_map", {})
            pi_norm  = (pi_mac or "").upper().replace(":", "")
            in_map   = any(
                mac.upper().replace(":", "") == pi_norm
                for mac in zone_map.keys()
            ) if pi_norm else False
            result.add("scanner_zone_map",
                       "pass" if in_map else "warn",
                       f"{len(zone_map)} scanners, registered={in_map}")
    except Exception as e:
        result.add("scanner_zone_map", "warn", str(e)[:80])

    # ── 7. mode.json validity ────────────────────────────────────────────────
    cfg = _read_mode_json()
    if cfg:
        missing = {"mode", "tenant_id", "mqtt_host"} - set(cfg.keys())
        result.add("mode_json",
                   "pass" if not missing else "warn",
                   f"mode={cfg.get('mode')} tenant={cfg.get('tenant_id')}"
                   + (f" missing={missing}" if missing else ""))
    else:
        result.add("mode_json", "fail", "not found or unreadable")

    # ── 8–12. blex systemd services ─────────────────────────────────────────
    for svc in BLEX_SERVICES:
        if svc in LOCAL_ONLY_SERVICES and not is_local:
            result.add(f"service:{svc}", "skip", "cloud mode — not needed")
            continue
        active = _systemctl_active(svc)
        if active:
            result.add(f"service:{svc}", "pass", "active")
        else:
            if svc != "blex-master.service":
                restarted  = _systemctl_restart(svc)
                time.sleep(2)
                now_active = _systemctl_active(svc)
                result.add(f"service:{svc}",
                           "healed" if now_active else "fail",
                           heal=f"systemctl restart {svc}")
            else:
                result.add(f"service:{svc}", "fail", "inactive")

    # ── 13. scanner.py process ───────────────────────────────────────────────
    result.add("process:scanner.py",
               "pass" if _is_process_running("scanner.py") else "warn",
               "running" if _is_process_running("scanner.py") else "not running")

    # ── 14–16. Local-only processes ──────────────────────────────────────────
    if is_local:
        for proc in LOCAL_ONLY_PROCESSES:
            running = _is_process_running(proc)
            result.add(f"process:{proc}",
                       "pass" if running else "warn",
                       "running" if running else "not running")

        # master_register last run
        reg_log = os.path.join(PI_DIR, "master", "logs", "register.out")
        if os.path.exists(reg_log):
            _, last_line, _ = _run(["tail", "-1", reg_log])
            result.add("master_register_log", "pass", (last_line or "empty")[:120])
        else:
            result.add("master_register_log", "warn", "log not found")

        # Redis on Pi
        redis_ok = _tcp_probe("127.0.0.1", 6379, timeout=2)
        if redis_ok:
            result.add("redis_local", "pass", "127.0.0.1:6379")
        else:
            _systemctl_restart("redis-server")
            time.sleep(2)
            redis_ok = _tcp_probe("127.0.0.1", 6379, timeout=2)
            result.add("redis_local",
                       "healed" if redis_ok else "fail",
                       heal="systemctl restart redis-server")

    # ── Final verdict ─────────────────────────────────────────────────────────
    if mqtt_ok:
        result.healed  = True
        result.outcome = "healed_local" if is_local else "cloud_ok"
    elif broker_ok:
        result.healed  = True
        result.outcome = "broker_ok_mqtt_retry"
    else:
        result.healed  = False
        result.outcome = "cloud_fallback"

    if fresh_ip and fresh_ip != master_ip:
        _update_config_broker(fresh_ip, 1883)

    elapsed = time.time() - start
    _save_log(result, tenant_id, pi_mac, trigger, elapsed)
    print(f"[SAGE] outcome={result.outcome} | {len(result.checks)} checks | {round(elapsed,1)}s", flush=True)
    print("[SAGE] ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", flush=True)
    return result


def _save_log(result: SageResult, tenant_id: str, pi_mac: str,
              trigger: str, duration: float):
    report = result.to_dict(tenant_id, pi_mac, trigger, duration)
    ts     = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    path   = os.path.join(LOG_DIR, f"sage_{ts}.json")
    try:
        with open(path, "w") as f:
            json.dump(report, f, indent=2)
        print(f"[SAGE] Report → {path}", flush=True)
    except Exception as e:
        print(f"[SAGE] Could not save: {e}", flush=True)
