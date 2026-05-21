# BleX System Setup Guide

This document provides step-by-step instructions for deploying the BleX Asset Tracking platform hardware and software components.

---

## 1. Raspberry Pi (Scanner/Master) Deployment

Our Raspberry Pi instances are distributed as pre-configured disk images to minimize on-site configuration.

### **1.1 Hardware Setup**
- **Requirements**: Raspberry Pi 4 B or Pi 5, 16GB+ MicroSD Card, 5V/3A Power Supply.
- **Steps**:
    1. **Download**: Obtain the latest BleX OS image (`.img` or `.iso`) from the project repository.
    2. **Flash**: Use a tool like **Raspberry Pi Imager** or **Etcher** to write the image to the MicroSD card.
    3. **Boot**: Insert the card into the Pi and connect power.
    4. **Network**: 
        - **Wired**: Connect an Ethernet cable; the Pi will DHCP automatically.
        - **Wireless**: The image is pre-configured to look for a specific SSID (contact admin for credentials) or will start an "Initial Setup" hotspot on first boot.

### **1.2 Verification**
Once powered on, the Pi scanner will automatically start the `ble_scanner.service` and attempt to register with the Central API.

---

## 2. Android Tablet (Mobile Hub/Scanner)

The Android Tablet serves a dual role: it provides a portable scanning footprint and an on-field management interface.

### **2.1 Installation**
1. **Download**: Transfer the `blex.apk` to the tablet.
2. **Install**: Open the APK and allow "Installation from Unknown Sources" when prompted.

### **2.2 Required Permissions**
To ensure background scanning and MQTT stability, the following permissions **must** be granted:
- **Bluetooth & Nearby Devices**: Required for scanning advertising packets.
- **Location (Fine & Background)**: System requirement for BLE scanning on Android. Select **"Allow all the time"** for background tracking.
- **Notifications**: Required to keep the scanning service alive in the foreground.
- **Battery Optimization**: You must select **"Don't Optimize"** for the BleX app to prevent the system from killing the background scanning service during idle periods.

### **2.3 Network Configuration**
- Open **Settings** in the app and enter the **API URL**.
- If the tablet is hosting the **MQTT Broker** (Hybrid Hub Mode), ensure the "Embedded Broker" toggle is enabled.

---

## 3. BLE Beacons (Assets)

1. **Activation**: Ensure the beacon is powered (pull battery tab or press the physical button if applicable).
2. **Identification**: Note the MAC Address or UUID printed on the label.
3. **Provisioning**: Scan for the beacon using the Tablet App or Web UI and assign it a "Human Readable" name (e.g., "Cardiac Monitor 01") in the **Assets** configurator.

---

## 4. Initial System Check
1. Log in to the **Web Dashboard**.
2. Navigate to the **Scanners** tab.
3. Verify that all deployed Pi nodes and the Android Tablet show a status of **"Online"**.
4. Move a Beacon between two zones and verify the movement appears in the **Live View**.
