#!/usr/bin/env python3
import http.server
import json
import subprocess
import threading
import time
import os

# Configuration
PORT = 8888
SETUP_SSID = "setup"
SETUP_PASS = "setup@1234"

# Global provision status
_provision_status = {"state": "idle", "ssid": None}


class ProvisionHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        """Handle GET requests, specifically /status endpoint."""
        if self.path == '/status':
            self._response(200, _provision_status)
        else:
            self._response(404, {"status": "not_found"})

    def do_POST(self):
        """Handle POST requests for /provision endpoint."""
        if self.path == '/provision':
            content_length = int(self.headers.get('Content-Length', 0))
            post_data = self.rfile.read(content_length)

            try:
                config = json.loads(post_data.decode('utf-8'))
                ssid = config.get('ssid')
                psk = config.get('psk')
                mqtt_host = config.get('mqtt_host')
                mqtt_port = config.get('mqtt_port', 1883)
                tenant_id = config.get('tenant_id', 'default')

                print(f"Received provisioning request. Fields: {list(config.keys())}")
                if mqtt_host:
                    print(f"MQTT Broker: {mqtt_host}:{mqtt_port}")

                # Build response config bundle
                response_config = {
                    "api_url": "https://sigmatic-asc.tech/asset",
                    "web_url": "https://sigmatic-asc.tech/beam",
                    "mqtt_host": mqtt_host,
                    "tenant_id": tenant_id
                }

                response_data = {
                    "status": "ok",
                    "message": "Connecting...",
                    "config": response_config
                }

                # Send 200 response IMMEDIATELY before any network operations
                self._response(200, response_data)

                # Save MQTT config if provided
                if mqtt_host:
                    mqtt_config_path = os.path.expanduser("~/mqtt_config.json")
                    mqtt_config = {
                        "mqtt_host": mqtt_host,
                        "mqtt_port": int(mqtt_port),
                        "tenant_id": tenant_id
                    }
                    with open(mqtt_config_path, 'w') as f:
                        json.dump(mqtt_config, f)
                    print(f"MQTT config saved to {mqtt_config_path}")

                # Only handle WiFi if both SSID and PSK are provided
                if ssid and psk:
                    print(f"Applying WiFi settings for SSID: {ssid}")
                    # Remove existing connection with same name (if re-provisioning)
                    subprocess.run(["sudo", "nmcli", "connection", "delete", ssid],
                                   capture_output=True)

                    # Save as a known network with high priority
                    subprocess.run([
                        "sudo", "nmcli", "connection", "add",
                        "type", "wifi",
                        "ifname", "wlan0",
                        "con-name", ssid,
                        "ssid", ssid,
                        "wifi-sec.key-mgmt", "wpa-psk",
                        "wifi-sec.psk", psk,
                        "connection.autoconnect", "yes",
                        "connection.autoconnect-priority", "10"
                    ])

                    # Attempt to connect to site WiFi (no immediate teardown of setup network)
                    print(f"Attempting to connect to {ssid}...")
                    subprocess.run(["sudo", "nmcli", "connection", "up", ssid],
                                   capture_output=True)

                    # Start watchdog thread to verify connectivity
                    watchdog_thread = threading.Thread(
                        target=_watchdog,
                        args=(ssid,),
                        daemon=True
                    )
                    watchdog_thread.start()
                else:
                    print("No WiFi credentials provided, skipping network setup.")

            except Exception as e:
                print(f"Error during provisioning: {e}")
                self._response(500, {"status": "error", "message": str(e)})
        else:
            self._response(404, {"status": "not_found"})

    def _response(self, code, data):
        """Send HTTP response with JSON payload."""
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode('utf-8'))

    def log_message(self, format, *args):
        """Suppress default HTTP request logging."""
        pass


def _watchdog(ssid, timeout=60):
    """
    Watchdog thread to verify WiFi connectivity.
    - Pings 8.8.8.8 every 5 seconds
    - If successful within timeout: tears down setup networks and marks as connected
    - If timeout reached: reverts connection and marks as failed
    """
    global _provision_status

    start_time = time.time()
    check_interval = 5

    print(f"Watchdog started for SSID '{ssid}', timeout={timeout}s")

    while time.time() - start_time < timeout:
        # Check connectivity with ping
        result = subprocess.run(
            ["ping", "-c", "1", "-W", "3", "8.8.8.8"],
            capture_output=True
        )

        if result.returncode == 0:
            # Ping succeeded, connection is good
            print(f"WiFi connection to '{ssid}' verified!")

            # Update status
            _provision_status = {"state": "connected", "ssid": ssid}

            # Tear down setup networks
            print("Tearing down setup network...")
            subprocess.run(["sudo", "nmcli", "connection", "down", "setup"],
                           capture_output=True)
            subprocess.run(["sudo", "nmcli", "connection", "down", "AsseTrack-Setup"],
                           capture_output=True)

            return

        # Wait before next check
        time.sleep(check_interval)

    # Timeout reached, connection failed
    print(f"Watchdog timeout: failed to connect to '{ssid}' within {timeout}s")

    # Delete the failed connection
    subprocess.run(["sudo", "nmcli", "connection", "delete", ssid],
                   capture_output=True)

    # Reconnect to setup network
    print("Reverting to setup network...")
    subprocess.run(["sudo", "nmcli", "connection", "up", "setup"],
                   capture_output=True)

    # Update status
    _provision_status = {
        "state": "failed",
        "ssid": ssid,
        "error": "timeout"
    }


if __name__ == "__main__":
    print(f"Provisioning listener active on port {PORT}...")
    server = http.server.HTTPServer(('0.0.0.0', PORT), ProvisionHandler)
    server.serve_forever()
