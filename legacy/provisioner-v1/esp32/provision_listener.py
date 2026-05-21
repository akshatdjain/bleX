import socket
import json
import time
import machine
import network
import _thread
import gc

UDP_PORT = 9000
HTTP_PORT = 8888

def get_mac():
    wlan = network.WLAN(network.STA_IF)
    mac = wlan.config('mac')
    return ':'.join(['%02x' % b for b in mac]).upper()

def broadcast_loop():
    print("Starting discovery broadcast...")
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    mac = get_mac()
    
    while True:
        try:
            wlan = network.WLAN(network.STA_IF)
            ip = wlan.ifconfig()[0]
            
            data = {
                "mac": mac,
                "ip": ip,
                "type": "esp32",
                "uptime": time.ticks_ms() // 1000
            }
            
            msg = json.dumps(data)
            sock.sendto(msg.encode(), ('255.255.255.255', UDP_PORT))
        except Exception as e:
            print("Broadcast error:", e)
        
        time.sleep(2)
        gc.collect()

def http_server():
    print("Starting provisioning listener on port", HTTP_PORT)
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('', HTTP_PORT))
    s.listen(1)

    while True:
        try:
            conn, addr = s.accept()
            print("Connection from:", addr)
            request = conn.recv(1024).decode()
            
            if 'POST /provision' in request:
                # Find JSON body
                body_start = request.find('{')
                if body_start != -1:
                    data = json.loads(request[body_start:])
                    print("Received WiFi Config:", data)
                    
                    with open('wifi_config.json', 'w') as f:
                        json.dump(data, f)
                    
                    conn.send('HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n')
                    conn.send(json.dumps({"status": "ok", "message": "Rebooting..."}))
                    conn.close()
                    
                    time.sleep(2)
                    machine.reset()
            
            conn.send('HTTP/1.1 404 Not Found\r\n\r\n')
            conn.close()
        except Exception as e:
            print("HTTP error:", e)
            try: conn.close()
            except: pass
        
        gc.collect()

def start():
    _thread.start_new_thread(broadcast_loop, ())
    _thread.start_new_thread(http_server, ())
