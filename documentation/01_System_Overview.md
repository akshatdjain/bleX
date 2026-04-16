# BleX Asset Tracking System - System Overview

**Document Version:** 1.0  
**Last Updated:** 16th of April 2026
**Status:** Production  
**Classification:** Internal

---

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [System Vision](#system-vision)
3. [High-Level Architecture](#high-level-architecture)
4. [Core Components](#core-components)
5. [Key Features](#key-features)
6. [Deployment Models](#deployment-models)
7. [Technology Stack](#technology-stack)
8. [System Capabilities](#system-capabilities)

---

## Executive Summary

BleX is a next-generation **proximity-based asset tracking platform** designed for industrial and commercial environments. The system enables real-time tracking of BLE-enabled assets across defined zones using a distributed network of edge scanners orchestrated by Android tablets acting as intelligent hubs.

### Key Differentiators
- **Edge-First Architecture**: Processing happens at the edge (tablet), not the cloud
- **Embedded MQTT Broker**: No external infrastructure required for basic operation
- **Zero-Touch Provisioning**: Scanners auto-discover and configure themselves
- **Hybrid Deployment**: Works offline, online, or in hybrid mode
- **Cost-Effective**: Leverages commodity hardware (Android tablets, Raspberry Pi, ESP32)

### Business Value
- **Reduced Infrastructure Costs**: 60-80% savings vs traditional RTLS systems
- **Rapid Deployment**: Days instead of weeks for installation
- **Scalability**: Start with 1 zone, scale to 100+ without architectural changes
- **Flexibility**: Multiple deployment models for different scenarios

---

## System Vision

### Problem Statement
Traditional asset tracking systems face several challenges:
- **High Infrastructure Cost**: Require dedicated servers, gateways, and network equipment
- **Complex Setup**: Manual configuration of each scanner and network endpoint
- **Cloud Dependency**: Continuous internet required, data privacy concerns
- **Limited Flexibility**: Fixed installation, difficult to relocate or expand

### BleX Solution
BleX addresses these challenges through:
1. **Tablet-as-Hub Model**: Android tablets serve as mobile, self-contained tracking hubs
2. **Embedded Intelligence**: MQTT broker and processing engine run directly on the tablet
3. **Self-Provisioning Network**: Scanners discover and configure themselves automatically
4. **Zone-Based Tracking**: Room-level accuracy without complex trilateration
5. **Offline-Capable**: Full functionality without internet connectivity

---

## High-Level Architecture

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        BleX Ecosystem                                │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│  BLE Beacons     │         │  BLE Beacons     │         │  BLE Beacons     │
│  (Assets)        │         │  (Assets)        │         │  (Assets)        │
└────────┬─────────┘         └────────┬─────────┘         └────────┬─────────┘
         │                            │                            │
         │ BLE Broadcast              │ BLE Broadcast              │ BLE Broadcast
         │ (MAC, RSSI, TX)            │ (MAC, RSSI, TX)            │ (MAC, RSSI, TX)
         ▼                            ▼                            ▼
┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│   Pi Scanner     │         │  ESP32 Scanner   │         │   Pi Scanner     │
│   (Zone A)       │         │   (Zone B)       │         │   (Zone C)       │
└────────┬─────────┘         └────────┬─────────┘         └────────┬─────────┘
         │                            │                            │
         │ MQTT Publish               │ MQTT Publish               │ MQTT Publish
         │ tcp://tablet:1883          │ tcp://tablet:1883          │ tcp://tablet:1883
         │                            │                            │
         └────────────────────────────┼────────────────────────────┘
                                      ▼
         ┌─────────────────────────────────────────────────────────┐
         │           Android Tablet (Central Hub)                  │
         │                                                          │
         │  ┌────────────────────────────────────────────────┐    │
         │  │  Embedded Moquette MQTT Broker                 │    │
         │  │  - Listens on port 1883                        │    │
         │  │  - QoS 1, Auth, TLS support                    │    │
         │  │  - Handles 50+ concurrent clients              │    │
         │  └─────────────────┬──────────────────────────────┘    │
         │                    │                                     │
         │  ┌─────────────────▼──────────────────────────────┐    │
         │  │  BLE Scanner + MQTT Client                     │    │
         │  │  - Scans local beacons                         │    │
         │  │  - Publishes to local broker                   │    │
         │  └─────────────────┬──────────────────────────────┘    │
         │                    │                                     │
         │  ┌─────────────────▼──────────────────────────────┐    │
         │  │  MQTT Bridge                                   │    │
         │  │  - Subscribes to all local topics              │    │
         │  │  - Filters and enriches data                   │    │
         │  │  - Forwards to remote via WSS/TLS 1.3          │    │
         │  └─────────────────┬──────────────────────────────┘    │
         │                    │                                     │
         │  ┌─────────────────▼──────────────────────────────┐    │
         │  │  UI Dashboard (Jetpack Compose)                │    │
         │  │  - Real-time beacon visualization              │    │
         │  │  - Configuration screens                       │    │
         │  │  - System health monitoring                    │    │
         │  └────────────────────────────────────────────────┘    │
         └────────────────────────┬────────────────────────────────┘
                                  │
                                  │ HTTPS/WSS (Optional)
                                  ▼
         ┌─────────────────────────────────────────────────────────┐
         │           Backend API Server                            │
         │                                                          │
         │  ┌────────────────────────────────────────────────┐    │
         │  │  FastAPI Application                           │    │
         │  │  - RESTful endpoints                           │    │
         │  │  - WebSocket support                           │    │
         │  │  - Movement event processing                   │    │
         │  └─────────────────┬──────────────────────────────┘    │
         │                    │                                     │
         │  ┌─────────────────▼──────────────────────────────┐    │
         │  │  PostgreSQL Database                           │    │
         │  │  - Asset registry                              │    │
         │  │  - Zone definitions                            │    │
         │  │  - Movement logs (time-series)                 │    │
         │  │  - Scanner inventory                           │    │
         │  └────────────────────────────────────────────────┘    │
         └─────────────────────────────────────────────────────────┘
```

### Architecture Narrative

The BleX system follows a **hierarchical edge-computing architecture** with three primary layers:

#### 1. **Sensing Layer** (Edge)
- **BLE Beacons**: Battery-powered tags attached to assets, broadcasting identity and telemetry
- **Edge Scanners**: Raspberry Pi and ESP32 devices that capture BLE advertisements
- **Tablet BLE Scanner**: The Android hub also acts as a scanner for its immediate vicinity

#### 2. **Processing Layer** (Hub)
- **Android Tablet**: Acts as the central hub, running:
  - **Moquette MQTT Broker**: Receives data from all scanners
  - **Data Processing Engine**: Filters, aggregates, and analyzes beacon data
  - **MQTT Bridge**: Forwards processed data to backend (if configured)
  - **Local UI**: Real-time dashboard and configuration interface

#### 3. **Persistence Layer** (Backend - Optional)
- **FastAPI Server**: Processes movement events and serves APIs
- **PostgreSQL Database**: Stores asset registry, zones, and historical movements
- **Web Dashboard**: Alternative UI for remote monitoring

---

## Core Components

### 1. BLE Beacons (Assets)
**Purpose**: Identify and track physical assets

**Supported Standards**:
- **iBeacon** (Apple): UUID (16 bytes) + Major (2 bytes) + Minor (2 bytes)
- **Eddystone** (Google): UID, URL, TLM, EID formats

**Typical Hardware**:
- CR2032 battery-powered tags
- 1-3 year battery life at 1 Hz broadcast rate
- ~50m range (open air), 10-20m (indoor)

**Use Cases**:
- Equipment tags (tools, wheelchairs, carts)
- Personnel badges (optional, privacy considerations)
- Container/pallet tracking

---

### 2. Edge Scanners

#### Raspberry Pi Scanner
**Hardware**: Raspberry Pi 3/4, Bluetooth 4.0+  
**OS**: Raspberry Pi OS Lite  
**Software**: Python 3.9+, BlueZ stack, NMCLI  
**Power**: 5V 2.5A USB-C  
**Cost**: ~$50-75 per unit

**Capabilities**:
- Scan up to 50 beacons simultaneously
- MQTT publish at 1-5 Hz
- Auto-provisioning via WiFi
- Heartbeat reporting (health monitoring)
- Remote firmware updates (planned)

#### ESP32 Scanner
**Hardware**: ESP32-WROOM-32, built-in BLE  
**Software**: Arduino framework, PubSubClient library  
**Power**: 5V USB or 3.7V LiPo battery  
**Cost**: ~$5-10 per unit

**Capabilities**:
- Ultra-low cost deployment
- Battery-powered option (deep sleep mode)
- Scan up to 20 beacons simultaneously
- Basic MQTT publish functionality
- OTA firmware updates (planned)

---

### 3. Android Tablet Hub

**Recommended Hardware**:
- Android 12+ (API level 31+)
- 4GB+ RAM
- 64GB+ storage
- 8000mAh+ battery
- WiFi 5 (802.11ac) or better
- Examples: Samsung Galaxy Tab A8, Lenovo Tab P11

**Software Stack**:
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Material 3
- **MQTT Broker**: Moquette 0.17
- **MQTT Client**: Eclipse Paho
- **BLE Stack**: Android BluetoothLeScanner API

**Key Services**:
1. **Embedded MQTT Broker**
   - Port: 1883 (configurable)
   - Authentication: Username/password
   - TLS: Optional (self-signed or custom CA)
   - QoS: 0, 1, 2 supported
   - Persistent sessions: Yes

2. **BLE Scanner Service**
   - Foreground service (persistent notification)
   - Scan modes: LOW_POWER, BALANCED, LOW_LATENCY
   - Filters: iBeacon and Eddystone only
   - Batch processing: Configurable interval (default 5s)

3. **MQTT Bridge**
   - Local subscription: All topics (#)
   - Remote connection: WSS with TLS 1.3
   - Message queue: Up to 1000 messages during offline
   - Retry logic: Exponential backoff

4. **Data Processing Engine**
   - Zone determination: Strongest RSSI wins
   - Dwell time filtering: Configurable (default 10s)
   - Movement detection: Zone transition events
   - Asset filtering: Only registered beacons processed

5. **UI Dashboard**
   - Real-time beacon list with RSSI
   - Zone heatmap visualization
   - Scanner health monitoring
   - Configuration screens (zones, assets, scanners)
   - System logs viewer

---

### 4. Backend API (Optional)

**Purpose**: Centralized data persistence and multi-site aggregation

**Technology**:
- **Framework**: Python FastAPI 0.100+
- **Database**: PostgreSQL 14+
- **ORM**: SQLAlchemy 2.0 (async)
- **Server**: Uvicorn with auto-reload

**API Endpoints**:
- `/api/assets` - Asset CRUD operations
- `/api/zones` - Zone management
- `/api/scanners` - Scanner inventory
- `/api/asset/movement` - Movement event ingestion
- `/api/assets/current` - Real-time location query
- `/api/assets/history` - Historical movement log

**Database Schema**:
- `mst_asset` - Asset registry (MAC → Name mapping)
- `mst_zone` - Zone definitions
- `mst_scanner` - Scanner inventory
- `mst_zone_scanner` - Zone-to-scanner assignments
- `movement_log` - Time-series event log (append-only)

---

## Key Features

### 1. Auto-Provisioning System
**Zero-touch scanner deployment**

**Process**:
1. Scanner boots and connects to "setup" WiFi hotspot (preconfigured or tablet hotspot)
2. Scanner broadcasts UDP heartbeat: `{"mac": "aa:bb:cc:dd:ee:ff"}` on port 9000
3. Tablet's provisioning listener detects the broadcast
4. Tablet UI shows "Discoverable Scanners" list
5. User selects scanner and enters site WiFi credentials
6. Tablet POSTs `{ssid, psk, mqtt_host, mqtt_port}` to scanner HTTP endpoint (port 8888)
7. Scanner saves config, reboots, connects to site WiFi
8. Scanner connects to MQTT broker and starts publishing
9. Tablet UI shows scanner as "Active" with heartbeat status

**Benefits**:
- No manual SSH or terminal access needed
- Works with 100+ scanners in bulk provisioning
- Reduces deployment time by 90%

---

### 2. Zone-Based Tracking
**Room-level accuracy without GPS**

**Algorithm**:
```
FOR each beacon MAC:
    FOR each scanner in the network:
        IF scanner detected beacon:
            Store (zone_id, rssi) pair
    
    SELECT zone with highest RSSI
    
    IF zone changed from previous:
        IF dwell_time > threshold (default 10s):
            EMIT movement_event {
                from_zone: previous_zone,
                to_zone: new_zone,
                timestamp: now(),
                deciding_rssi: max_rssi
            }
```

**Advantages**:
- Simple and robust
- Works indoors (GPS doesn't)
- Privacy-friendly (no precise coordinates)
- Computationally lightweight

**Limitations**:
- Room-level accuracy only (not XY coordinates)
- Requires at least 1 scanner per zone
- RSSI can fluctuate (mitigated by dwell time filter)

---

### 3. Real-Time Dashboard
**Live visualization of asset locations**

**UI Screens**:
1. **Dashboard** - Real-time beacon list with RSSI bars
2. **Configurator** - Manage zones, assets, scanners, hotspot
3. **Settings** - MQTT, BLE, broker configuration
4. **Logs** - System event viewer with filtering
5. **Web Dashboard** - Embedded WebView for backend UI

**Key Features**:
- Material 3 adaptive theming
- Dark mode support
- Pull-to-refresh
- Real-time updates via Flow/State
- Offline-capable (local data)

---

### 4. Embedded MQTT Broker
**Self-contained messaging infrastructure**

**Why Embedded?**
- **No External Dependency**: Works in airgapped environments
- **Lower Latency**: No cloud round-trip (sub-100ms)
- **Cost Savings**: No broker hosting fees
- **Privacy**: Data stays on-premises by default

**Configuration**:
- Port: 1883 (or custom)
- Auth: Optional username/password
- TLS: Optional (self-signed or custom CA)
- Storage: Persistent sessions in app data directory
- Max Clients: 50+ (tested on mid-range tablets)

---

### 5. MQTT Bridge
**Hybrid cloud connectivity**

**Purpose**: Forward local MQTT data to remote server while maintaining offline capability

**Flow**:
```
Local Broker (localhost:1883)
    ↓ Subscribe (#)
Bridge Component
    ↓ Transform/Filter
    ↓ Queue if offline
Remote Broker (wss://server:443)
    ↓ Publish
Backend API
```

**Features**:
- Automatic reconnection with exponential backoff
- Message queuing (up to 1000 messages)
- Configurable forwarding rules (filter by topic)
- TLS 1.3 encryption
- Custom CA certificate support

---

## Deployment Models

### Model 1: Standalone (No Backend)
**Use Case**: Small sites, demos, proof-of-concept

**Components**:
- Android tablet with BleX app
- 1-10 edge scanners
- BLE beacons on assets

**Data Storage**: SQLite on tablet  
**Connectivity**: Local WiFi only  
**Cost**: ~$500-1500 total

**Pros**:
- Minimal setup
- No recurring costs
- Works offline
- Portable

**Cons**:
- Single point of failure (tablet)
- Limited to one site
- No historical data beyond 30 days

---

### Model 2: Local Backend
**Use Case**: Single facility with on-premises server

**Components**:
- Android tablet(s)
- 10-50 edge scanners
- Backend API on local server/NAS
- PostgreSQL database

**Data Storage**: Centralized PostgreSQL  
**Connectivity**: Local network + optional internet  
**Cost**: ~$2000-5000 total

**Pros**:
- Centralized data
- Multi-tablet support
- Long-term historical data
- Better redundancy

**Cons**:
- Requires server maintenance
- More complex setup

---

### Model 3: Cloud Backend
**Use Case**: Multi-site enterprise deployment

**Components**:
- Multiple Android tablets (one per site)
- 50-200+ edge scanners
- Backend API on AWS/Azure/GCP
- Managed PostgreSQL (RDS/Cloud SQL)

**Data Storage**: Cloud database  
**Connectivity**: Internet required for cloud sync  
**Cost**: ~$10,000+ (plus cloud fees)

**Pros**:
- Multi-site aggregation
- Centralized reporting
- Remote management
- High availability

**Cons**:
- Internet dependency
- Recurring cloud costs
- Data privacy considerations

---

### Model 4: Hybrid
**Use Case**: Remote sites with unreliable internet

**Components**:
- Android tablets with local caching
- Edge scanners
- Optional periodic cloud sync

**Data Storage**: Local SQLite + eventual cloud sync  
**Connectivity**: Offline-first, sync when online  
**Cost**: Variable

**Pros**:
- Works offline
- Cloud backup when available
- Best of both worlds

**Cons**:
- Conflict resolution needed
- More complex implementation

---

## Technology Stack

### Android Application
| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 1.9+ |
| UI Framework | Jetpack Compose | 2024.12.01 BOM |
| Design System | Material 3 | Latest |
| Async | Coroutines + Flow | 1.9.0 |
| MQTT Broker | Moquette | 0.17 |
| MQTT Client | Eclipse Paho | 1.2.5 |
| JSON | Gson | 2.11.0 |
| Navigation | Navigation Compose | 2.8.5 |
| Min SDK | Android 12 (API 31) | - |
| Target SDK | Android 15 (API 35) | - |

### Backend API
| Component | Technology | Version |
|-----------|------------|---------|
| Language | Python | 3.12+ |
| Framework | FastAPI | 0.100+ |
| Server | Uvicorn | 0.23+ |
| Database | PostgreSQL | 14+ |
| ORM | SQLAlchemy | 2.0+ (async) |
| DB Driver | asyncpg | 0.28+ |
| Validation | Pydantic | 2.0+ |

### Edge Scanners
| Component | Raspberry Pi | ESP32 |
|-----------|-------------|--------|
| Language | Python 3.9+ | C++ (Arduino) |
| BLE Stack | BlueZ | Built-in |
| MQTT Client | Paho MQTT | PubSubClient |
| WiFi Mgmt | nmcli (NetworkManager) | WiFi.h |
| Provisioning | HTTP (Flask) | HTTP (ESPAsyncWebServer) |

---

## System Capabilities

### Performance Metrics
| Metric | Value | Notes |
|--------|-------|-------|
| BLE Scan Rate | 100-500 beacons/sec | Depends on power mode |
| MQTT Throughput | 1000+ msgs/sec | Moquette on tablet |
| Bridge Latency | <100ms local, <500ms cloud | Via WSS |
| Battery Life (Tablet) | 24-48 hours | Continuous scanning |
| Max Scanners/Tablet | 50+ concurrent | MQTT connections |
| Max Beacons Tracked | 1000s | Database limited |
| Zone Transition Detection | ~10 seconds | Configurable dwell time |
| Provisioning Time | <2 minutes/scanner | Auto-provisioning |

### Scalability
- **Beacons**: Unlimited (database constrained, not app)
- **Scanners**: 50+ per tablet, 200+ per backend
- **Zones**: Unlimited
- **Tablets**: 1-100+ (with backend)
- **Sites**: Unlimited (with cloud backend)

### Reliability
- **Uptime**: 99%+ (with proper tablet power management)
- **Data Loss**: <0.1% (with message queueing)
- **Crash Recovery**: Auto-restart via foreground service
- **Offline Operation**: Full functionality without internet

### Security
- **MQTT Auth**: Username/password (optional)
- **Encryption**: TLS 1.2/1.3 for cloud connections
- **Data Privacy**: No PII stored (MAC addresses only)
- **Network**: Isolated WiFi for provisioning
- **API Auth**: Bearer tokens (JWT planned)

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Apr 2026 | Akshat Jain | Initial release |

**Next Review Date**: May 2026

---

*This document is part of the BleX technical documentation suite. For detailed component specifications, see related documents.*