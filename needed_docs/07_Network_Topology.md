# BleX Network Topology

**Document Version:** 1.0  
**Last Updated:** 16th of April 2026 
**Status:** Production  
**Classification:** Internal

---

## Overview

The BleX system operates across multiple network segments with flexible deployment options. This document describes supported network topologies, addressing schemes, and connectivity requirements.

## Deployment Topologies

### Topology 1: Standalone (Single-Site, No Backend)
- All devices on single subnet
- No internet required
- Tablet as MQTT broker
- Local SQLite storage

### Topology 2: Local Backend
- Segmented network (IoT VLAN + Corporate LAN)
- Local backend server
- PostgreSQL centralized storage
- Firewall between VLANs

### Topology 3: Cloud Backend (Multi-Site)
- Multiple sites reporting to central cloud
- Internet required for cloud sync
- Horizontal scaling of API tier

### Topology 4: Hybrid (Offline-First)
- Local MQTT always available
- SQLite cache for local persistence
- Periodic sync to cloud when online

## IP Addressing Schemes

**IoT VLAN** (192.168.100.0/24):
- 192.168.100.10 - Android Tablet (Hub)
- 192.168.100.101+ - Raspberry Pi Scanners
- 192.168.100.201+ - ESP32 Scanners

**Corporate LAN** (192.168.1.0/24):
- 192.168.1.50 - Backend server
- 192.168.1.100-200 - Workstations

## Port Assignments

| Port | Protocol | Service | Access |
|------|----------|---------|--------|
| 1883 | TCP | MQTT | Local only |
| 8883 | TCP | MQTT (TLS) | Local/Remote |
| 9000 | UDP | Discovery | Local only |
| 8888 | TCP | Provisioning | Setup WiFi |
| 443 | TCP | HTTPS/WSS | Internet |
| 5432 | TCP | PostgreSQL | Internal |

## Network Requirements

**Bandwidth**: ~200 kbps upstream for 10 scanners  
**Internet**: 1 Mbps down / 256 kbps up (minimum for cloud)  
**WiFi Signal**: -60 dBm or better recommended  
**Latency**: <100ms local, <500ms cloud

---

*See full documentation suite for complete details.*