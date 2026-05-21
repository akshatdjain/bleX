# BleX Server — Asset Tracking Backend

FastAPI backend for the BleX BLE asset tracking platform. Multi-tenant, schema-per-tenant PostgreSQL architecture.

## Structure

```
asset_tracking/
├── asset_api/        REST API — assets, zones, scanners, auth, runtime
├── ui_api/           Dashboard read API + built React UI (beam)
└── docker-compose.yml
```

## Running

```bash
docker compose up -d
```

- asset_api: http://localhost:5000 / https://sigmatic-asc.tech/asset
- beam UI:   http://localhost:4000 / https://sigmatic-asc.tech/beam
- adminer:   http://localhost:8080

## API Docs

https://sigmatic-asc.tech/asset/docs
