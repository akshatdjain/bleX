# BleX Technical Documentation Suite

**Generated:** December 2024  
**Version:** 1.0  
**Purpose:** Comprehensive technical documentation for stakeholders and Confluence publishing

---

## Document Inventory

This directory contains complete technical documentation for the BleX Asset Tracking System, structured for Confluence or similar project management platforms.

### Core Documentation

| # | Document | Description | Pages |
|---|----------|-------------|-------|
| 01 | **System Overview** | High-level architecture, vision, deployment models, tech stack | ~20 |
| 02 | **Devices and Firmware** | BLE beacons, edge scanners (Pi/ESP32), firmware specs | ~30 |
| 03 | **Communication Protocols** | BLE, MQTT, HTTP, UDP, WSS - complete protocol specifications | ~25 |
| 04 | **Data Ingestion Layer** | Data pipeline, validation, transformation, buffering | ~20 |
| 05 | **Backend Services** | FastAPI architecture, API endpoints, database layer | ~18 |
| 06 | **Security Architecture** | Threat model, authentication, encryption, compliance | ~25 |
| 07 | **Network Topology** | Deployment topologies, IP schemes, connectivity patterns | ~15 |
| 08 | **Architecture Decision Records** | 10 key architectural decisions with rationale | ~15 |

**Total:** ~168 pages of technical documentation

---

## Document Structure

Each document follows a consistent structure:

```
1. Title Page
   - Document version, date, status, classification

2. Table of Contents
   - Hierarchical navigation

3. Overview Section
   - Executive summary
   - Key concepts
   - Design principles

4. Detailed Sections
   - Technical specifications
   - Code examples
   - Diagrams (ASCII art for text compatibility)
   - Tables and metrics

5. Document Control
   - Version history
   - Next review date
   - Change log
```

---

## How to Use

### For Confluence

1. **Import Markdown**: Use Confluence Markdown macro or convert to Confluence Storage Format
2. **Create Space**: Recommend creating "BleX Technical Documentation" space
3. **Page Hierarchy**:
   ```
   BleX Documentation (Home)
   ├── 01. System Overview
   ├── 02. Devices and Firmware
   ├── 03. Communication Protocols
   ├── 04. Data Ingestion Layer
   ├── 05. Backend Services
   ├── 06. Security Architecture
   ├── 07. Network Topology
   └── 08. Architecture Decision Records
   ```
4. **Add Labels**: `blex`, `iot`, `architecture`, `technical-docs`
5. **Set Permissions**: Restrict to engineering team as needed

### For Other Platforms

- **GitHub Wiki**: Direct markdown import
- **Notion**: Import as markdown pages
- **GitBook**: Create book from markdown files
- **Read the Docs**: Use MkDocs with markdown source

---

## Key Highlights

### System Overview (01)
- **Unique Architecture**: Edge-first with embedded MQTT broker on Android tablets
- **Deployment Flexibility**: Standalone, local backend, cloud, or hybrid
- **Cost-Effective**: 60-80% savings vs traditional RTLS systems
- **Technology Stack**: Kotlin, FastAPI, PostgreSQL, Moquette

### Devices and Firmware (02)
- **BLE Beacons**: iBeacon and Eddystone support with detailed parsing logic
- **Raspberry Pi Scanners**: Python-based with auto-provisioning
- **ESP32 Scanners**: C++ firmware for ultra-low-cost deployment
- **Android Hub**: Comprehensive tablet specifications and service architecture

### Communication Protocols (03)
- **BLE Protocol**: Complete advertisement packet structures with parsing examples
- **MQTT**: QoS levels, authentication, TLS configuration
- **Auto-Provisioning**: UDP discovery + HTTP config push flow
- **Security**: TLS 1.3, certificate management, hostname verification

### Data Ingestion (04)
- **Three-Tier Architecture**: Edge sensors → Hub → Backend
- **Pipeline Stages**: Capture, validate, transform, filter, aggregate, route
- **Performance**: 500-1500 msgs/sec throughput, <1s end-to-end latency
- **Quality**: <0.1% data loss, <1% duplicate rate

### Backend Services (05)
- **FastAPI Application**: Async-first, self-documenting API
- **Database Schema**: Normalized PostgreSQL with time-series optimization
- **API Endpoints**: Movement events, asset CRUD, zone management
- **Scalability**: Horizontal scaling, connection pooling, read replicas

### Security (06)
- **Threat Model**: 6 attack vectors analyzed with mitigations
- **Authentication**: MQTT username/password, JWT for API (planned)
- **Encryption**: TLS 1.3 for cloud, optional for local
- **Compliance**: GDPR considerations, data export/deletion

### Network Topology (07)
- **4 Deployment Models**: Standalone, local backend, cloud, hybrid
- **IP Addressing**: Recommended schemes for IoT VLAN and corporate LAN
- **Port Assignments**: Complete list with firewall rules
- **Troubleshooting**: Common issues and diagnostic commands

### Architecture Decisions (08)
- **10 ADRs Documented**: Key decisions with rationale and trade-offs
- **Highlights**:
  - Embedded broker (no external infrastructure)
  - Zone-based tracking (not GPS/trilateration)
  - Foreground service (24/7 reliability)
  - Kotlin + Compose (modern Android stack)
  - MQTT QoS 1 (balance reliability and performance)

---

## Technical Depth

This documentation suite provides:

### Code Examples
- **Kotlin**: Android BLE scanning, MQTT client, foreground service
- **Python**: FastAPI endpoints, SQLAlchemy models, Pi scanner firmware
- **C++**: ESP32 Arduino firmware
- **SQL**: Database schema, indexes, queries
- **Shell**: Deployment scripts, firewall rules

### Diagrams
- **Architecture diagrams**: System context, component interactions
- **Data flow diagrams**: End-to-end message flow
- **Network topologies**: Physical and logical layouts
- **Sequence diagrams**: Provisioning, movement detection

### Specifications
- **Protocol specs**: BLE advertisement formats, MQTT payloads
- **Performance metrics**: Throughput, latency, battery life
- **Security specs**: TLS configuration, authentication methods
- **Hardware specs**: Recommended devices, power requirements

---

## Audience

This documentation is designed for:

### Primary Stakeholders
- **Engineering Team**: Complete technical reference
- **DevOps/IT**: Deployment and network configuration
- **Security Team**: Threat model and security controls
- **Product Management**: Feature understanding and roadmap planning

### Secondary Stakeholders
- **Technical Sales**: System capabilities and architecture
- **Customer Success**: Implementation guidance
- **Executive Leadership**: High-level system understanding

---

## Maintenance

### Review Schedule
- **Quarterly Review**: March, June, September, December
- **Owner**: System Architecture Team
- **Process**:
  1. Review for accuracy
  2. Update with new features/decisions
  3. Incorporate feedback from engineering
  4. Publish updated versions to Confluence

### Version Control
- Documents stored in Git repository
- Changes tracked via commit history
- Major versions (1.0, 2.0) for significant updates
- Minor versions (1.1, 1.2) for corrections/additions

---

## Related Documentation

See also:

- **`/documentation/`** - Additional technical documents
  - `architecture.md` - Hybrid architecture specification
  - `api_documentation.md` - API and database reference
  - `setup_guide.md` - Deployment instructions
  - `test_scenarios_and_cases.md` - Testing documentation

- **`/docs/`** - Design documents
  - `BLEGOD_EMBEDDED_BROKER.txt` - Embedded broker deep dive
  - `BLEGOD_QA.txt` - Q&A document

- **`README.md`** (root) - Project overview
- **`RELEASING.md`** - Release process
- **`SERVER_MIGRATION.md`** - Backend migration guide

---

## Feedback

For questions, corrections, or additions:

- **Technical Questions**: engineering@blex.example.com
- **Documentation Issues**: docs@blex.example.com
- **Security Concerns**: security@blex.example.com

---

## Quick Reference

### System At-A-Glance

**Architecture**: Edge-first, embedded broker, zone-based tracking  
**Primary Language**: Kotlin (Android), Python (Backend)  
**Communication**: BLE → MQTT → HTTPS/WSS  
**Database**: PostgreSQL (backend), SQLite (tablet)  
**Deployment**: 4 models (standalone, local, cloud, hybrid)  
**Performance**: 1000+ msgs/sec, <1s latency, <0.1% data loss  
**Security**: TLS 1.3, MQTT auth, GDPR-friendly  
**Cost**: 60-80% savings vs traditional RTLS

### Key Metrics

| Metric | Value |
|--------|-------|
| BLE Range | 10-30m (indoor) |
| MQTT Latency | <100ms (local) |
| End-to-End Latency | 300ms-5s (depends on batching) |
| Scanners per Tablet | 50+ |
| Beacons Tracked | 1000s |
| Battery Life (Tablet) | 24-48 hours |
| Deployment Time | Days (not weeks) |

---

**Generated on:** December 19, 2024  
**Document Suite Version:** 1.0  
**Total Word Count:** ~50,000 words  
**Total Pages:** ~168 pages

---

*This documentation suite represents the complete technical knowledge of the BleX system as of December 2024. For the most up-to-date information, always refer to the latest version in the Git repository or Confluence.*