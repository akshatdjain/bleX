#!/usr/bin/env python3
import socket
import json
import time
import uuid
import subprocess
import re

# Configuration
BROADCAST_PORT = 9000
INTERVAL = 2.0

def get_ip():
    """Get IP from wlan0 interface directly — works even without internet."""
    try:
        result = subprocess.run(
            ["ip", "-4", "addr", "show", "wlan0"],
            capture_output=True, text=True
        )
        match = re.search(r'inet (\d+\.\d+\.\d+\.\d+)', result.stdout)
        if match:
            return match.group(1)
    except:
        pass
    # Fallback: try the old method (needs internet)
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return None

def get_mac():
    mac = ':'.join(['{:02x}'.format((uuid.getnode() >> ele) & 0xff)
                   for ele in range(0, 8*6, 8)][::-1])
    return mac.upper()

def send_heartbeat():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    
    mac = get_mac()
    print(f"Starting discovery broadcast for {mac}...")
    
    while True:
        try:
            ip = get_ip()
            if ip is None:
                print("No IP on wlan0 yet, waiting...")
                time.sleep(5)
                continue
                
            data = {
                "mac": mac,
                "ip": ip,
                "type": "pi",
                "hostname": socket.gethostname(),
                "uptime": int(time.clock_gettime(time.CLOCK_BOOTTIME))
            }
            
            message = json.dumps(data).encode('utf-8')
            sock.sendto(message, ('255.255.255.255', BROADCAST_PORT))
            time.sleep(INTERVAL)
        except Exception as e:
            print(f"Error sending heartbeat: {e}")
            time.sleep(5)

if __name__ == "__main__":
    send_heartbeat()
