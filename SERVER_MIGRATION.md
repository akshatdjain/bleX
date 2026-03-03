# Mosquitto Setup: TLS 1.3 on Port 8883 (Non-Websocket)

Follow these steps to migrate from your old WSS/443 setup to a native MQTT over TLS configuration on the new server.

---

## 🚀 Option A: Native Linux Setup
Modify your `/etc/mosquitto/mosquitto.conf` (or create a file in `/etc/mosquitto/conf.d/tls.conf`).

```conf
# Disable default listener
listener 1883 127.0.0.1

# TLS 1.3 Listener
listener 8883
protocol mqtt

# Certificates
cafile /etc/mosquitto/certs/ca.crt
certfile /etc/mosquitto/certs/server.crt
keyfile /etc/mosquitto/certs/server.key

# TLS 1.3 Settings
tls_version tlsv1.3
require_certificate false

# Security
allow_anonymous false
password_file /etc/mosquitto/passwd
```

---

## 🐳 Option B: Docker Compose Setup
If you prefer running in Docker, use this `docker-compose.yml`:

```yaml
services:
  mosquitto:
    image: eclipse-mosquitto:latest
    container_name: mosquitto
    volumes:
      - ./mosquitto.conf:/mosquitto/config/mosquitto.conf
      - ./certs:/etc/mosquitto/certs
      - ./passwd:/mosquitto/config/passwd
    ports:
      - "8883:8883"
    restart: always
```

---

## 2. Generating Self-Signed Certificates
If you are using a raw IP address (not a domain), you must generate self-signed certs. Run these commands on your server:

```bash
mkdir -p certs
cd certs

# 1. Generate CA (Certificate Authority)
openssl genrsa -out ca.key 2048
openssl req -new -x509 -days 3650 -key ca.key -out ca.crt -subj "/CN=BleGod-CA"

# 2. Generate Server Key & CSR
openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr -subj "/CN=[YOUR_SERVER_IP]"

# 3. Sign the Server Cert with your CA
openssl x509 -req -days 3650 -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -sha256
```
*Note: Replace `[YOUR_SERVER_IP]` with the actual IP of the new server.*

---

## 3. Creating Passwords
To add your users:
```bash
mosquitto_passwd -c /etc/mosquitto/passwd [username]
```

---

## 4. Firewall Setup
Ensure port 8883 is open:
```bash
# If using UFW
ufw allow 8883
# If using Docker, ensure -p 8883:8883 is in your command
```

---

## 5. Android App Configuration
In the **BleGod App -> Settings**, update the **Remote Server** section:

1.  **Domain / Host**: `[Your Server IP]`
2.  **Port**: `8883`
3.  **TLS**: `ON`
4.  **WebSocket**: `OFF` (Native TLS uses `ssl://` or `tcp://` logic, not `wss://`)
5.  **Remote Username/Password**: Match what you set in Step 3.

---

## ⚠️ Important Note on Android & Self-Signed Certs
When using self-signed certificates on a raw IP, Android's default `SSLSocketFactory` might reject the connection because the CA is untrusted.

Options:
1.  **Recommended**: Use a Domain Name + **Let's Encrypt** (Certbot). It is free and works out-of-the-box with Android's system certs.
2.  **Advanced**: If you strictly need IP-only self-signed, I will need to update the app's `MqttBridge.kt` to include a custom TrustManager that accepts your `ca.crt`. (Let me know if you want me to write that code).
