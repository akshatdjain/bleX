# BleX IoT Technical Architecture - Backend Services

**Document Version:** 1.0  
**Last Updated:** 16th of April 2026  
**Status:** Production  
**Classification:** Internal

---

## Table of Contents
1. [Overview](#overview)
2. [Service Architecture](#service-architecture)
3. [FastAPI Application](#fastapi-application)
4. [API Endpoints](#api-endpoints)
5. [Data Storage Layer](#data-storage-layer)
6. [Business Logic Layer](#business-logic-layer)
7. [Integration Patterns](#integration-patterns)
8. [Deployment Architecture](#deployment-architecture)
9. [Performance and Scalability](#performance-and-scalability)
10. [Monitoring and Observability](#monitoring-and-observability)

---

## Overview

The BleX Backend Services provide centralized data persistence, business logic processing, and API endpoints for multi-site asset tracking. The backend is **optional** in the BleX architecture - tablets can operate standalone with local storage.

### Design Principles

1. **API-First**: All functionality exposed via RESTful APIs
2. **Async by Default**: FastAPI with async/await for high concurrency
3. **Database-Agnostic**: SQLAlchemy ORM abstracts database details
4. **Stateless Services**: No session state stored in application tier
5. **Horizontal Scalability**: Services can be replicated across multiple instances

### Technology Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|----------|
| **Framework** | FastAPI | 0.100+ | REST API framework |
| **Server** | Uvicorn | 0.23+ | ASGI server |
| **ORM** | SQLAlchemy | 2.0+ | Database abstraction |
| **Database** | PostgreSQL | 14+ | Relational storage |
| **DB Driver** | asyncpg | 0.28+ | Async PostgreSQL driver |
| **Validation** | Pydantic | 2.0+ | Request/response schemas |
| **Migration** | Alembic | 1.12+ | Database migrations |
| **Cache** | Redis | 7.0+ | Session/state cache (optional) |

---

## Service Architecture

### High-Level Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                      Client Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │Android Tablet│  │ Web Dashboard│  │  API Client  │        │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘        │
└─────────┼──────────────────┼──────────────────┼────────────────┘
          │                  │                  │
          │ HTTPS/WSS        │ HTTPS            │ HTTPS
          │                  │                  │
          ▼                  ▼                  ▼
┌───────────────────────────────────────────────────────────────┐
│                    Load Balancer (Optional)                    │
│                    Nginx / AWS ALB / GCP LB                    │
└───────────────────────────┬───────────────────────────────────┘
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
         ▼                  ▼                  ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  FastAPI App 1  │  │  FastAPI App 2  │  │  FastAPI App N  │
│  (Uvicorn)      │  │  (Uvicorn)      │  │  (Uvicorn)      │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              │                               │
              ▼                               ▼
    ┌──────────────────┐           ┌──────────────────┐
    │   PostgreSQL     │           │   Redis (Cache)  │
    │   (Primary)      │           │   (Optional)     │
    └──────────────────┘           └──────────────────┘
              │
              │ Replication (Optional)
              ▼
    ┌──────────────────┐
    │   PostgreSQL     │
    │   (Replica)      │
    └──────────────────┘
```

### Service Layers

| Layer | Responsibility | Components |
|-------|----------------|------------|
| **API Layer** | HTTP request handling | FastAPI routers, middleware |
| **Business Logic** | Domain logic, validation | Service classes |
| **Data Access** | Database operations | SQLAlchemy models, repositories |
| **Integration** | External systems | MQTT client, HTTP clients |

---

## FastAPI Application

### Application Structure

```
backend/asset_api/
├── main.py                      # Application entry point
├── database.py                  # Database connection management
├── models.py                    # SQLAlchemy ORM models
├── schemas.py                   # Pydantic request/response schemas
├── events.py                    # Startup/shutdown events
├── config.py                    # Configuration management
├── dependencies.py              # Dependency injection
├── requirements.txt             # Python dependencies
├── alembic/                     # Database migrations
│   ├── versions/                # Migration scripts
│   └── env.py                   # Alembic environment
├── routers/
│   ├── __init__.py
│   ├── movement.py              # Movement event endpoints
│   ├── assets.py                # Asset CRUD
│   ├── zones.py                 # Zone management
│   ├── scanners.py              # Scanner registry
│   ├── runtime.py               # Runtime configuration sync
│   └── health.py                # Health check endpoints
├── services/
│   ├── movement_service.py      # Movement detection logic
│   ├── asset_service.py         # Asset business logic
│   └── notification_service.py  # Alert notifications
└── utils/
    ├── logging.py               # Structured logging
    └── metrics.py               # Prometheus metrics
```

### Application Entry Point

**File**: `main.py`

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from routers import movement, runtime, zones, assets, scanners, health
from database import create_tables

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    await create_tables()
    print("✓ Database tables created/verified")
    yield
    # Shutdown
    print("✓ Application shutdown")

app = FastAPI(
    title="BleX Asset Tracking API",
    description="Zone-based asset tracking with movement events",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc"
)

# CORS Configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # TODO: Restrict in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include Routers
app.include_router(movement.router, prefix="/api", tags=["Movement"])
app.include_router(runtime.router, prefix="/api/runtime", tags=["Runtime"])
app.include_router(zones.router, prefix="/api/zones", tags=["Zones"])
app.include_router(assets.router, prefix="/api/assets", tags=["Assets"])
app.include_router(scanners.router, prefix="/api/scanners", tags=["Scanners"])
app.include_router(health.router, prefix="/api", tags=["Health"])

@app.get("/", tags=["Root"])
async def root():
    return {
        "service": "BleX API",
        "version": "1.0.0",
        "status": "operational",
        "docs": "/docs"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,  # Disable in production
        log_level="info"
    )
```

### Database Connection Management

**File**: `database.py`

```python
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import declarative_base
import os

# Database URL from environment
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql+asyncpg://blex:password@localhost:5432/blex"
)

# Create async engine
engine = create_async_engine(
    DATABASE_URL,
    echo=False,  # Set True for SQL logging
    pool_size=20,
    max_overflow=40,
    pool_pre_ping=True,
    pool_recycle=3600
)

# Create session factory
AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False
)

# Base class for ORM models
Base = declarative_base()

async def get_db() -> AsyncSession:
    """Dependency for getting database session"""
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()

async def create_tables():
    """Create all tables on startup"""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
```

---

## API Endpoints

### Movement Events API

**Router**: `routers/movement.py`

#### POST /api/asset/movement

**Purpose**: Submit confirmed zone-change event from Android tablet

**Request Body**:
```json
{
  "asset_mac": "AC:23:3F:A1:B2:C3",
  "from_zone_id": 1,
  "to_zone_id": 2,
  "state": "ZONE",
  "deciding_rssi": -62.5,
  "timestamp": "2024-12-19T10:30:45Z"
}
```

**Response** (200 OK):
```json
{
  "ok": true,
  "detail": "zone updated → 2"
}
```

**Implementation**:
```python
from fastapi import APIRouter, HTTPException, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from datetime import datetime

from database import get_db
from models import MovementLog, MstAsset
from schemas import MovementIn, MovementOut

router = APIRouter()

@router.post("/asset/movement", response_model=MovementOut)
async def asset_movement(
    payload: MovementIn,
    db: AsyncSession = Depends(get_db)
):
    # Parse timestamp
    try:
        ts = datetime.fromisoformat(payload.timestamp)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid timestamp format")
    
    asset_mac = payload.asset_mac.upper()
    
    # Check if asset is registered
    stmt = select(MstAsset).where(MstAsset.bluetooth_id == asset_mac)
    result = await db.execute(stmt)
    asset = result.scalars().first()
    
    if not asset:
        return MovementOut(ok=True, detail="asset not registered, ignored")
    
    # Insert movement log
    movement = MovementLog(
        bluetooth_id=asset_mac,
        from_zone_id=payload.from_zone_id,
        to_zone_id=payload.to_zone_id,
        deciding_rssi=payload.deciding_rssi,
        timestamp_movement=ts
    )
    db.add(movement)
    
    # Update asset current zone
    if payload.state == "EXIT":
        asset.current_zone_id = None
    else:
        asset.current_zone_id = payload.to_zone_id
        asset.last_movement_dt = ts
    
    await db.commit()
    
    return MovementOut(
        ok=True,
        detail=f"zone updated → {payload.to_zone_id}"
    )
```

---

#### GET /api/assets/current

**Purpose**: Query current location of all assets

**Response** (200 OK):
```json
[
  {
    "mac": "AC:23:3F:A1:B2:C3",
    "zone": 2,
    "last_seen": "2024-12-19T10:30:45Z",
    "rssi": -62
  },
  {
    "mac": "AC:23:3F:D4:E5:F6",
    "zone": 1,
    "last_seen": "2024-12-19T10:29:12Z",
    "rssi": -58
  }
]
```

**Implementation**:
```python
@router.get("/assets/current")
async def get_current_status(db: AsyncSession = Depends(get_db)):
    stmt = select(MstAsset).where(MstAsset.current_zone_id.is_not(None))
    result = await db.execute(stmt)
    assets = result.scalars().all()
    
    return [
        {
            "mac": a.bluetooth_id,
            "zone": a.current_zone_id,
            "last_seen": a.last_movement_dt.isoformat() if a.last_movement_dt else None,
            "rssi": -65  # TODO: Store in extra field
        }
        for a in assets
    ]
```

---

#### GET /api/assets/history

**Purpose**: Retrieve recent movement events

**Query Parameters**:
- `limit` (optional): Max number of events (default 50)
- `asset_mac` (optional): Filter by specific asset

**Response** (200 OK):
```json
[
  {
    "id": 12345,
    "mac": "AC:23:3F:A1:B2:C3",
    "from_zone": 1,
    "to_zone": 2,
    "timestamp": "2024-12-19T10:30:45Z"
  }
]
```

**Implementation**:
```python
@router.get("/assets/history")
async def get_history(
    limit: int = 50,
    asset_mac: str = None,
    db: AsyncSession = Depends(get_db)
):
    stmt = select(MovementLog).order_by(MovementLog.timestamp_movement.desc())
    
    if asset_mac:
        stmt = stmt.where(MovementLog.bluetooth_id == asset_mac.upper())
    
    stmt = stmt.limit(limit)
    
    result = await db.execute(stmt)
    logs = result.scalars().all()
    
    return [
        {
            "id": l.id,
            "mac": l.bluetooth_id,
            "from_zone": l.from_zone_id,
            "to_zone": l.to_zone_id,
            "timestamp": l.timestamp_movement.isoformat()
        }
        for l in logs
    ]
```

---

### Assets API

**Router**: `routers/assets.py`

#### GET /api/assets

**Purpose**: List all registered assets

**Response**:
```json
[
  {
    "id": 1,
    "bluetooth_id": "AC:23:3F:A1:B2:C3",
    "asset_name": "Wheelchair 01",
    "current_zone_id": 2,
    "last_movement_dt": "2024-12-19T10:30:45Z"
  }
]
```

---

#### POST /api/assets

**Purpose**: Register a new asset

**Request Body**:
```json
{
  "bluetooth_id": "AC:23:3F:A1:B2:C3",
  "asset_name": "Wheelchair 01"
}
```

**Response** (201 Created):
```json
{
  "id": 1,
  "bluetooth_id": "AC:23:3F:A1:B2:C3",
  "asset_name": "Wheelchair 01",
  "current_zone_id": null
}
```

---

#### PUT /api/assets/{id}

**Purpose**: Update asset details

**Request Body**:
```json
{
  "asset_name": "Wheelchair 01 - Updated"
}
```

---

#### DELETE /api/assets/{id}

**Purpose**: Unregister an asset

**Response** (204 No Content)

---

### Zones API

**Router**: `routers/zones.py`

#### GET /api/zones

**Purpose**: List all zones with assigned scanners

**Response**:
```json
[
  {
    "id": 1,
    "zone_name": "Warehouse A",
    "description": "Main storage area",
    "scanners": [
      {
        "id": 1,
        "mac_id": "b8:27:eb:12:34:56",
        "name": "Scanner-01"
      }
    ]
  }
]
```

---

#### POST /api/zones

**Purpose**: Create a new zone

**Request Body**:
```json
{
  "zone_name": "Warehouse B",
  "description": "Secondary storage"
}
```

---

#### POST /api/zones/{id}/scanners

**Purpose**: Assign a scanner to a zone

**Request Body**:
```json
{
  "scanner_id": 2
}
```

---

### Scanners API

**Router**: `routers/scanners.py`

#### GET /api/scanners

**Purpose**: List all registered scanners

**Response**:
```json
[
  {
    "id": 1,
    "mac_id": "b8:27:eb:12:34:56",
    "name": "Scanner-01",
    "type": "pi",
    "last_heartbeat": "2024-12-19T10:30:00Z"
  }
]
```

---

#### PUT /api/scanners/by-mac/{mac}

**Purpose**: Upsert scanner (create if not exists, update if exists)

**Request Body**:
```json
{
  "name": "Scanner-01",
  "type": "pi"
}
```

---

### Runtime Configuration API

**Router**: `routers/runtime.py`

#### GET /api/runtime/scanner-zone-map

**Purpose**: Get scanner-to-zone mapping for decision engine

**Response**:
```json
{
  "b8:27:eb:12:34:56": 1,
  "b8:27:eb:78:90:ab": 2,
  "24:6f:28:cd:ef:01": 3
}
```

**Use Case**: Android tablet queries this on startup to build local cache

---

#### POST /api/runtime/master

**Purpose**: Master node registers its IP address

**Request Body**:
```json
{
  "name": "Tablet-01",
  "mac": "aa:bb:cc:dd:ee:ff",
  "ip": "192.168.1.100"
}
```

**Use Case**: Tablet publishes its IP so scanners know where to connect

---

## Data Storage Layer

### Database Schema

See `02_Component_Design_Database.md` for complete schema.

**Key Tables**:
- `mst_zone` - Zone definitions
- `mst_asset` - Asset registry
- `mst_scanner` - Scanner inventory
- `mst_zone_scanner` - Zone-scanner mapping
- `movement_log` - Event history (append-only)

### ORM Models

**File**: `models.py`

```python
from sqlalchemy import Column, Integer, BigInteger, Text, DateTime, Numeric, JSON, ForeignKey
from sqlalchemy.sql import func
from database import Base

class MstZone(Base):
    __tablename__ = "mst_zone"
    id = Column(Integer, primary_key=True)
    zone_name = Column(Text, nullable=False)
    description = Column(Text)
    dimension = Column(JSON)

class MstScanner(Base):
    __tablename__ = "mst_scanner"
    id = Column(Integer, primary_key=True)
    mac_id = Column(Text, nullable=False, unique=True)
    name = Column(Text)
    type = Column(Text)
    last_heartbeat = Column(DateTime(timezone=True))
    created_at = Column(DateTime(timezone=True), server_default=func.now())

class MstAsset(Base):
    __tablename__ = "mst_asset"
    id = Column(Integer, primary_key=True)
    bluetooth_id = Column(Text, nullable=False, unique=True)
    asset_name = Column(Text)
    current_zone_id = Column(Integer, ForeignKey("mst_zone.id"))
    last_movement_dt = Column(DateTime(timezone=True))
    created_at = Column(DateTime(timezone=True), server_default=func.now())

class MovementLog(Base):
    __tablename__ = "movement_log"
    id = Column(BigInteger, primary_key=True)
    bluetooth_id = Column(Text, nullable=False)
    from_zone_id = Column(Integer, ForeignKey("mst_zone.id"))
    to_zone_id = Column(Integer, ForeignKey("mst_zone.id"))
    deciding_rssi = Column(Numeric(6, 2))
    timestamp_movement = Column(DateTime(timezone=True), nullable=False)
```

### Database Indexing

**Performance Optimization**:
```sql
-- Movement log queries (most frequent)
CREATE INDEX idx_movement_timestamp ON movement_log(timestamp_movement DESC);
CREATE INDEX idx_movement_bluetooth ON movement_log(bluetooth_id);

-- Asset lookups
CREATE INDEX idx_asset_bluetooth ON mst_asset(bluetooth_id);
CREATE INDEX idx_asset_current_zone ON mst_asset(current_zone_id);

-- Scanner lookups
CREATE INDEX idx_scanner_mac ON mst_scanner(mac_id);
```

---

## Business Logic Layer

### Movement Detection Service

**File**: `services/movement_service.py`

```python
class MovementService:
    def __init__(self, db: AsyncSession):
        self.db = db
    
    async def process_movement_event(self, event: MovementIn) -> MovementOut:
        # Validate asset exists
        asset = await self.get_asset(event.asset_mac)
        if not asset:
            return MovementOut(ok=False, detail="Asset not registered")
        
        # Check if zone actually changed
        if asset.current_zone_id == event.to_zone_id:
            return MovementOut(ok=True, detail="No zone change")
        
        # Log movement
        await self.log_movement(event)
        
        # Update asset location
        await self.update_asset_location(asset, event.to_zone_id)
        
        # Trigger notifications (if configured)
        await self.notify_movement(asset, event)
        
        return MovementOut(ok=True, detail=f"Moved to zone {event.to_zone_id}")
```

---

## Integration Patterns

### MQTT Integration

**Tablet → Backend** (via MQTT Bridge):
```python
import paho.mqtt.client as mqtt

class MqttIntegration:
    def __init__(self, broker_url: str):
        self.client = mqtt.Client()
        self.client.on_message = self.on_message
        self.client.connect(broker_url)
    
    def on_message(self, client, userdata, msg):
        # Parse movement event
        event = json.loads(msg.payload)
        
        # Call API
        requests.post(
            "http://localhost:8000/api/asset/movement",
            json=event
        )
```

---

## Deployment Architecture

### Single-Server Deployment

```yaml
# docker-compose.yml
version: '3.8'

services:
  api:
    image: blex-api:latest
    ports:
      - "8000:8000"
    environment:
      DATABASE_URL: postgresql+asyncpg://blex:password@db:5432/blex
    depends_on:
      - db
  
  db:
    image: postgres:14
    environment:
      POSTGRES_DB: blex
      POSTGRES_USER: blex
      POSTGRES_PASSWORD: password
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

### Multi-Instance Deployment (Kubernetes)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: blex-api
spec:
  replicas: 3
  selector:
    matchLabels:
      app: blex-api
  template:
    metadata:
      labels:
        app: blex-api
    spec:
      containers:
      - name: api
        image: blex-api:latest
        ports:
        - containerPort: 8000
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: blex-secrets
              key: database-url
```

---

## Performance and Scalability

### Performance Targets

| Metric | Target | Current |
|--------|--------|--------|
| API Latency (p50) | <50ms | 30-40ms |
| API Latency (p99) | <200ms | 150-180ms |
| Throughput | 1000 req/sec | 500-800 req/sec |
| Database Connections | Max 100 | 20-40 typical |
| Memory Usage | <512MB | 200-300MB |

### Scalability Strategy

1. **Horizontal Scaling**: Add more API instances behind load balancer
2. **Database Read Replicas**: Route read queries to replicas
3. **Connection Pooling**: Reuse database connections
4. **Caching**: Redis for frequently accessed data
5. **Async Processing**: Background workers for heavy tasks

---

## Monitoring and Observability

### Health Check Endpoint

```python
@router.get("/health")
async def health_check(db: AsyncSession = Depends(get_db)):
    # Check database connection
    try:
        await db.execute(text("SELECT 1"))
        db_status = "healthy"
    except Exception:
        db_status = "unhealthy"
    
    return {
        "status": "healthy" if db_status == "healthy" else "degraded",
        "database": db_status,
        "timestamp": datetime.utcnow().isoformat()
    }
```

### Prometheus Metrics

```python
from prometheus_client import Counter, Histogram

movement_events_total = Counter(
    'blex_movement_events_total',
    'Total movement events processed'
)

api_latency = Histogram(
    'blex_api_latency_seconds',
    'API endpoint latency'
)
```

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | Apr 2026 | Akshat Jain | Initial release |

---

*This document is part of the BleX technical documentation suite.*