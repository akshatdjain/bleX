#!/usr/bin/env python3
import socket
import json
import time
import uuid
import subprocess
import re
import os
import sys

_BLEX_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _BLEX_DIR not in sys.path:
    sys.path.insert(0, _BLEX_DIR)
from cypher import get_logger
log = get_logger("discovery")

BROADCAST_PORT = 9000
INTERVAL = 2.0


def get_ip():
    try:
        result = subprocess.run(["ip", "-4", "addr", "show", "wlan0"], capture_output=True, text=True)
        match = re.search(r'inet (\d+\.\d+\.\d+\.\d+)', result.stdout)
        if match:
            return match.group(1)
    except:
        pass
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return None


def get_mac():
    mac = ':'.join(['{:02x}'.format((uuid.getnode() >> ele) & 0xff) for ele in range(0, 8*6, 8)][::-1])
    return mac.upper()


def send_heartbeat():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    mac = get_mac()
    log.info("discovery broadcast started", extra={"mac": mac, "port": BROADCAST_PORT})
    while True:
        try:
            ip = get_ip()
            if ip is None:
                log.warning("no ip on wlan0, waiting")
                time.sleep(5)
                continue
            data = {
                "mac": mac, "ip": ip, "type": "pi",
                "hostname": socket.gethostname(),
                "uptime": int(time.clock_gettime(time.CLOCK_BOOTTIME))
            }
            sock.sendto(json.dumps(data).encode('utf-8'), ('255.255.255.255', BROADCAST_PORT))
            time.sleep(INTERVAL)
        except Exception:
            log.error("heartbeat error", exc_info=True)
            time.sleep(5)


if __name__ == "__main__":
    send_heartbeat()
