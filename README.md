# BleGod: Next-Gen Asset Tracking System

BleGod is a comprehensive, proximity-based asset tracking solution designed for industrial and commercial environments. It integrates Android tablets as central hubs/brokers with edge scanner nodes (Raspberry Pi/ESP32) to provide real-time visibility into asset locations and status.

## 🚀 System Architecture

The system is composed of three primary modules:

### 1. [Android Hub](./android)
A powerful Kotlin-based application built with Jetpack Compose.
- **Central Broker**: Runs an embedded Moquette MQTT broker, allowing the system to operate without an external server.
- **Proximity Engine**: High-performance BLE scanning and filtering (Kalman filter support).
- **Control Center**: Manage zones, register assets, and provision scan nodes.
- **Provisioner**: Auto-detects scanners in the network and pushes WiFi/MQTT credentials.

### 2. [Backend API](./backend)
A professional FastAPI (Python) service for system management.
- **RESTful API**: Handles zone management, asset registration, and scanner assignments.
- **Persistence**: PostgreSQL-backed data store with optimized schemas.
- **Scalability**: Designed to handle hundreds of scanners and thousands of assets.

### 3. [Scanner Nodes](./provisioner)
Edge devices responsible for raw BLE data collection.
- **Raspberry Pi**: Python-based scanners with support for heartbeats and remote provisioning.
- **ESP32**: Lightweight firmware for cost-effective coverage.
- **Auto-Provisioning**: Automated setup flow—connect once, provision forever.

## 🛠️ Tech Stack

- **Mobile**: Kotlin, Jetpack Compose, Material 3, Retrofit, Coroutines.
- **Backend**: Python 3.12+, FastAPI, SQLAlchemy, PostgreSQL.
- **Networking**: MQTT (Paho), UDP Discovery, HTTP Provisioning.
- **Edge**: Python (NMCLI integration), C++ (ESP32).

## 📂 Project Structure

- `android/`: The complete Android Studio project.
- `backend/`: FastAPI server and database models.
- `provisioner/`: Provisioning logic and edge-node scripts.
- `docs/`: Technical documentation and design notes.
- `scripts/`: Helper scripts for deployment and maintenance.

## 🔧 Getting Started

1. **Backend**: Navigate to `backend/`, install requirements, and run the FastAPI server.
2. **Android**: Open `android/` in Android Studio, sync Gradle, and install on a tablet.
3. **Provisioning**: 
   - Turn on the Android Hotspot.
   - Boot a Raspberry Pi with the `provisioner_service.py`.
   - Use the Android app to discover and push credentials.

## 🔮 Future Roadmap

- [ ] AI-powered predictive asset movement.
- [ ] Offline-first sync for remote sites.
- [ ] Advanced analytics dashboard in the app.
- [ ] Support for Ultra-Wideband (UWB) high-precision tracking.

---
*Created with passion by a creative engineering mind.*
