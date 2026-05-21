#!/usr/bin/env python3
# provisioner_service.py — BleX Pi Provisioner
# Listens on port 8888 for provisioning requests from the Android app.
# Handles WiFi setup, mode selection, tenant_id, and watchdog fallback.

import http.server
import json
import subprocess
import threading
import time
import os

PORT = 8888
SETUP_SSID = "setup"
BLEX_ENV_DIR = "/etc/blex"
BLEX_ENV_FILE = "/etc/blex/env"
MODE_JSON_FILE = "/etc/blex/mode.json"
MQTT_CONFIG_FILE = os.path.expanduser("~/mqtt_config.json")

_provision_status = {"state": "idle", "ssid": None}


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
            ssid       = config.get("ssid")
            psk        = config.get("psk")
            mqtt_host  = config.get("mqtt_host", "sigmatic-asc.tech")
            mqtt_port  = int(config.get("mqtt_port", 8883))
            tenant_id  = config.get("tenant_id", "default")
            mode       = config.get("mode", "cloud")   # "local" or "cloud"
            use_tls    = config.get("use_tls", True)

            print(f"[PROVISIONER] mode={mode} tenant={tenant_id} mqtt={mqtt_host}:{mqtt_port}", flush=True)

            # Send 200 immediately — Android app needs a fast response
            self._response(200, {
                "status": "ok",
                "message": "Connecting...",
                "config": {
                    "api_url": "https://sigmatic-asc.tech/asset",
                    "web_url": "https://sigmatic-asc.tech/beam",
                    "mqtt_host": mqtt_host,
                    "tenant_id": tenant_id,
                    "mode": mode,
                }
            })

            # Write /etc/blex/env (TENANT_ID + MODE for systemd services)
            subprocess.run(["sudo", "mkdir", "-p", BLEX_ENV_DIR], capture_output=True)
            env_content = f"TENANT_ID={tenant_id}\nMODE={mode}\n"
            subprocess.run(
                ["sudo", "tee", BLEX_ENV_FILE],
                input=env_content.encode(),
                capture_output=True
            )

            # Write /etc/blex/mode.json (full config for blex-mode.service)
            mode_data = {
                "mode": mode,
                "tenant_id": tenant_id,
                "mqtt_host": mqtt_host,
                "mqtt_port": mqtt_port,
                "use_tls": use_tls,
            }
            mode_json = json.dumps(mode_data, indent=2)
            subprocess.run(
                ["sudo", "tee", MODE_JSON_FILE],
                input=mode_json.encode(),
                capture_output=True
            )

            # Write ~/mqtt_config.json (read by scanner.py at startup)
            mqtt_config = {
                "mqtt_host": mqtt_host,
                "mqtt_port": mqtt_port,
                "tenant_id": tenant_id,
                "use_tls": use_tls,
                "mode": mode,
            }
            with open(MQTT_CONFIG_FILE, "w") as f:
                json.dump(mqtt_config, f)
            print(f"[PROVISIONER] Configs written — restarting services", flush=True)

            # Restart services in background so we don't block the HTTP response
            def _restart_services():
                # Re-run mode check so it picks up new mode.json and sets runtime flags
                subprocess.run(["sudo", "systemctl", "restart", "blex-mode.service"], capture_output=True)
                time.sleep(2)

                if mode == "local":
                    # Local: scanner + master both run on Pi
                    subprocess.run(["sudo", "systemctl", "restart", "blex-scanner.service"], capture_output=True)
                    subprocess.run(["sudo", "systemctl", "restart", "blex-master.service"], capture_output=True)
                else:
                    # Cloud: scanner publishes to DGX directly, master runs on DGX
                    # STOP local master — cloud master handles zone logic
                    subprocess.run(["sudo", "systemctl", "stop", "blex-master.service"], capture_output=True)
                    subprocess.run(["sudo", "systemctl", "restart", "blex-scanner.service"], capture_output=True)

                print(f"[PROVISIONER] Services restarted for mode={mode}", flush=True)

            threading.Thread(target=_restart_services, daemon=True).start()

            # Handle WiFi provisioning + watchdog
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
        pass  # suppress default HTTP logs


def _do_wifi(ssid, psk):
    global _provision_status
    _provision_status = {"state": "connecting", "ssid": ssid}

    # Add new WiFi without tearing down setup first
    subprocess.run(["sudo", "nmcli", "connection", "delete", ssid], capture_output=True)
    subprocess.run([
        "sudo", "nmcli", "connection", "add",
        "type", "wifi", "ifname", "wlan0",
        "con-name", ssid, "ssid", ssid,
        "wifi-sec.key-mgmt", "wpa-psk",
        "wifi-sec.psk", psk,
        "connection.autoconnect", "yes",
        "connection.autoconnect-priority", "10"
    ], capture_output=True)
    subprocess.run(["sudo", "nmcli", "connection", "up", ssid], capture_output=True)

    t = threading.Thread(target=_watchdog, args=(ssid,), daemon=True)
    t.start()


def _watchdog(ssid, timeout=60):
    global _provision_status
    start = time.time()
    print(f"[WATCHDOG] Checking connectivity for {ssid}...", flush=True)

    while time.time() - start < timeout:
        result = subprocess.run(["ping", "-c", "1", "-W", "3", "8.8.8.8"], capture_output=True)
        if result.returncode == 0:
            print(f"[WATCHDOG] Connected to {ssid}", flush=True)
            _provision_status = {"state": "connected", "ssid": ssid}
            # Tear down setup only after confirmed connection
            subprocess.run(["sudo", "nmcli", "connection", "down", "setup"], capture_output=True)
            subprocess.run(["sudo", "nmcli", "connection", "down", "AsseTrack-Setup"], capture_output=True)
            return
        time.sleep(5)

    # Timeout — revert to setup network
    print(f"[WATCHDOG] Timeout — reverting to setup network", flush=True)
    subprocess.run(["sudo", "nmcli", "connection", "delete", ssid], capture_output=True)
    subprocess.run(["sudo", "nmcli", "connection", "up", "setup"], capture_output=True)
    _provision_status = {"state": "failed", "ssid": ssid, "error": "timeout"}


if __name__ == "__main__":
    print(f"[PROVISIONER] Listening on port {PORT}...", flush=True)
    server = http.server.HTTPServer(("0.0.0.0", PORT), ProvisionHandler)
    server.serve_forever()
