# BleX Security Architecture

**Document Version:** 1.0  
**Last Updated:** 16th of April 2026 
**Status:** Production  
**Classification:** Internal

---

## Table of Contents
1. [Security Overview](#security-overview)
2. [Threat Model](#threat-model)
3. [Authentication and Authorization](#authentication-and-authorization)
4. [Data Encryption](#data-encryption)
5. [Network Security](#network-security)
6. [Device Security](#device-security)
7. [Application Security](#application-security)
8. [Operational Security](#operational-security)
9. [Compliance and Privacy](#compliance-and-privacy)
10. [Incident Response](#incident-response)

---

## Security Overview

The BleX system implements a **defense-in-depth** security architecture with multiple layers of protection. Given the IoT nature of the system with edge devices and wireless communications, security measures address physical, network, application, and data layers.

### Security Principles

1. **Zero Trust**: Verify every connection, even on local networks
2. **Least Privilege**: Grant minimum necessary permissions
3. **Defense in Depth**: Multiple security layers
4. **Secure by Default**: Security enabled out-of-the-box
5. **Privacy by Design**: Minimal data collection (no PII)

### Security Posture

| Layer | Security Measures | Risk Level |
|-------|-------------------|------------|
| **Physical** | Device authentication, tamper detection | Medium |
| **Network** | TLS encryption, isolated provisioning | Low |
| **Application** | Input validation, secure coding | Low |
| **Data** | Encryption at rest (optional), in transit | Low |
| **Access** | MQTT auth, API tokens | Medium |

---

## Threat Model

### Attack Surface Analysis

```
┌─────────────────────────────────────────────────────────────┐
│                    Attack Vectors                            │
└─────────────────────────────────────────────────────────────┘

1. BLE Broadcast Layer
   ┌──────────────────────────────────────┐
   │ Threat: Beacon Spoofing              │ Risk: Medium
   │ Mitigation: MAC whitelist            │
   └──────────────────────────────────────┘

2. WiFi Network Layer
   ┌──────────────────────────────────────┐
   │ Threat: Man-in-the-Middle            │ Risk: Low (local)
   │ Mitigation: WPA2/3 encryption        │
   └──────────────────────────────────────┘

3. MQTT Protocol Layer
   ┌──────────────────────────────────────┐
   │ Threat: Unauthorized publish         │ Risk: Medium
   │ Mitigation: Auth + TLS               │
   └──────────────────────────────────────┘

4. Provisioning Layer
   ┌──────────────────────────────────────┐
   │ Threat: Rogue scanner provisioning   │ Risk: Medium
   │ Mitigation: Isolated network, MAC    │
   │            verification, short window│
   └──────────────────────────────────────┘

5. Backend API Layer
   ┌──────────────────────────────────────┐
   │ Threat: API abuse, injection         │ Risk: Medium
   │ Mitigation: Rate limiting, input     │
   │            validation, prepared stmts│
   └──────────────────────────────────────┘

6. Android Application
   ┌──────────────────────────────────────┐
   │ Threat: Reverse engineering, tampering│ Risk: Low
   │ Mitigation: ProGuard obfuscation     │
   └──────────────────────────────────────┘
```

### Threat Scenarios

#### Scenario 1: Beacon Spoofing
**Threat**: Attacker broadcasts fake BLE beacons to inject false location data

**Attack Steps**:
1. Attacker captures legitimate beacon MAC address
2. Uses BLE development board to spoof beacons
3. System tracks fake beacon instead of real asset

**Mitigation**:
- **Asset Whitelist**: Only registered MAC addresses are tracked
- **RSSI Anomaly Detection**: Flag sudden signal strength changes
- **Beacon Authentication**: Use encrypted beacons (future)

**Risk Level**: Medium (requires physical proximity)

---

#### Scenario 2: MQTT Hijacking
**Threat**: Attacker intercepts or injects MQTT messages

**Attack Steps**:
1. Attacker gains access to local WiFi network
2. Sniffs MQTT traffic on port 1883
3. Publishes fake scan data or commands

**Mitigation**:
- **MQTT Authentication**: Username/password required
- **TLS Encryption**: Encrypted MQTT (port 8883)
- **Network Segmentation**: Scanners on isolated VLAN
- **Message Validation**: Server-side schema validation

**Risk Level**: Low (requires network access + credentials)

---

#### Scenario 3: Scanner Impersonation
**Threat**: Attacker provisions rogue scanner

**Attack Steps**:
1. Attacker connects to "setup" WiFi during provisioning window
2. Broadcasts fake UDP heartbeat with spoofed MAC
3. Gets provisioned and joins network

**Mitigation**:
- **MAC Verification**: Cross-check MAC against known hardware
- **Short Provisioning Window**: 5-minute timeout
- **Manual Approval**: Operator confirms each scanner in UI
- **Heartbeat Validation**: Monitor scanner behavior patterns

**Risk Level**: Medium (requires physical access during setup)

---

#### Scenario 4: Data Interception (Cloud)
**Threat**: Attacker intercepts data sent to backend

**Attack Steps**:
1. MITM attack on tablet's internet connection
2. Intercept MQTT/HTTPS traffic
3. Steal movement event data

**Mitigation**:
- **Mandatory TLS 1.3**: All cloud traffic encrypted
- **Certificate Pinning**: Validate server certificate (future)
- **Data Minimization**: Only movement events sent, no raw scans

**Risk Level**: Very Low (TLS protects against MITM)

---

#### Scenario 5: SQL Injection
**Threat**: Attacker exploits API to inject SQL

**Attack Steps**:
1. Send malicious input to API endpoint
2. Bypass input validation
3. Execute arbitrary SQL queries

**Mitigation**:
- **Parameterized Queries**: SQLAlchemy ORM (prevents injection)
- **Input Validation**: Pydantic schema validation
- **Least Privilege DB User**: App user has minimal permissions

**Risk Level**: Very Low (ORM prevents SQL injection)

---

## Authentication and Authorization

### MQTT Authentication

**Mechanism**: Username/Password

**Configuration** (Moquette - `EmbeddedBroker.kt`):
```kotlin
val props = Properties().apply {
    setProperty("allow_anonymous", "false")
    setProperty("password_file", "$dataDir/password_file.conf")
}

// Password file format (plain text - secured by OS permissions)
File("$dataDir/password_file.conf").apply {
    writeText("scanner:${settings.scannerPassword}\n")
    writeText("bridge:${settings.bridgePassword}\n")
    setReadable(false, false)  // Owner only
    setReadable(true, true)
}
```

**Scanner Configuration**:
```python
mqtt_client.username_pw_set("scanner", "<password>")
mqtt_client.connect(mqtt_host, mqtt_port)
```

**Password Policy**:
- Minimum 12 characters
- Alphanumeric + special characters
- Changed every 90 days (recommended)
- Not hardcoded in firmware (read from config file)

**Future Enhancement**: Client certificates (mutual TLS)

---

### API Authentication

**Current**: No authentication (trusted network)

**Planned** (v3.1.0):

```python
from fastapi import HTTPException, Depends, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt

security = HTTPBearer()

def verify_token(credentials: HTTPAuthorizationCredentials = Security(security)):
    token = credentials.credentials
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=["HS256"])
        return payload
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")

# Protected endpoint
@router.post("/api/asset/movement")
async def asset_movement(
    payload: MovementIn,
    user = Depends(verify_token)
):
    # Process request
    pass
```

**Token Structure** (JWT):
```json
{
  "sub": "tablet_abc123",
  "role": "hub",
  "exp": 1735560000,
  "iat": 1735473600
}
```

---

### Role-Based Access Control (RBAC)

**Planned Roles**:

| Role | Permissions |
|------|-------------|
| **Admin** | Full access: CRUD all resources, system config |
| **Operator** | Read/write assets, zones, scanners; trigger provisioning |
| **Viewer** | Read-only access to dashboard and reports |
| **Hub** | Submit movement events, query config |
| **Scanner** | Publish scan data, heartbeats |

**Implementation** (FastAPI):
```python
from enum import Enum

class Role(str, Enum):
    ADMIN = "admin"
    OPERATOR = "operator"
    VIEWER = "viewer"
    HUB = "hub"
    SCANNER = "scanner"

def require_role(allowed_roles: list[Role]):
    def dependency(user = Depends(verify_token)):
        if user["role"] not in allowed_roles:
            raise HTTPException(status_code=403, detail="Insufficient permissions")
        return user
    return dependency

# Example: Only admins can delete assets
@router.delete("/api/assets/{id}")
async def delete_asset(
    id: int,
    user = Depends(require_role([Role.ADMIN]))
):
    # Delete asset
    pass
```

---

## Data Encryption

### Encryption in Transit

#### MQTT over TLS

**Configuration** (Android - `MqttManager.kt`):
```kotlin
val sslContext = SSLContext.getInstance("TLS")

if (settings.mqttTlsEnabled) {
    if (settings.customCaCert.isNotEmpty()) {
        // Load custom CA certificate
        val cf = CertificateFactory.getInstance("X.509")
        val caInput = contentResolver.openInputStream(Uri.parse(settings.customCaCert))
        val ca = caInput.use { cf.generateCertificate(it) as X509Certificate }
        
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("ca", ca)
        }
        
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)
        
        sslContext.init(null, tmf.trustManagers, SecureRandom())
    } else {
        // Use system CA certificates
        sslContext.init(null, null, SecureRandom())
    }
    
    val options = MqttConnectOptions().apply {
        socketFactory = HostnameInsensitiveSocketFactory(sslContext.socketFactory)
    }
}
```

**TLS Configuration**:
- **Protocol**: TLS 1.3 (preferred), TLS 1.2 (fallback)
- **Cipher Suites**: AES-256-GCM, ChaCha20-Poly1305
- **Certificate Validation**: System CA or custom CA
- **Hostname Verification**: Disabled for raw IPs (by design)

---

#### HTTPS for Backend API

**Production Deployment** (Nginx reverse proxy):
```nginx
server {
    listen 443 ssl http2;
    server_name api.blex.example.com;
    
    ssl_certificate /etc/ssl/certs/api.blex.example.com.crt;
    ssl_certificate_key /etc/ssl/private/api.blex.example.com.key;
    
    # SSL Configuration
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384';
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;
    
    # HSTS
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    
    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

### Encryption at Rest

**Database** (PostgreSQL):
```bash
# Enable transparent data encryption (TDE)
# Option 1: Full disk encryption (LUKS on Linux)
sudo cryptsetup luksFormat /dev/sdb
sudo cryptsetup open /dev/sdb pgdata

# Option 2: PostgreSQL column-level encryption (pgcrypto)
CREATE EXTENSION pgcrypto;

UPDATE mst_asset 
SET asset_name = pgp_sym_encrypt(asset_name, 'encryption_key');
```

**Android Local Storage**:
```kotlin
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// Encrypted SharedPreferences
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

encryptedPrefs.edit().putString("mqtt_password", password).apply()
```

---

### Key Management

**MQTT Credentials**:
- Stored in Android EncryptedSharedPreferences
- Backed up with Android Auto Backup (encrypted)
- Never logged or transmitted in plaintext

**API Keys** (future):
- Generated via secure random (256-bit)
- Hashed with bcrypt before storage
- Rotated every 90 days

**TLS Certificates**:
- Let's Encrypt for public domains (auto-renewal)
- Self-signed for private deployments (manual)
- Custom CA certificates uploaded via UI

---

## Network Security

### Network Segmentation

```
┌─────────────────────────────────────────────────────────────┐
│                    Corporate Network                         │
│                    192.168.1.0/24                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ User Devices │  │   Servers    │  │  Printers    │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ Firewall/Router
                            │
┌─────────────────────────────────────────────────────────────┐
│                    IoT VLAN (Recommended)                    │
│                    192.168.100.0/24                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │Android Tablet│  │ Pi Scanners  │  │ ESP32 Scan.  │     │
│  │ (MQTT Broker)│  │              │  │              │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
│                                                             │
│  Firewall Rules:                                            │
│  - Allow: Scanners → Tablet:1883 (MQTT)                    │
│  - Allow: Tablet → Internet:443 (HTTPS)                    │
│  - Deny: IoT → Corporate (except Tablet)                   │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ Internet
                            ▼
                ┌─────────────────────────┐
                │   Backend API (Cloud)   │
                │   TLS 1.3 Required      │
                └─────────────────────────┘
```

**VLAN Configuration** (Cisco):
```
vlan 100
 name IoT-BleX
 exit

interface GigabitEthernet0/1
 switchport mode access
 switchport access vlan 100
```

---

### Firewall Rules

**Android Tablet** (iptables - if rooted):
```bash
# Allow MQTT from scanners
iptables -A INPUT -p tcp --dport 1883 -s 192.168.100.0/24 -j ACCEPT

# Allow outbound HTTPS (for backend API)
iptables -A OUTPUT -p tcp --dport 443 -j ACCEPT

# Drop all other inbound
iptables -A INPUT -j DROP
```

**Corporate Firewall**:
```
# Allow tablet to reach backend API
allow tcp 192.168.100.10 any 443

# Deny IoT devices from accessing corporate network
deny ip 192.168.100.0/24 192.168.1.0/24
```

---

### WiFi Security

**Production Network**:
- **Protocol**: WPA3 (or WPA2-Enterprise with 802.1X)
- **Encryption**: AES-256
- **SSID**: Hidden (optional, but reduces casual discovery)
- **MAC Filtering**: Whitelist scanner MAC addresses

**Provisioning Network** ("setup" WiFi):
- **Protocol**: WPA2-PSK
- **Password**: Strong (20+ characters), changed after provisioning
- **Duration**: Temporary (disabled after provisioning complete)
- **Isolation**: Separate from production network

---

## Device Security

### Android Tablet

**OS Security**:
- **Minimum OS**: Android 12 (API 31) - receives security patches
- **Updates**: Monthly security patches via OTA
- **Encryption**: Full disk encryption (enabled by default on Android 10+)
- **Lock Screen**: PIN/password required (enforced by MDM)

**Application Security**:
- **Code Obfuscation**: ProGuard/R8 in release builds
- **Root Detection**: Check for rooted devices (warn user)
- **Certificate Pinning**: (Planned) Validate server certificates

**Physical Security**:
- **Kiosk Mode**: Lock tablet to BleX app only (MDM policy)
- **Tamper Detection**: Alert if tablet leaves geofence (future)
- **Remote Wipe**: MDM can wipe tablet if stolen

---

### Raspberry Pi Scanners

**OS Security**:
- **Minimal Install**: Raspberry Pi OS Lite (no GUI)
- **Auto-Updates**: `unattended-upgrades` package
- **SSH**: Key-based auth only, password auth disabled
- **Firewall**: UFW configured (allow only MQTT + SSH)

**Hardening** (`/boot/cmdline.txt`):
```
initcall_debug=0 audit=1
```

**SSH Configuration** (`/etc/ssh/sshd_config`):
```
PermitRootLogin no
PasswordAuthentication no
PubkeyAuthentication yes
AuthorizedKeysFile .ssh/authorized_keys
Port 2222  # Non-standard port
```

**Systemd Service** (runs as non-root):
```ini
[Service]
User=pi
Group=pi
Capabilities=CAP_NET_RAW+eip  # Only for BLE scanning
```

---

### ESP32 Scanners

**Firmware Security**:
- **Secure Boot**: Enable in production (prevents firmware tampering)
- **Flash Encryption**: Encrypt firmware on-chip
- **OTA Updates**: Signed firmware images only

**Configuration**:
```cpp
// Enable secure boot and flash encryption
#define CONFIG_SECURE_BOOT_V2_ENABLED 1
#define CONFIG_SECURE_FLASH_ENC_ENABLED 1
```

**Network Security**:
- **WiFi**: WPA2/WPA3 only
- **MQTT**: TLS enabled (if sufficient flash)
- **No Web UI**: Disable HTTP server in production

---

## Application Security

### Secure Coding Practices

**Input Validation** (FastAPI):
```python
from pydantic import BaseModel, Field, validator
import re

class MovementIn(BaseModel):
    asset_mac: str = Field(..., regex="^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
    from_zone_id: int = Field(..., ge=1)  # Greater than or equal to 1
    to_zone_id: int = Field(..., ge=1)
    deciding_rssi: float = Field(..., ge=-100, le=-30)
    timestamp: str
    
    @validator('asset_mac')
    def uppercase_mac(cls, v):
        return v.upper()
```

**SQL Injection Prevention** (SQLAlchemy ORM):
```python
# Safe (parameterized)
stmt = select(MstAsset).where(MstAsset.bluetooth_id == user_input)

# Unsafe (avoid raw SQL with user input)
# db.execute(f"SELECT * FROM mst_asset WHERE bluetooth_id = '{user_input}'")
```

**XSS Prevention** (React/HTML escaping):
```kotlin
// Kotlin Compose (automatically escapes)
Text(text = userInput)  // Safe

// HTML (use TextUtils.htmlEncode)
val safeHtml = TextUtils.htmlEncode(userInput)
```

---

### Dependency Management

**Android** (`build.gradle.kts`):
```kotlin
// Use Gradle dependency verification
dependencyLocking {
    lockAllConfigurations()
}

// Check for vulnerabilities
// ./gradlew dependencyUpdates
```

**Python** (`requirements.txt` with hashes):
```
fastapi==0.100.0 \
    --hash=sha256:abc123...
sqlalchemy==2.0.0 \
    --hash=sha256:def456...
```

**Regular Scans**:
```bash
# Python
pip install safety
safety check

# Android
./gradlew dependencyCheckAnalyze
```

---

## Operational Security

### Secure Deployment

**Checklist**:
- [ ] Change default passwords (MQTT, database)
- [ ] Enable TLS for MQTT and HTTPS
- [ ] Configure firewall rules
- [ ] Disable unnecessary services
- [ ] Enable auto-updates
- [ ] Set up log monitoring
- [ ] Configure backup strategy
- [ ] Test disaster recovery plan

---

### Logging and Auditing

**What to Log**:
- Authentication attempts (success/failure)
- API requests (method, endpoint, user)
- Movement events (who, what, when)
- Configuration changes (zones, assets, scanners)
- Errors and exceptions

**What NOT to Log**:
- Passwords or credentials
- Full MQTT payloads (may contain sensitive data)
- Raw BLE advertisement data (MAC addresses are logged)

**Log Format** (structured JSON):
```json
{
  "timestamp": "2024-12-19T10:30:45.123Z",
  "level": "INFO",
  "component": "API",
  "event": "asset_movement",
  "user": "tablet_abc123",
  "asset_mac": "AC:23:3F:A1:B2:C3",
  "from_zone": 1,
  "to_zone": 2
}
```

**Log Retention**:
- **Application Logs**: 30 days (local), 90 days (centralized)
- **Audit Logs**: 1 year
- **Security Logs**: 2 years

---

### Backup and Recovery

**Database Backup** (PostgreSQL):
```bash
# Daily automated backup
pg_dump -U blex -d blex | gzip > /backups/blex_$(date +%F).sql.gz

# Retention: 7 daily, 4 weekly, 12 monthly
```

**Configuration Backup**:
- Export zones, assets, scanners as JSON
- Store in version control (Git)
- Encrypted at rest

**Disaster Recovery**:
1. Provision new tablet/server
2. Restore database from backup
3. Reinstall BleX app/API
4. Import configuration
5. Re-provision scanners (if needed)

**RTO**: 4 hours  
**RPO**: 24 hours (daily backups)

---

## Compliance and Privacy

### Data Privacy

**Data Collected**:
- **MAC Addresses**: BLE beacon and scanner MAC addresses
- **RSSI Values**: Signal strength measurements
- **Timestamps**: When beacons were detected
- **Zone IDs**: Logical location identifiers

**Data NOT Collected**:
- **PII**: No names, emails, phone numbers
- **Precise GPS**: Only zone-level location
- **Biometric Data**: None
- **Health Data**: None (unless beacons are medical devices)

**GDPR Considerations** (if applicable):
- MAC addresses may be considered personal data
- Provide data export functionality (GDPR Article 20)
- Implement data deletion (GDPR Article 17 - "Right to be forgotten")
- Maintain data processing records (GDPR Article 30)

**Implementation**:
```python
# Data export endpoint
@router.get("/api/assets/{id}/export")
async def export_asset_data(id: int, db: AsyncSession = Depends(get_db)):
    # Export all data related to asset
    asset = await db.get(MstAsset, id)
    movements = await db.execute(
        select(MovementLog).where(MovementLog.bluetooth_id == asset.bluetooth_id)
    )
    return {
        "asset": asset,
        "movements": movements.scalars().all()
    }

# Data deletion endpoint
@router.delete("/api/assets/{id}/gdpr-delete")
async def gdpr_delete_asset(id: int, db: AsyncSession = Depends(get_db)):
    # Delete asset and all related data
    asset = await db.get(MstAsset, id)
    await db.execute(
        delete(MovementLog).where(MovementLog.bluetooth_id == asset.bluetooth_id)
    )
    await db.delete(asset)
    await db.commit()
```

---

### Compliance Standards

**Applicable Standards** (depending on deployment):
- **ISO 27001**: Information Security Management
- **NIST Cybersecurity Framework**: Security controls
- **GDPR**: Data protection (EU)
- **HIPAA**: Healthcare (if tracking medical devices)
- **SOC 2**: Service organization controls

---

## Incident Response

### Incident Classification

| Severity | Definition | Response Time |
|----------|------------|---------------|
| **Critical** | System compromise, data breach | Immediate (1 hour) |
| **High** | Service disruption, vulnerability exploit | 4 hours |
| **Medium** | Failed login attempts, minor issues | 24 hours |
| **Low** | Informational, false positives | 7 days |

---

### Incident Response Plan

**Phase 1: Detection**
1. Monitor logs for suspicious activity
2. Alert on anomalies (failed auth, unusual API calls)
3. User reports security concerns

**Phase 2: Containment**
1. Isolate affected systems
2. Revoke compromised credentials
3. Enable additional logging

**Phase 3: Eradication**
1. Identify root cause
2. Patch vulnerabilities
3. Remove malicious code/scanners

**Phase 4: Recovery**
1. Restore from clean backups
2. Reset passwords
3. Monitor for persistence

**Phase 5: Lessons Learned**
1. Document incident
2. Update security measures
3. Train team on new procedures

---

### Contact Information

**Security Team**: security@blex.example.com  
**Incident Hotline**: +1-XXX-XXX-XXXX  
**PGP Key**: Available at https://blex.example.com/pgp

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Apr 2026 | Akshat Jain | Initial release |

**Next Review Date**: March 2025  
**Classification**: Internal - Security Sensitive

---

*This document is part of the BleX technical documentation suite. Handle with care.*