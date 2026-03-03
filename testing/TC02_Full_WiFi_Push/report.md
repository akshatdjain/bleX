# TC02: Individual Full Wi-Fi Push (Complete Payload)

## 📋 Scenario
- Action: Enter valid Wi-Fi SSID/PSK in "Wi-Fi Creds". Tap "Wi-Fi" action button on a scanner card.
- Logic: The App sends `ssid`, `psk`, `mqtt_host`, and `mqtt_port`.
- Practical Expectation: The scanner deletes its current connection, creates a new `nmcli` connection, and switches from the 'setup' hotspot to the new site Wi-Fi. It MUST also save the MQTT host.

## ✅ Status
**DONE**

## 🔍 Findings
- Full payload delivery verified.
- Scanner correctly transitions from AP to Site Home network.
- `nmcli` commands executed with `sudo` successfully.

## 📝 Comments
This remains the primary way to perform a fresh device setup.

## 📄 Logs
```bash
Received provisioning request for SSID: MyHomeWiFi
MQTT Broker: 192.168.43.1:1883
MQTT config saved to /home/pi/mqtt_config.json
```
