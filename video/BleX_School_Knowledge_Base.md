# BleX Smart School: Enterprise Technical Specification
## "Campus Safety, Attendance Automation, and Operational Intelligence"

### 1. Hardware Engineering: The Smart ID Badge (The Beacon)
*   **Form Factor**: Standard CR80 ID-1 card format (85.60 x 53.98 mm). Thickness: 3.5mm.
*   **Material Selection**: Advanced PC/ABS (Polycarbonate/Acrylonitrile Butadiene Styrene) blend for high impact resistance and flame retardancy (UL94-V0).
*   **Sealing & Durability**: IP67-rated. Enclosure is sealed via high-frequency **Ultrasonic Welding**, making it tamper-proof and fully waterproof (survives 30 mins at 1m depth). 
*   **Battery Power**: Driven by a high-capacity **CR2477 Coin Cell** (1000mAh) or custom thin-film LiPo.
*   **Battery Management**: Dynamic Advertising Intervals. The badge alternates between **500ms (High-Activity Gate Mode)** and **5000ms (Power-Save Idle Mode)** based on onboard accelerometer motion detection.
*   **Safety Integration**: Pre-punched slot for **Breakaway Lanyards**, ensuring the badge detaches instantly under 5kg of force to prevent student injury.

### 2. RF & Protocol Layer: Wireless Precision
*   **Wireless Core**: BLE 5.3 (Bluetooth Low Energy) with support for **Coded PHY** (Long Range) and **AoA (Angle of Arrival)** for sub-meter location accuracy.
*   **Signal Processing**: BleX Hubs utilize **Kalman Filtering** and Weighted Moving Averages to smooth RSSI (signal strength) jitter, eliminating "ghost detections" through walls.
*   **Zone Logic**: 
    *   **Proximity Gating**: Detects "In-Room" vs. "In-Hallway" via signal path loss modeling.
    *   **Exit Detection**: 5-second "Exit Confirmation Window" to prevent false gate logs if a student just walks near a door.

### 3. Security & Privacy (The "Fortress" Layer)
*   **Payload Encryption**: BLE packets are encrypted using **AES-128 GCM** (Galois/Counter Mode), ensuring IDs cannot be sniffed or spoofed.
*   **Privacy Guard (MAC Randomization)**: The badge uses Private Resolvable Addresses that rotate every 15 minutes. This ensures students cannot be "tracked" by 3rd-party scanners outside the school premises.
*   **Anti-Replay Handshake**: For attendance, the BleX App issues a **Time-Synced Challenge**. The badge response must match the current 10-second session window to be valid.
*   **Compliance**: Built for **FERPA (Family Educational Rights and Privacy Act)** and **GDPR** compliance. All PII (Personally Identifiable Information) is hashed and stored in encrypted shards.

### 4. Software Ecosystem: The Intelligence Layer
*   **Teacher App (Fleet Manager)**: 
    *   **Mass-Roll Call**: Scans 50+ students in <10 seconds.
    *   **Student Profiling**: Instant HUD showing student photo, medical alerts (e.g., allergies), and emergency contact.
*   **Parent Connect (ERP Sync)**:
    *   **Push Notifications**: Real-time arrival/departure logs.
    *   **History**: Monthly attendance trends and automated "Absence Warning" reports.
*   **School Dashboard (Admin Console)**:
    *   **Geofencing**: Draw "Restricted Zones" on a digital map. Instant security alerts if a student badge enters the lab or construction area.
    *   **Missing Student Search**: "Last Seen" feature shows the exact time and Gateway Node that last pinged a student's badge.

### 5. Deployment & Logistics
*   **OTA Updates**: Gateway nodes (ESP32/Pi) support **Over-the-Air** firmware updates for security patches via the BleX Cloud.
*   **System Watchdog**: Integrated hardware watchdog timers auto-reboot any "locked" scanner nodes within 60 seconds to ensure 99.9% uptime.
*   **ERP Webhooks**: Direct RESTful API integration for **PowerSchool, Canvas, and Blackboard**. Automates the "Absentee List" export every morning.
