# BleGod System API

A robust FastAPI backend that serves as the system of record for the BleGod ecosystem.

## 📋 Responsibilities

- **Inventory Management**: Permanent storage for registered BLE assets.
- **Topological Mapping**: Definition of Zones and their associations with Scanners.
- **Reporting**: Aggregation of scanner status and last-seen data.

## 🔌 API Endpoints

- `GET /api/assets`: List all registered beacons.
- `POST /api/assets`: Register a new beacon.
- `GET /api/zones`: List zones and assigned scanners.
- `POST /api/zones`: Create new logical zones.
- `GET /api/scanners`: List all provisioned scanner nodes.

## ⚙️ Setup

1. **Database**: Requires PostgreSQL. Create a database named `blegod`.
2. **Environment**:
   ```bash
   pip install -r requirements.txt
   ```
3. **Execution**:
   ```bash
   uvicorn asset_api.main:app --host 0.0.0.0 --port 8000
   ```

---
*Powered by FastAPI & SQLAlchemy.*
