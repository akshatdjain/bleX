# BleX Cloud Master Engine

Zone-decision engine that subscribes to MQTT, applies Kalman + hysteresis + dwell-time logic, and pushes zone-change events to the asset API.

## Config

All tuning via env vars in `docker-compose.yml`:
- `TENANT_ID` — tenant schema (e.g. SF5WU6)
- `MQTT_BROKER` — broker hostname
- `SCANNER_TTL` — how long scanner RSSI is kept (25s for cloud)
- `ZONE_CONFIRM_COUNT` — confirmations before zone switch (3)
- `DWELL_TIME_SEC` — dwell time after confirm (8s)

## Deploy

```bash
docker compose up -d
```
