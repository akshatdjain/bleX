import network
import time
import json
import machine
import gc

def connect_wifi():
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    
    # Configuration for 'setup' fallback
    SETUP_SSID = "setup"
    SETUP_PASS = "setup@1234"
    
    try:
        with open('wifi_config.json', 'r') as f:
            config = json.load(f)
            ssid = config.get('ssid')
            psk = config.get('psk')
    except:
        ssid = None
        psk = None

    if ssid:
        print("Connecting to site WiFi:", ssid)
        wlan.connect(ssid, psk)
        
        # Wait up to 10s for connection
        for _ in range(20):
            if wlan.isconnected():
                print("Connected! IP:", wlan.ifconfig()[0])
                return True
            time.sleep(0.5)
        
        print("Failed to connect to site WiFi.")
    
    # Fallback to 'setup' hotspot
    print("Falling back to setup hotspot...")
    wlan.connect(SETUP_SSID, SETUP_PASS)
    for _ in range(20):
        if wlan.isconnected():
            print("Connected to SETUP hotspot. IP:", wlan.ifconfig()[0])
            return True
        time.sleep(0.5)
        
    return False

# Execute connection
connected = connect_wifi()
gc.collect()

if connected:
    # Start the background tasks
    import provision_listener
    provision_listener.start()
else:
    print("FATAL: No network found. Retrying in 30s...")
    time.sleep(30)
    machine.reset()
