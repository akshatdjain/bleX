#!/usr/bin/env python3
"""
provisioner_service.py — Receives provisioning POST from Android app,
writes /etc/blex/blex.env (single source of truth), runs blex-flags.sh
to refresh /run/blex/* flags, then restarts affected services.

No more mode.json. blex.env is the only file written.
"""
import http.server
import json
import subprocess
import threading
import time
import os

PORT          = 8888
BLEX_ENV_DIR  = "/etc/blex"
BLEX_ENV_FILE = "/etc/blex/blex.env"
FLAGS_SCRIPT  = "/usr/local/bin/blex-flags.sh"

_provision_status = {"state": "idle", "ssid": None}


# Static defaults for fields not provided by the app.
_STATIC_DEFAULTS = {
    "REDIS_HOST":           "127.0.0.1",
    "REDIS_PORT":           "6379",
    "REDIS_PASSWORD":       "",
    "SCANNER_ZONE_API":     "https://sigmatic-asc.tech/asset/api/runtime/scanner-zone-map",
    "API_URL":              "https://sigmatic-asc.tech/asset/api/asset/movement",
    "HEALTH_API_BASE":      "https://sigmatic-asc.tech/asset/api/health",
    "PUBLISH_INTERVAL":     "2.0",
    "BEACON_TTL":           "5.0",
    "KALMAN_Q":             "0.008",
    "KALMAN_R":             "4.0",
    "HYSTERESIS_DBM":       "8",
    "SCANNER_TTL":          "8",
    "ZONE_CONFIRM_COUNT":   "3",
    "DWELL_TIME_SEC":       "8.0",
    "LOST_TIMEOUT":         "30.0",
    "HEALTH_PUSH_INTERVAL": "60",
    "SCANNER_HEALTH_TIMEOUT":   "90",
    "SCANNER_ZONE_REFRESH_SEC": "600",
    "CONSUMER_SLEEP_SEC":   "1",
    "ENABLE_DEBUG_LOGS":    "true",
}


def _build_env(config: dict) -> str:
    """Render the full blex.env content from a provisioning payload."""
    mode      = config.get("mode", "cloud")
    role      = config.get("role", "master")
    tenant_id = config.get("tenant_id", "default")
    api_token = config.get("api_token", "") or ""

    if mode == "local":
        mqtt_broker = "127.0.0.1"
        mqtt_port   = 1883
        mqtt_tls    = "false"
        mqtt_user   = ""
        mqtt_pass   = ""
    else:
        mqtt_broker = config.get("mqtt_host", "sigmatic-asc.tech")
        mqtt_port   = int(config.get("mqtt_port", 8883))
        mqtt_tls    = "true" if config.get("use_tls", True) else "false"
        mqtt_user   = config.get("mqtt_username", "")
        mqtt_pass   = config.get("mqtt_password", "")

    # Tablet fallback (used when cloud unreachable). Optional.
    tablet      = config.get("tablet_fallback") or {}
    tablet_host = tablet.get("host", "")
    tablet_port = int(tablet.get("port", 1883)) if tablet else 1883
    tablet_tls  = "true" if tablet.get("use_tls", False) else "false"
    tablet_user = tablet.get("username", "")
    tablet_pass = tablet.get("password", "")

    env = {
        "TENANT_ID":     tenant_id,
        "MODE":          mode,
        "ROLE":          role,
        "BLEX_API_TOKEN": api_token,
        "MQTT_BROKER":   mqtt_broker,
        "MQTT_PORT":     str(mqtt_port),
        "MQTT_USE_TLS":  mqtt_tls,
        "MQTT_USERNAME": mqtt_user,
        "MQTT_PASSWORD": mqtt_pass,
        "TABLET_HOST":     tablet_host,
        "TABLET_PORT":     str(tablet_port),
        "TABLET_USE_TLS":  tablet_tls,
        "TABLET_USERNAME": tablet_user,
        "TABLET_PASSWORD": tablet_pass,
    }
    env.update(_STATIC_DEFAULTS)
    # Allow caller to override any default with explicit "tuning" block.
    for k, v in (config.get("tuning") or {}).items():
        env[k] = str(v)

    return "".join(f"{k}={v}\n" for k, v in env.items())


def _write_env_and_flags(content: str):
    """Write /etc/blex/blex.env, then refresh /run/blex/ flags."""
    subprocess.run(["sudo", "mkdir", "-p", BLEX_ENV_DIR], capture_output=True)
    subprocess.run(["sudo", "tee", BLEX_ENV_FILE], input=content.encode(), capture_output=True)
    subprocess.run(["sudo", FLAGS_SCRIPT], capture_output=True)


class ProvisionHandler(http.server.BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path == "/status":
            self._response(200, _provision_status)
        else:
            self._response(404, {"status": "not_found"})

    def do_POST(self):
        if self.path != "/provision":
            self._response(404, {"status": "not_found"})
            return
        content_length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(content_length)
        try:
            config = json.loads(post_data.decode("utf-8"))
            ssid   = config.get("ssid")
            psk    = config.get("psk")
            mode   = config.get("mode", "cloud")
            tenant = config.get("tenant_id", "default")

            print(f"[PROVISIONER] mode={mode} tenant={tenant} mqtt={config.get('mqtt_host')}:{config.get('mqtt_port')}", flush=True)

            self._response(200, {
                "status": "ok",
                "message": "Connecting...",
                "config": {
                    "api_url":   "https://sigmatic-asc.tech/asset",
                    "web_url":   "https://sigmatic-asc.tech/blex",
                    "mqtt_host": config.get("mqtt_host"),
                    "tenant_id": tenant,
                    "mode":      mode,
                }
            })

            env_content = _build_env(config)
            _write_env_and_flags(env_content)
            print("[PROVISIONER] Wrote /etc/blex/blex.env and refreshed /run/blex/ flags", flush=True)

            def _restart_services():
                # Always restart provisioner-related services to pick up new env.
                if mode == "local":
                    subprocess.run(["sudo", "systemctl", "restart", "blex-scanner.service"], capture_output=True)
                    subprocess.run(["sudo", "systemctl", "restart", "blex-master.service"], capture_output=True)
                else:
                    subprocess.run(["sudo", "systemctl", "stop", "blex-master.service"], capture_output=True)
                    subprocess.run(["sudo", "systemctl", "restart", "blex-scanner.service"], capture_output=True)
                print(f"[PROVISIONER] Services restarted for mode={mode}", flush=True)

            threading.Thread(target=_restart_services, daemon=True).start()

            if ssid and psk:
                _do_wifi(ssid, psk)
            else:
                print("[PROVISIONER] No WiFi creds — skipping WiFi setup", flush=True)

        except Exception as e:
            print(f"[PROVISIONER] Error: {e}", flush=True)
            self._response(500, {"status": "error", "message": str(e)})

    def _response(self, code, data):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def log_message(self, *args):
        pass


def _do_wifi(ssid, psk):
    global _provision_status
    _provision_status = {"state": "connecting", "ssid": ssid}
    subprocess.run(["sudo", "nmcli", "connection", "delete", ssid], capture_output=True)
    subprocess.run(["sudo", "nmcli", "connection", "add", "type", "wifi", "ifname", "wlan0",
                    "con-name", ssid, "ssid", ssid, "wifi-sec.key-mgmt", "wpa-psk",
                    "wifi-sec.psk", psk, "connection.autoconnect", "yes",
                    "connection.autoconnect-priority", "10"], capture_output=True)
    subprocess.run(["sudo", "nmcli", "connection", "up", ssid], capture_output=True)
    threading.Thread(target=_watchdog, args=(ssid,), daemon=True).start()


def _watchdog(ssid, timeout=60):
    global _provision_status
    start = time.time()
    while time.time() - start < timeout:
        result = subprocess.run(["ping", "-c", "1", "-W", "3", "8.8.8.8"], capture_output=True)
        if result.returncode == 0:
            _provision_status = {"state": "connected", "ssid": ssid}
            subprocess.run(["sudo", "nmcli", "connection", "down", "setup"], capture_output=True)
            return
        time.sleep(5)
    subprocess.run(["sudo", "nmcli", "connection", "delete", ssid], capture_output=True)
    subprocess.run(["sudo", "nmcli", "connection", "up", "setup"], capture_output=True)
    _provision_status = {"state": "failed", "ssid": ssid, "error": "timeout"}


if __name__ == "__main__":
    print(f"[PROVISIONER] Listening on port {PORT}...", flush=True)
    server = http.server.HTTPServer(("0.0.0.0", PORT), ProvisionHandler)
    server.serve_forever()
