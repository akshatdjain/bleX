# Option B: Asset API Routing Configuration

## Overview
Asset API is now accessible via the `/asset` path prefix, keeping it separate from UI API endpoints.

## Architecture

```
EXTERNAL REQUEST
  ↓
https://sigmatic-asc.tech/asset/api/scanners
  ↓
Caddy (reverse proxy)
  ├─ Matches: handle /asset*
  ├─ Forwards full path to: HOST_IP:5000 (asset_api container)
  │  (Does NOT strip the /asset prefix)
  ↓
asset_api Container (port 8000)
  ├─ FastAPI root_path="/asset"
  ├─ Receives request: /asset/api/scanners
  ├─ Strips root_path prefix internally
  ├─ Router prefix="/api/scanners"  
  └─ Handler receives: /api/scanners ✅
```

## How FastAPI root_path Works

When you set `root_path="/asset"` in FastAPI:
- The app knows it's mounted at `/asset`
- FastAPI internally strips this prefix from incoming requests before routing
- OpenAPI docs and redirects are adjusted accordingly
- The reverse proxy should forward the **full path** including `/asset`
- FastAPI handles the prefix stripping automatically

## Endpoints

### Asset API (via /asset prefix)
- **Base URL**: `https://sigmatic-asc.tech/asset`
- **Scanners Endpoint**: `https://sigmatic-asc.tech/asset/api/scanners`
- **Other endpoints** follow the same pattern:
  - `/asset/api/assets` (assets router)
  - `/asset/api/zones` (zones router)
  - `/asset/api/movement` (movement router)
  - `/asset/api/runtime` (runtime router)
  - `/asset/api/health` (health check)

### UI API (via /api prefix)
- **Base URL**: `https://sigmatic-asc.tech/api`
- **Scanners Endpoint**: `https://sigmatic-asc.tech/api/scanners`
- **Other endpoints**:
  - `/api/assets`
  - `/api/zones`
  - `/api/history`
  - `/api/notifications`

## Testing Checklist

### 1. Verify Docker Services Are Running
```bash
cd /home/akshat/asset_tracking
docker-compose ps
# Should show: asset_api, ui_api, db all running
```

### 2. Update Caddy Configuration
```bash
cd /home/akshat/asset_tracking
bash update_caddy.sh
# This will update /home/raghu/sonic/Caddyfile and restart Caddy
```

### 3. Test Asset API Endpoint
```bash
# Direct test to container (internal)
curl http://localhost:5000/api/scanners

# Via Caddy (external)
curl https://sigmatic-asc.tech/asset/api/scanners

# Verbose version to see headers
curl -v https://sigmatic-asc.tech/asset/api/scanners
```

### 4. Verify Both APIs Are Accessible
```bash
# Asset API
curl https://sigmatic-asc.tech/asset/health

# UI API
curl https://sigmatic-asc.tech/api/scanners
```

## Troubleshooting

### Issue: "Connection refused" or "Host not found"
**Check:**
1. Asset API container is running: `docker ps | grep asset_api`
2. Port 5000 is exposed: `netstat -tulpn | grep 5000`
3. Caddy can reach the host IP: Check `update_caddy.sh` output for detected HOST_IP

### Issue: 404 on /asset/api/scanners
**Possible causes:**
1. Caddyfile not properly reloaded: Run `update_caddy.sh` again
2. Asset API not running: Check `docker logs asset_api`
3. Database not running: Check `docker logs db`

### Issue: CORS errors
**Status:** CORS is already enabled with `allow_origins=["*"]` in both APIs

## Configuration Files Modified

1. **update_caddy.sh** - Simplified routing:
   - Removed duplicate `/api/asset*` and `/api/runtime*` handlers
   - Added proper Host headers for reverse proxy
   - Cleaner separation of concerns

2. **asset_api/main.py** - Already correctly configured with:
   ```python
   root_path="/asset"
   ```

## Notes

- **root_path**: This is a FastAPI feature that instructs the app that all routes are served under `/asset`. Swagger UI and path prefixes work with this automatically.
- **Reverse Proxy Headers**: The `header_up Host` directive ensures proper Host header handling for internal communication.
- **Port Mapping**: Asset API listens on 8000 internally, exposed as 5000 on host (`5000:8000` in docker-compose).
