#!/usr/bin/env python3
"""
BleGod MQTT Receiver — Runs on Termux
======================================

This script subscribes to the MQTT broker and receives BLE beacon
scan data from the BleGod Android app.

SETUP (run these in Termux):
    pkg install mosquitto       # Install the MQTT broker
    mosquitto -d                # Start broker in background (daemon mode)
    pip install paho-mqtt       # Install Python MQTT client
    python mqtt_receiver.py     # Run this script

HOW IT WORKS:
    1. Connects to Mosquitto broker on localhost:1883
    2. Subscribes to "blegod/beacons/#" (# = wildcard for ALL device IDs)
    3. Receives JSON payloads from the BleGod app
    4. Parses and prints each beacon's MAC address and RSSI
    5. You can modify on_message() to feed data into your pipeline

MQTT TOPIC STRUCTURE:
    blegod/beacons/{device_id}  — one topic per tablet
    blegod/beacons/#            — wildcard to receive from ALL tablets

EXAMPLE MESSAGE PAYLOAD:
    {
        "device_id": "a1b2c3d4",
        "timestamp": 1708200000000,
        "beacon_count": 3,
        "beacons": [
            {
                "mac": "AA:BB:CC:DD:EE:FF",
                "rssi": -65,
                "timestamp": 1708200000000,
                "device_id": "a1b2c3d4",
                "tx_power": -59,
                "name": null
            },
            ...
        ]
    }

Usage:
    python mqtt_receiver.py                          # Default: localhost:1883
    python mqtt_receiver.py --host 192.168.1.100     # Custom broker host
    python mqtt_receiver.py --port 1884              # Custom broker port
    python mqtt_receiver.py --topic "blegod/beacons/a1b2c3d4"  # Specific device
"""

import json
import argparse
import signal
import sys
from datetime import datetime

import paho.mqtt.client as mqtt


# ══════════════════════════════════════════════════════════════════
#  CONFIGURATION
# ══════════════════════════════════════════════════════════════════

DEFAULT_BROKER_HOST = "127.0.0.1"
DEFAULT_BROKER_PORT = 1883
DEFAULT_TOPIC = "blegod/beacons/#"  # Wildcard: all devices


# ══════════════════════════════════════════════════════════════════
#  MQTT CALLBACKS
# ══════════════════════════════════════════════════════════════════

def on_connect(client, userdata, flags, reason_code, properties):
    """Called when connected to the MQTT broker."""
    print(f"\n{'='*60}")
    print(f"  ✅ Connected to MQTT Broker")
    print(f"  Subscribing to: {userdata['topic']}")
    print(f"{'='*60}\n")

    # Subscribe to the beacon topic
    client.subscribe(userdata["topic"], qos=1)


def on_disconnect(client, userdata, flags, reason_code, properties):
    """Called when disconnected from the MQTT broker."""
    print(f"\n  ❌ Disconnected from broker (rc={reason_code})")
    print(f"  Auto-reconnecting...")


def on_message(client, userdata, msg):
    """
    Called when a message is received on a subscribed topic.

    ╔═══════════════════════════════════════════════════════════════╗
    ║  THIS IS WHERE YOU CONNECT TO YOUR PYTHON PIPELINE!         ║
    ║                                                              ║
    ║  Modify this function to:                                    ║
    ║  - Store data in Redis                                       ║
    ║  - Feed into your math/distance calculations                 ║
    ║  - Forward to your API                                       ║
    ║  - Whatever your project needs!                              ║
    ╚═══════════════════════════════════════════════════════════════╝
    """
    try:
        # Parse the JSON payload
        payload = json.loads(msg.payload.decode("utf-8"))

        device_id = payload.get("device_id", "unknown")
        beacon_count = payload.get("beacon_count", 0)
        timestamp = payload.get("timestamp", 0)

        # Convert timestamp to readable time
        time_str = datetime.fromtimestamp(timestamp / 1000).strftime("%H:%M:%S")

        # Print header for this scan batch
        print(f"[{time_str}] 📡 Device: {device_id} | Beacons: {beacon_count}")

        # Print each beacon
        beacons = payload.get("beacons", [])
        for beacon in beacons:
            mac = beacon.get("mac", "??:??:??:??:??:??")
            rssi = beacon.get("rssi", 0)
            tx_power = beacon.get("tx_power")
            name = beacon.get("name")

            # Format output
            line = f"  ├─ MAC: {mac}  RSSI: {rssi:>4} dBm"
            if tx_power is not None:
                line += f"  TxPower: {tx_power} dBm"
            if name:
                line += f"  Name: {name}"
            print(line)

        if beacons:
            print(f"  └─ {'─' * 50}")

        # ──────────────────────────────────────────────────────
        #  👇 ADD YOUR PIPELINE CODE HERE 👇
        #
        #  Examples:
        #
        #  # Store in Redis:
        #  import redis
        #  r = redis.Redis()
        #  for b in beacons:
        #      r.hset(f"beacon:{b['mac']}", mapping=b)
        #
        #  # Send to your API:
        #  import requests
        #  requests.post("http://localhost:5000/api/beacons", json=payload)
        #
        #  # Your distance math:
        #  from your_module import calculate_distance
        #  for b in beacons:
        #      dist = calculate_distance(b['rssi'], b.get('tx_power', -59))
        #      print(f"    Distance: {dist:.2f}m")
        #
        # ──────────────────────────────────────────────────────

    except json.JSONDecodeError as e:
        print(f"  ⚠ Invalid JSON: {e}")
    except Exception as e:
        print(f"  ⚠ Error processing message: {e}")


# ══════════════════════════════════════════════════════════════════
#  MAIN
# ══════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="BleGod MQTT Receiver — Receives BLE beacon data from the BleGod Android app"
    )
    parser.add_argument(
        "--host",
        default=DEFAULT_BROKER_HOST,
        help=f"MQTT broker host (default: {DEFAULT_BROKER_HOST})"
    )
    parser.add_argument(
        "--port",
        type=int,
        default=DEFAULT_BROKER_PORT,
        help=f"MQTT broker port (default: {DEFAULT_BROKER_PORT})"
    )
    parser.add_argument(
        "--topic",
        default=DEFAULT_TOPIC,
        help=f"MQTT topic to subscribe to (default: {DEFAULT_TOPIC})"
    )
    args = parser.parse_args()

    print(f"""
╔═══════════════════════════════════════════════════════════════╗
║                                                               ║
║   🔵 BleGod MQTT Receiver                                    ║
║                                                               ║
║   Broker:  {args.host}:{args.port:<39}  ║
║   Topic:   {args.topic:<47}  ║
║                                                               ║
║   Waiting for beacon data...                                  ║
║   Press Ctrl+C to stop                                        ║
║                                                               ║
╚═══════════════════════════════════════════════════════════════╝
""")

    # Create MQTT client (v2 API)
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id="blegod_python_receiver",
        userdata={"topic": args.topic}
    )

    # Set callbacks
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_message = on_message

    # Enable auto-reconnect
    client.reconnect_delay_set(min_delay=1, max_delay=30)

    # Connect to broker
    try:
        client.connect(args.host, args.port, keepalive=60)
    except ConnectionRefusedError:
        print(f"\n❌ Cannot connect to MQTT broker at {args.host}:{args.port}")
        print(f"   Make sure Mosquitto is running:")
        print(f"   $ mosquitto -d")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Connection error: {e}")
        sys.exit(1)

    # Handle Ctrl+C gracefully
    def signal_handler(sig, frame):
        print(f"\n\n  Shutting down BleGod receiver...")
        client.disconnect()
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)

    # Start the MQTT loop (blocks forever, handles reconnection)
    client.loop_forever()


if __name__ == "__main__":
    main()
