# BleX Hybrid Architecture Specification

This document provides a detailed breakdown of the **Hybrid/Mobile-Centric** deployment model of the BleX Asset Tracking platform. In this architecture, an Android Tablet serves as both a primary data ingestion point and a potential central communication hub.

---

## 1. Architectural Overview

The Hybrid Model is designed for environments requiring high mobility or localized tracking where a fixed infrastructure is not feasible. It leverages a "Tab-as-a-Hub" capability, allowing the system to operate even in disconnected or temporary environments.

---

## 2. Layer Breakdown

### **2.1 Interaction & Scanning Layer (SH1)**
- **Hardware Mix**: Utilizes a combination of fixed Raspberry Pi scanners and active Bluetooth scanning on the Android Tablet via the BleX App.
- **Function**: Continuously listens for BLE Advertising packets. The Tablet specifically adds a layer of "Human-in-the-Loop" tracking as it can provide localized scans from areas where fixed infrastructure is absent.

### **2.2 Communication & Hub Layer (SH2)**
- **Host Flexibility**: The **MQTT Broker** can be hosted on a central Master Pi or directly on the **Android Tablet**.
- **Real-Time Pipeline**:
    - **MQTT Broker**: Handles incoming packets from all edge nodes.
    - **Redis State & Queue**: Acts as the high-speed data buffer. It stores the sub-second location state of every asset and manages the queue of movement events.

### **2.3 Storage & Persistence Layer (SH3)**
- **Asset API**: A FastAPI backend that implements the core business logic.
- **PostgreSQL**: The relational database used for long-term data persistence, including historical movement logs, asset registries, and zone configurations.

### **2.4 Consumption Layer**
- **Web Dashboard**: An interactive, real-time interface that fetches current status and heatmaps from the Asset API.
- **Android UI**: Provides on-site monitoring and configuration directly on the scanning device.

---

## 3. Data Flow Scenario

1.  **Broadcast**: BLE Beacons broadcast identity and signal metrics.
2.  **Capture**: Both the Pi Node and the Android Tablet capture these metrics (`MAC`, `RSSI`, `TX_Power`).
3.  **Transport**: Nodes publish standardized JSON payloads to the **MQTT Broker** (hosted on the Tab/Pi).
4.  **Triangulation**: The **Decision Engine** consumes these metrics from MQTT, performs dwell-time filtering and RSSI triangulation, and checks the current state in **Redis**.
5.  **Event Notification**: If a zone change is confirmed, the Engine pushes an event to the Redis queue.
6.  **Persistence**: The **Asset API** pops the event from Redis and writes a record to the **PostgreSQL** `movement_log`.
7.  **Visualization**: The **Web Dashboard** updates automatically as it polls the API for the latest movements.

---

## 4. Key Advantages
- **Portability**: Enables tracking in mobile sites (e.g., remote medical camps or fleet vehicles).
- **Redundancy**: The tablet provides secondary scanning coverage for areas with poor Pi placement.
- **Zero-Infrastructure Hub**: The ability to host the MQTT Broker on the Tablet eliminates the need for a dedicated server in simple deployments.
