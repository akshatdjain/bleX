# BleX Admin Panel - Technical Planning Document

## 1. Overview and Purpose

The BleX Admin Panel is a web-based management interface for superadmins and tenant administrators to manage the distributed BLE asset tracking platform. It centralizes control over multi-tenant deployments, remote devices (Raspberry Pi, ESP32, Android tablets), firmware updates, debugging, and operational monitoring.

### Users and Access

- **Superadmin** (Akshat): Sees all tenants, all devices globally, all logs, can push OTA updates to any device
- **Tenant Admin**: Sees only their tenant's devices, zones, assets, users; manages their own configuration
- **Tenant Viewer** (future): Read-only access to tenant data (for non-technical staff)

### Why It Exists

Currently, managing multi-tenant BLE tracker deployments requires direct SSH access to Pi devices and manual API calls. The admin panel replaces this with:

1. Unified device management dashboard
2. In-browser SSH terminal (no client software needed)
3. Firmware OTA push with canary rollout
4. Log aggregation and crash reporting
5. Tenant billing and plan management
6. Real-time device health monitoring

---

## 2. Tech Stack

### Frontend

- **Framework**: Next.js (existing deployment at sigmatic-asc.tech/beam, admin under /admin route)
- **UI Framework**: Tailwind CSS + existing component library
- **Terminal**: Xterm.js (v4/v5 - same as VS Code, GitHub Codespaces, Google Cloud Shell, Render, Railway)
- **State**: React Context or Zustand (colocate with existing dashboard patterns)
- **Charting**: Recharts or Chart.js (device health graphs)

### Backend

- **Framework**: FastAPI (existing at sigmatic-asc.tech/asset)
- **SSH Bridge**: Python asyncssh + websockets (integrates with existing Python infrastructure)
- **Auth**: JWT (existing bcrypt + shared.users system)
- **Remote SSH**: Tailscale (100.x.x.x/22 tunneling)
- **Logging**: Loki HTTP API or Grafana iframe
- **Database**: PostgreSQL (existing shared schema + new admin tables)

### Why Xterm.js + asyncssh

- **Xterm.js**: Battle-tested, used by every major cloud platform. Production-ready WebSocket integration.
- **asyncssh**: Cleanest Python async SSH library. Pairs perfectly with FastAPI WebSocket endpoints.
- **Tailscale SSH**: Zero-trust tunneling. Works from anywhere, firewall-agnostic, ephemeral keys.
- **No client**: Admins connect from any browser - no ssh client, no special software.
- **Architecture**: Browser (Xterm.js) <-> WebSocket <-> DGX asyncssh bridge <-> Tailscale SSH <-> Pi

---

## 3. Role System and Auth

### Database Schema Addition

Add role hierarchy to shared.users table:

```sql
ALTER TABLE shared.users ADD COLUMN (
    role TEXT DEFAULT 'viewer',  -- 'admin' | 'viewer'
    is_superadmin BOOLEAN DEFAULT FALSE
);

-- Superadmin has is_superadmin=TRUE
-- Tenant admin has role='admin' and is_superadmin=FALSE
-- Tenant viewer has role='viewer' and is_superadmin=FALSE
```

### Access Control Logic

**For /admin/* routes:**

```python
def require_superadmin(token: str) -> dict:
    claims = decode_token(token)
    if not claims.get('is_superadmin'):
        raise HTTPException(status_code=403, detail="Superadmin access required")
    return claims

def require_admin_for_tenant(token: str, tenant_id: str) -> dict:
    claims = decode_token(token)
    # Superadmin always passes
    if claims.get('is_superadmin'):
        return claims
    # Tenant admin must own the tenant
    if claims.get('tenant_id') == tenant_id and claims.get('role') in ['admin']:
        return claims
    raise HTTPException(status_code=403, detail="Not authorized for this tenant")
```

### Token Structure

```json
{
  "user_id": "uuid",
  "tenant_id": "tenant_123",
  "username": "akshat",
  "is_superadmin": true,
  "role": "admin",
  "exp": 1234567890
}
```

---

## 4. Admin Panel Sections

### 4.1 Dashboard Overview (Superadmin)

**Route**: `/admin`

**Purpose**: System-wide snapshot and quick actions.

**Components**:

1. **KPI Cards** (top row):
   - Total tenants count
   - Total active devices (online in last 5 min)
   - Total beacons tracked today (from MQTT message count)
   - System uptime (DGX)

2. **Device Health Grid**:
   - Each Pi/ESP32 as a card
   - Shows: device_id, name, tenant, online/offline status, last seen, uptime %
   - Color-coded: green (online > 95%), yellow (80-95%), red (< 80%)
   - Click to drill into device detail

3. **Tenant List**:
   - Table: tenant_id, name, plan, created_at, device_count, user_count, last_activity
   - Sort by: last_activity (default), device_count, name
   - Search by tenant name

4. **System Health Panel**:
   - DGX CPU % (via /api/admin/system/health)
   - DGX RAM usage %
   - MQTT broker connection count
   - PostgreSQL active connections
   - Loki indexing lag

5. **Quick Actions**:
   - View all pending OTA updates
   - View critical alerts (any device offline > 1h)
   - Export tenant list as CSV

---

### 4.2 Tenant Management

**Route**: `/admin/tenants`

**Purpose**: Create, view, update, and manage tenant deployments.

**Sections**:

#### 4.2.1 Tenant List

- **Table columns**: tenant_id, name, plan, created_at, active_devices, active_users, last_activity, actions
- **Actions per tenant**:
  - View detail
  - Edit (name, plan tier)
  - Promote master tier (pooled -> dedicated)
  - Delete (with confirmation modal)
- **Bulk actions**: Delete multiple, export

#### 4.2.2 Tenant Detail

**Route**: `/admin/tenants/{tenant_id}`

- **Overview tab**:
  - Tenant metadata: id, name, plan, created_at, schema_name (t_{tenant_id})
  - Device count (total, online, offline)
  - User count (active, inactive)
  - Total assets in registry
  - Total zones
  - Plan upgrade/downgrade button

- **Users tab**:
  - List of users in tenant
  - Can add user, remove user, reset password
  - Links to shared.users table

- **Zones tab**:
  - List of zones in tenant schema
  - Create new zone
  - Edit zone (name, location metadata)

- **Scanners tab**:
  - List of Pi/ESP32 devices assigned to this tenant
  - Scanner config (MQTT broker, pool settings)

- **Assets tab**:
  - Beacon registry (MAC -> name mapping)
  - Import/export asset CSV

#### 4.2.3 Create Tenant

**Route**: `/admin/tenants/new` (POST)

- Form: tenant_name, admin_email, admin_password, plan_tier
- Calls existing POST /api/auth/register flow (backend handles schema creation)
- Stores in shared.tenants + creates t_{tenant_id} schema
- Confirmation email to admin_email

#### 4.2.4 Delete Tenant

- Modal confirmation: "This will permanently delete the tenant, all schemas, zones, scanners, and assets."
- Calls DELETE /admin/tenants/{tenant_id}
- Backend executes: DROP SCHEMA t_{tenant_id} CASCADE; DELETE FROM shared.tenants WHERE tenant_id=?
- Audit log: who deleted, when, tenant_id

---

### 4.3 Device Management

**Route**: `/admin/devices`

**Purpose**: Monitor, control, and debug all Pi/ESP32 scanners.

#### 4.3.1 Device List

- **Table columns**: device_id (MAC), name, tenant, type (pi/esp32), online_status, last_seen, firmware_version, uptime_%
- **Filters**: by tenant, by status (online/offline/error), by type
- **Search**: by device_id, name
- **Actions per device**:
  - View detail
  - Enable Debug (only if not already enabled)
  - Disable Debug (only if enabled)
  - Open Terminal (only if debug enabled)
  - Reboot device
  - View logs
  - Push OTA
  - Edit device (rename, change tenant)

#### 4.3.2 Device Detail

**Route**: `/admin/devices/{device_id}`

**Tabs**:

1. **Overview**:
   - Device metadata: device_id (MAC), name, type, tenant, serial (if available)
   - Network: Tailscale IP (if debug enabled), local IP (from last MQTT message)
   - Firmware: current_version, boot_time, uptime
   - Last heartbeat: timestamp, RSSI_avg, CPU %, RAM %, temperature
   - Online status: green if heartbeat < 5 min, yellow if < 1 hour, red if > 1 hour

2. **Health History**:
   - Chart: uptime % over last 7 days (daily)
   - Chart: RAM usage % over last 24 hours (hourly)
   - Chart: CPU % over last 24 hours (hourly)
   - Chart: Temperature (if available) over last 24 hours

3. **MQTT Topics**:
   - Display: ble/scanner/{device_id} (scanner publish)
   - Display: admin/scanner/{device_id} (commands - reboot, OTA, etc.)
   - Last 10 messages received (timestamp, topic, payload snippet)

4. **Firmware**:
   - Current version
   - Available updates (table from shared.releases)
   - Current OTA status (if pushing)
   - Rollback option (if previous version in history)

5. **Logs** (embedded log viewer):
   - Real-time tail or last 1000 lines
   - Filter by level (DEBUG/INFO/WARN/ERROR)
   - Search by text
   - Date range picker
   - Export as .txt or .json

#### 4.3.3 Device Actions

**Enable Debug**:
- Button: "Enable Debug"
- Calls: POST /admin/devices/{device_id}/ssh/enable
- Backend:
  - Checks device exists and is online
  - Generates ephemeral Tailscale auth key (1 hour TTL)
  - Sends MQTT command: admin/scanner/{device_id} { "action": "tailscale_enable", "auth_key": "..." }
  - Waits for response (up to 30 sec) or times out
  - Stores tailscale_ip in shared.devices.tailscale_ip
  - Sets debug_enabled=TRUE, debug_enabled_at=NOW()
  - Response: { "status": "enabled", "ip": "100.x.x.x" }
- UI shows: "Debug enabled at 100.x.x.x, will expire in 1 hour"
- Auto-refresh device detail to show "Open Terminal" button

**Disable Debug**:
- Button: "Disable Debug"
- Calls: DELETE /admin/devices/{device_id}/ssh/enable
- Backend:
  - Sets debug_enabled=FALSE
  - Sends MQTT command: admin/scanner/{device_id} { "action": "tailscale_disable" }
  - Returns confirmation
- UI removes "Open Terminal" button

**Open Terminal**:
- Button: "Open Terminal" (only appears if debug_enabled=TRUE)
- Navigates to: /admin/devices/{device_id}/terminal
- See section 4.4 for full terminal implementation

**Reboot Device**:
- Button: "Reboot"
- Modal confirmation: "Device will reboot and may be offline for 2 minutes."
- Calls: POST /admin/devices/{device_id}/reboot
- Backend sends MQTT command: admin/scanner/{device_id} { "action": "reboot" }
- UI shows spinner until device comes back online

**Push OTA**:
- Button: "Update to v2.3.1" (if new version available)
- Calls: POST /admin/ota/push with { device_id, version }
- See section 4.6 for OTA flow

---

### 4.4 Web Terminal

**Route**: `/admin/devices/{device_id}/terminal`

**The most critical section. Full technical detail below.**

#### 4.4.1 Architecture

```
┌─────────────────────────────────────────────┐
│       Browser (Tab)                         │
│       Xterm.js Terminal UI                  │
│       Renders SSH output, captures input    │
└─────────────┬───────────────────────────────┘
              │
              │ WebSocket
              │ wss://sigmatic-asc.tech/admin/ws/terminal/{device_id}
              │ JWT in query param: ?token={jwt}
              │
┌─────────────▼───────────────────────────────┐
│       DGX Backend (FastAPI)                 │
│       WebSocket Handler                     │
│       asyncssh Client Manager               │
└─────────────┬───────────────────────────────┘
              │
              │ SSH (Port 22)
              │ Via Tailscale VPN
              │ 100.x.x.x:22
              │
┌─────────────▼───────────────────────────────┐
│       Pi Device (Tailscale Enabled)         │
│       SSH Server (sshd)                     │
│       User: pi                              │
│       Auth: Tailscale ephemeral key         │
└─────────────────────────────────────────────┘
```

#### 4.4.2 Flow Diagram

1. Admin clicks "Open Terminal" on device detail page
2. Frontend navigates to `/admin/devices/{device_id}/terminal`
3. Frontend initializes Xterm.js terminal UI
4. Frontend creates WebSocket: `new WebSocket("wss://sigmatic-asc.tech/admin/ws/terminal/{device_id}?token={jwt}")`
5. Backend WebSocket handler receives connection:
   - Extracts JWT from query param
   - Verifies JWT signature (checks exp, is_superadmin or tenant ownership)
   - Looks up device_id in shared.devices
   - Checks device.debug_enabled=TRUE and device.tailscale_ip is not null
   - If all OK, accepts WebSocket connection
6. Backend initiates asyncssh connection to device.tailscale_ip:22
   - asyncssh auto-uses system SSH keys (or Tailscale-provided)
   - Connects as user "pi"
   - Opens interactive shell (PTY mode)
7. Backend pipes are set up:
   - stdin (WebSocket input) -> SSH stdin
   - SSH stdout -> WebSocket output
   - Terminal resize events -> SSH PTY resize
8. Admin types in Xterm.js -> keystroke sent as WebSocket JSON -> SSH stdin
9. SSH output -> WebSocket JSON -> Xterm.js renders
10. Admin closes tab -> WebSocket close frame -> SSH connection closes

#### 4.4.3 WebSocket Message Protocol

**Frontend -> Backend (input)**:
```json
{
  "type": "input",
  "data": "ls -la\n"
}
```

**Frontend -> Backend (resize)**:
```json
{
  "type": "resize",
  "cols": 120,
  "rows": 40
}
```

**Backend -> Frontend (output)**:
```json
{
  "type": "output",
  "data": "pi@raspberrypi:~ $ ls -la\ntotal 40\ndrwxr-xr-x 5 pi pi 4096 May 17 10:00 .\n..."
}
```

**Backend -> Frontend (connection status)**:
```json
{
  "type": "status",
  "status": "connected|disconnected|error",
  "message": "Connected to 100.x.x.x"
}
```

#### 4.4.4 Backend Implementation (Python FastAPI)

**File**: `backend/asset_api/routers/admin/terminal.py`

```python
import asyncio
import json
import logging
from typing import Optional

import asyncssh
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.auth import decode_token
from app.models import Device
from app.db import get_async_session

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/admin/ws", tags=["admin-terminal"])

# Global connection manager to track active terminals
class TerminalConnectionManager:
    def __init__(self):
        self.active_connections: dict[str, dict] = {}
    
    async def connect(self, device_id: str, websocket: WebSocket, user_id: str):
        await websocket.accept()
        self.active_connections[device_id] = {
            "websocket": websocket,
            "user_id": user_id,
            "connected_at": None,
            "ssh_process": None
        }
    
    def disconnect(self, device_id: str):
        if device_id in self.active_connections:
            del self.active_connections[device_id]
    
    async def is_active(self, device_id: str) -> bool:
        return device_id in self.active_connections

manager = TerminalConnectionManager()

@router.websocket("/terminal/{device_id}")
async def websocket_terminal(
    websocket: WebSocket,
    device_id: str,
    token: str = Query(...)
):
    """
    WebSocket endpoint for interactive terminal to remote Pi device.
    
    Flow:
    1. Verify JWT token (superadmin or tenant owner)
    2. Look up device Tailscale IP and debug status
    3. Connect to Pi via asyncssh over Tailscale
    4. Pipe WebSocket <-> SSH stdin/stdout/stderr
    5. Handle resize events
    """
    
    # Verify token and get claims
    try:
        claims = decode_token(token)
    except Exception as e:
        await websocket.close(code=4001, reason="Invalid token")
        logger.warning(f"Terminal WS: Invalid token for device {device_id}: {e}")
        return
    
    user_id = claims.get("user_id")
    is_superadmin = claims.get("is_superadmin", False)
    tenant_id = claims.get("tenant_id")
    
    # Get DB session
    async with get_async_session() as session:
        # Look up device
        stmt = select(Device).where(Device.device_id == device_id)
        result = await session.execute(stmt)
        device = result.scalar_one_or_none()
        
        if not device:
            await websocket.close(code=4002, reason="Device not found")
            logger.warning(f"Terminal WS: Device not found: {device_id}")
            return
        
        # Check authorization
        if not is_superadmin and device.tenant_id != tenant_id:
            await websocket.close(code=4003, reason="Not authorized for this device")
            logger.warning(f"Terminal WS: Unauthorized access to {device_id} by {user_id}")
            return
        
        # Check debug is enabled and Tailscale IP available
        if not device.debug_enabled or not device.tailscale_ip:
            await websocket.close(code=4004, reason="Device debug mode not enabled")
            logger.warning(f"Terminal WS: Debug not enabled for {device_id}")
            return
        
        # Accept WebSocket connection
        await websocket.accept()
        
        # Audit log
        logger.info(f"Terminal session started: user={user_id}, device={device_id}, "
                   f"ip={device.tailscale_ip}, tenant={device.tenant_id}")
        
        # Connect to Pi via asyncssh
        try:
            async with asyncssh.connect(
                device.tailscale_ip,
                username="pi",
                known_hosts=None,
                client_keys=[],  # Use system SSH keys or Tailscale auth
                connect_timeout=10
            ) as conn:
                # Open interactive shell with PTY
                async with conn.create_process(
                    term_type="xterm-256color",
                    term_size=(120, 40)
                ) as process:
                    
                    await websocket.send_json({
                        "type": "status",
                        "status": "connected",
                        "message": f"Connected to {device.tailscale_ip}"
                    })
                    
                    # Create bidirectional pipe tasks
                    async def websocket_to_ssh():
                        """Forward WebSocket messages to SSH stdin."""
                        try:
                            async for message in websocket.iter_text():
                                data = json.loads(message)
                                
                                if data["type"] == "input":
                                    # User typed something
                                    process.stdin.write(data["data"])
                                
                                elif data["type"] == "resize":
                                    # Terminal resized
                                    cols = data.get("cols", 120)
                                    rows = data.get("rows", 40)
                                    process.change_terminal_size(cols, rows)
                        
                        except WebSocketDisconnect:
                            pass
                        except json.JSONDecodeError:
                            logger.warning(f"Terminal WS: Invalid JSON from client")
                        except Exception as e:
                            logger.error(f"Terminal WS input error: {e}")
                        finally:
                            process.stdin.close()
                    
                    async def ssh_to_websocket():
                        """Forward SSH stdout to WebSocket."""
                        try:
                            async for line in process.stdout:
                                await websocket.send_json({
                                    "type": "output",
                                    "data": line
                                })
                        except Exception as e:
                            logger.error(f"Terminal WS output error: {e}")
                        finally:
                            try:
                                await websocket.close()
                            except:
                                pass
                    
                    # Run both tasks concurrently
                    await asyncio.gather(
                        websocket_to_ssh(),
                        ssh_to_websocket()
                    )
        
        except asyncssh.PermissionDenied:
            await websocket.send_json({
                "type": "error",
                "message": "SSH authentication failed. Check Tailscale key."
            })
            await websocket.close(code=4005, reason="SSH auth failed")
            logger.error(f"Terminal WS: SSH auth failed for {device_id}")
        
        except asyncssh.ConnectError as e:
            await websocket.send_json({
                "type": "error",
                "message": f"SSH connection failed: {str(e)}"
            })
            await websocket.close(code=4006, reason="SSH connection failed")
            logger.error(f"Terminal WS: SSH connect error for {device_id}: {e}")
        
        except Exception as e:
            await websocket.send_json({
                "type": "error",
                "message": f"Terminal error: {str(e)}"
            })
            await websocket.close(code=4007, reason="Internal error")
            logger.error(f"Terminal WS: Unexpected error for {device_id}: {e}")
        
        finally:
            manager.disconnect(device_id)
            logger.info(f"Terminal session closed: device={device_id}")
```

#### 4.4.5 Frontend Implementation (React + Xterm.js)

**File**: `beam/pages/admin/devices/[id]/terminal.tsx`

```typescript
import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/router'
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import 'xterm/css/xterm.css'

interface TerminalProps {
  deviceId: string
  token: string
}

export default function TerminalPage() {
  const router = useRouter()
  const { id: deviceId } = router.query
  const terminalRef = useRef<HTMLDivElement>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const termRef = useRef<Terminal | null>(null)
  const fitAddonRef = useRef<FitAddon | null>(null)
  const [connectionStatus, setConnectionStatus] = useState<'connecting' | 'connected' | 'disconnected'>('connecting')
  const [statusMessage, setStatusMessage] = useState('Connecting...')

  useEffect(() => {
    if (!deviceId || typeof deviceId !== 'string') return

    // Initialize Xterm.js
    const term = new Terminal({
      cursorBlink: true,
      cursorStyle: 'block',
      fontFamily: 'Menlo, Monaco, Consolas, monospace',
      fontSize: 14,
      theme: {
        background: '#010D0E',
        foreground: '#F1F5F9',
        cursor: '#F1F5F9'
      },
      scrollback: 1000,
      rows: 40,
      cols: 120
    })

    const fitAddon = new FitAddon()
    term.loadAddon(fitAddon)

    if (terminalRef.current) {
      term.open(terminalRef.current)
      fitAddon.fit()
      termRef.current = term
      fitAddonRef.current = fitAddon
    }

    // Get JWT token from localStorage or session
    const token = localStorage.getItem('token') // Adjust based on your auth setup

    if (!token) {
      term.write('Error: No authentication token found\r\n')
      setConnectionStatus('disconnected')
      setStatusMessage('Authentication failed')
      return
    }

    // Connect to WebSocket
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//sigmatic-asc.tech/admin/ws/terminal/${deviceId}?token=${token}`

    const ws = new WebSocket(wsUrl)
    wsRef.current = ws

    ws.onopen = () => {
      console.log('WebSocket connected')
      term.write('Establishing SSH connection...\r\n')
    }

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)

        if (msg.type === 'status') {
          if (msg.status === 'connected') {
            setConnectionStatus('connected')
            setStatusMessage(`Connected to ${msg.message}`)
            term.write(`\r\n${msg.message}\r\n`)
          } else if (msg.status === 'disconnected') {
            setConnectionStatus('disconnected')
            setStatusMessage('Disconnected')
            term.write('\r\nConnection closed\r\n')
          }
        }

        if (msg.type === 'output') {
          term.write(msg.data)
        }

        if (msg.type === 'error') {
          term.write(`\r\nError: ${msg.message}\r\n`)
        }
      } catch (e) {
        console.error('Failed to parse WebSocket message:', e)
      }
    }

    ws.onerror = (event) => {
      console.error('WebSocket error:', event)
      term.write('\r\nWebSocket error\r\n')
      setConnectionStatus('disconnected')
      setStatusMessage('Connection error')
    }

    ws.onclose = () => {
      console.log('WebSocket closed')
      setConnectionStatus('disconnected')
      setStatusMessage('Connection closed')
      term.write('\r\nConnection closed\r\n')
    }

    // Capture terminal input
    term.onData((data) => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'input', data }))
      }
    })

    // Handle terminal resize
    term.onResize(({ cols, rows }) => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'resize', cols, rows }))
      }
    })

    // Re-fit on window resize
    const handleResize = () => {
      if (fitAddonRef.current && terminalRef.current) {
        fitAddonRef.current.fit()
      }
    }

    window.addEventListener('resize', handleResize)

    // Cleanup
    return () => {
      window.removeEventListener('resize', handleResize)
      term.dispose()
      if (ws.readyState === WebSocket.OPEN) {
        ws.close()
      }
    }
  }, [deviceId])

  return (
    <div className="flex flex-col h-screen bg-slate-900">
      <div className="flex items-center justify-between p-4 bg-slate-800 border-b border-slate-700">
        <h1 className="text-lg font-semibold text-slate-100">
          Terminal: {router.query.id}
        </h1>
        <div className="flex items-center gap-3">
          <div className={`h-3 w-3 rounded-full ${
            connectionStatus === 'connected' ? 'bg-green-500' :
            connectionStatus === 'connecting' ? 'bg-yellow-500' :
            'bg-red-500'
          }`} />
          <span className="text-sm text-slate-300">{statusMessage}</span>
        </div>
      </div>
      <div
        ref={terminalRef}
        className="flex-1 overflow-hidden"
        style={{ margin: '1rem' }}
      />
    </div>
  )
}
```

#### 4.4.6 Audit Logging for Terminal Sessions

Every terminal session must be logged for compliance. Store in a new table:

```sql
CREATE TABLE shared.terminal_sessions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES shared.users(user_id),
    device_id   TEXT NOT NULL REFERENCES shared.devices(device_id),
    tenant_id   TEXT NOT NULL,
    started_at  TIMESTAMPTZ DEFAULT NOW(),
    ended_at    TIMESTAMPTZ,
    duration_sec INT,
    status      TEXT,  -- 'connected' | 'auth_failed' | 'timeout' | 'closed'
    remote_ip   TEXT,  -- IP of browser
    notes       TEXT
);
```

Log at session start and end:

```python
# At session start
await log_terminal_session(
    user_id=user_id,
    device_id=device_id,
    action="started",
    remote_ip=websocket.client.host
)

# At session end
await log_terminal_session(
    user_id=user_id,
    device_id=device_id,
    action="ended",
    duration_sec=session_duration
)
```

---

### 4.5 Log Viewer

**Route**: `/admin/logs`

**Purpose**: Centralized log aggregation and search.

#### 4.5.1 Log Source

Logs come from two sources:

1. **Device Logs** (Loki): Stored by scanner scripts on the device or pushed to DGX Loki
   - Endpoint: `http://localhost:3100` (DGX internal)
   - Query language: LogQL
   - Retention: 30 days

2. **Broker Logs** (Moquette): MQTT broker activity
   - Stored as text file on DGX: `/var/log/mosquitto/mosquitto.log` (if using Mosquitto)
   - Or if using Moquette, stored in memory/disk per config

#### 4.5.2 Log Viewer UI

**Route**: `/admin/logs`

- **Tabs**:
  1. Device Logs (Loki)
  2. Broker Logs (text tail)
  3. Crash Reports

- **Device Logs Tab**:
  - Filters:
    - By device_id (multi-select)
    - By log level (DEBUG/INFO/WARN/ERROR)
    - By date range (date picker)
    - By search text
  - Display:
    - Table: timestamp, device, level, message (truncated, expandable)
    - Color-coded by level (red for ERROR, yellow for WARN, etc.)
  - Actions:
    - Click row to expand full message
    - Export as JSON or CSV
    - Real-time tail (toggle checkbox)

- **Broker Logs Tab**:
  - Text area with monospace font
  - Last 1000 lines from Mosquitto/Moquette
  - Tail mode (auto-scroll to bottom)
  - Search within logs

- **Crash Reports Tab**:
  - Table: timestamp, device, crash_reason, stack_trace (snippet), actions
  - Click to expand full stack trace
  - Shows last 100 log lines before crash
  - Export as .txt or .json

#### 4.5.3 Backend Endpoints

```python
GET /admin/logs/query?device_id=AA:BB:CC&level=ERROR&start=2025-05-17T00:00:00Z&end=2025-05-18T00:00:00Z&search=timeout
# Returns: { "logs": [ { "timestamp", "device", "level", "message" }, ... ], "total": 42 }

GET /admin/logs/tail?lines=100
# Returns: { "logs": [ ... ], "lines": 100, "updated_at": "2025-05-17T10:15:30Z" }

GET /admin/logs/crashes
# Returns: { "crashes": [ { "timestamp", "device_id", "reason", "stack_trace", "context_logs": [...] } ] }
```

**Implementation via Loki HTTP API**:

```python
@router.get("/admin/logs/query")
async def get_logs(
    device_id: Optional[str] = None,
    level: Optional[str] = None,
    start: str = Query(...),  # ISO8601
    end: str = Query(...),
    search: Optional[str] = None,
    limit: int = 1000
):
    """Query logs from Loki."""
    
    # Build LogQL query
    # Example: {device_id="AA:BB:CC"} | level="ERROR" | "timeout"
    
    query = '{device_id=~".+"}'
    if device_id:
        query = f'{{device_id="{device_id}"}}'
    if level:
        query += f' | level="{level}"'
    if search:
        query += f' | "{search}"'
    
    # Query Loki
    response = await http_client.get(
        f"http://localhost:3100/loki/api/v1/query_range",
        params={
            "query": query,
            "start": start,
            "end": end,
            "limit": limit
        }
    )
    
    return response.json()
```

---

### 4.6 OTA Management

**Route**: `/admin/ota`

**Purpose**: Manage firmware releases and push updates to devices with canary rollout.

#### 4.6.1 Release Management

**Route**: `/admin/ota/releases`

- **List Releases**:
  - Table: version, device_type (pi/esp32), release_date, rollout_%, status, actions
  - Status: draft, canary, full, rolled_back
  - Sort by: release_date (newest first)
  - Filter by: device_type

- **Create Release**:
  - Form:
    - Version (e.g., "2.3.1")
    - Device type: pi / esp32 / both
    - Upload binary (file picker)
    - Calculate SHA256 checksum automatically
    - Release notes (markdown)
  - After upload:
    - Store in shared.releases table
    - Show checksum for verification
    - Set initial rollout_pct=5% (canary)

- **Release Detail**:
  - Show all metadata
  - Rollout progress:
    - Canary phase: "5% deployed, 0 errors, 0 crashes"
    - Full phase: "100% deployed, 5 errors, 1 crash"
  - Chart: deployment progress over time
  - Device status table: device_id, version, status (pending/downloading/installing/installed/failed/rolled_back), progress_%, error (if any)
  - Actions:
    - Approve canary -> increase to 25%
    - Approve full -> increase to 100%
    - Emergency full -> jump to 100% immediately
    - Rollback -> push previous version to all devices

#### 4.6.2 OTA Push Flow

**Route**: POST `/admin/ota/push`

```json
{
  "version": "2.3.1",
  "target": "all" | "tenant_id" | ["device_id_1", "device_id_2"],
  "rollout_pct": 5,
  "is_emergency": false
}
```

**Backend Logic**:

1. Load release from shared.releases (verify SHA256, URL)
2. Calculate target devices based on target parameter
3. For each device:
   - Create entry in shared.ota_status (status=pending)
   - Send MQTT command: admin/scanner/{device_id} { "action": "ota_download", "url": "...", "sha256": "..." }
   - Device downloads, verifies checksum, installs
   - Device reports status back via MQTT: ota_status/{device_id} { "status": "installing", "progress": 45 }
4. Update shared.ota_status with each progress report
5. Track rollout_pct: if 5% = 5 devices, and 1 device fails, then mark release as "canary_stalled" until manual approval

#### 4.6.3 OTA Database Tables

```sql
CREATE TABLE shared.releases (
    id          SERIAL PRIMARY KEY,
    version     TEXT NOT NULL UNIQUE,
    device_type TEXT NOT NULL,     -- 'pi' | 'esp32' | 'both'
    url         TEXT NOT NULL,     -- S3 URL or DGX file server
    sha256      TEXT NOT NULL,
    rollout_pct INT DEFAULT 5,     -- current canary percentage
    notes       TEXT,
    status      TEXT DEFAULT 'canary',  -- 'draft' | 'canary' | 'full' | 'rolled_back'
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    created_by  TEXT NOT NULL REFERENCES shared.users(user_id)
);

CREATE TABLE shared.ota_status (
    id          BIGSERIAL PRIMARY KEY,
    device_id   TEXT NOT NULL REFERENCES shared.devices(device_id),
    version     TEXT NOT NULL,
    status      TEXT NOT NULL,     -- 'pending' | 'downloading' | 'installing' | 'installed' | 'failed' | 'rolled_back'
    progress    INT DEFAULT 0,     -- 0-100
    error_msg   TEXT,
    attempt_count INT DEFAULT 1,
    last_error_at TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ DEFAULT NOW(),
    FOREIGN KEY (version) REFERENCES shared.releases(version)
);
```

---

### 4.7 Zone and Asset Management

**Route**: `/admin/config`

**Purpose**: Same functionality as Android Configurator but web-based.

#### 4.7.1 Zone Management

**Route**: `/admin/config/zones`

- **Zone List**:
  - Cards or table: zone_id, name, location, scanner_count, asset_count
  - Create new zone (form: name, location, description)
  - Edit zone (update metadata)
  - Delete zone (with confirmation)

- **Zone Detail**:
  - Edit metadata
  - Drag-and-drop assign scanners to zone
  - List assets currently in zone (from movement_log)
  - View zone logic (Kalman filter settings, dwell time, hysteresis)

#### 4.7.2 Asset Management

**Route**: `/admin/config/assets`

- **Asset List**:
  - Table: beacon_mac, name, asset_type, current_zone, battery_%, last_seen
  - Search by MAC or name
  - Filter by type

- **Asset Detail**:
  - Metadata: MAC, name, type, battery level, firmware
  - Movement history (last 20 zone changes with timestamps)
  - Current RSSI trend (last 24h chart)

- **Import/Export**:
  - Download template CSV
  - Bulk import: MAC,Name,Type
  - Stores in tenant schema table mst_asset

#### 4.7.3 Floor Plan (Future)

- Upload floor plan image (PNG/JPG)
- Define zone boundaries (draw rectangles on image)
- Place scanner locations
- Live asset positions overlay on map

---

### 4.8 Billing and Plan Management (Future)

**Route**: `/admin/billing`

**Purpose**: Manage SaaS tiers and usage tracking.

#### 4.8.1 Plan Tiers

| Tier | Device Limit | API Calls/Month | Price | Features |
|------|--------------|-----------------|-------|----------|
| Demo | 2 | 10K | Free | Standalone, no dashboard |
| Starter | 10 | 100K | $99 | Dashboard, basic reporting |
| Pro | 50 | 1M | $299 | Advanced analytics, OTA, API access |
| Enterprise | Unlimited | Unlimited | Custom | Dedicated master, SLA, support |

#### 4.8.2 Usage Tracking

- Count MQTT messages per tenant
- Count API calls per tenant (via rate limiter)
- Store in shared.usage_log table
- Calculate overage charges

#### 4.8.3 UI

- Tenant plan detail page
- Current usage vs limit
- Upgrade/downgrade buttons
- Invoice history

---

## 5. API Additions Needed on DGX

### 5.1 Admin Tenant Endpoints

```
GET    /admin/tenants
       List all tenants (superadmin only)
       Response: { "tenants": [ { id, name, plan, created_at, device_count, user_count }, ... ] }

GET    /admin/tenants/{tenant_id}
       Get single tenant detail
       Response: { "tenant": { id, name, plan, schema_name, created_at, ... } }

POST   /admin/tenants
       Create new tenant
       Request: { "name", "admin_email", "admin_password", "plan" }
       Response: { "tenant_id", "api_key", "schema_name" }

PUT    /admin/tenants/{tenant_id}
       Update tenant metadata
       Request: { "name", "plan", "master_tier" }

DELETE /admin/tenants/{tenant_id}
       Delete tenant (DROP SCHEMA CASCADE)
       Response: { "status": "deleted" }
```

### 5.2 Admin Device Endpoints

```
GET    /admin/devices
       List all devices (superadmin sees all, tenant admin sees only their tenant)
       Query params: tenant_id, status (online/offline), type (pi/esp32)
       Response: { "devices": [ { id, name, tenant_id, type, online_status, ... }, ... ] }

GET    /admin/devices/{device_id}
       Get device detail + health metrics
       Response: { "device": { id, name, tenant_id, firmware, last_heartbeat, cpu%, ram%, ... } }

GET    /admin/devices/{device_id}/health
       Get health history (from Loki or time-series DB)
       Query params: days=7
       Response: { "history": [ { timestamp, cpu, ram, temp, uptime } ] }

POST   /admin/devices/{device_id}/ssh/enable
       Enable debug mode (ephemeral Tailscale key)
       Response: { "status": "enabled", "tailscale_ip", "expires_at" }

DELETE /admin/devices/{device_id}/ssh/enable
       Disable debug mode

GET    /admin/devices/{device_id}/ssh/status
       Check if debug enabled + get Tailscale IP

POST   /admin/devices/{device_id}/reboot
       Send reboot command via MQTT
       Response: { "status": "reboot_requested" }

WS     /admin/ws/terminal/{device_id}?token={jwt}
       WebSocket for interactive terminal (see section 4.4)
```

### 5.3 Admin Log Endpoints

```
GET    /admin/logs/query
       Query device logs from Loki
       Query params: device_id, level, start, end, search, limit
       Response: { "logs": [ ... ], "total": 42 }

GET    /admin/logs/tail
       Get last N lines of broker logs
       Query params: lines=100

GET    /admin/logs/crashes
       Get crash reports with context logs
       Response: { "crashes": [ { timestamp, device_id, reason, stack_trace, context_logs } ] }
```

### 5.4 Admin OTA Endpoints

```
GET    /admin/ota/releases
       List all firmware releases
       Query params: device_type
       Response: { "releases": [ { id, version, device_type, rollout_pct, status, ... } ] }

POST   /admin/ota/releases
       Create new release
       Request: { "version", "device_type", "url", "sha256", "notes" }
       Response: { "release_id", "version" }

POST   /admin/ota/push
       Push OTA to devices
       Request: { "version", "target", "rollout_pct", "is_emergency" }
       Response: { "status": "pushing", "affected_devices": 42 }

GET    /admin/ota/status
       Get per-device OTA status
       Query params: version
       Response: { "devices": [ { device_id, status, progress, error } ] }

POST   /admin/ota/rollback
       Rollback to previous version
       Request: { "version" }
```

### 5.5 Admin System Endpoints

```
GET    /admin/system/health
       System-wide health
       Response: { "dgx_cpu_pct", "dgx_ram_pct", "mqtt_connections", "postgres_connections", "uptime_days" }

GET    /admin/audit/terminal-sessions
       Audit log of terminal sessions
       Query params: user_id, device_id, start_date, end_date
       Response: { "sessions": [ { user_id, device_id, started_at, ended_at, duration_sec, status } ] }
```

### 5.6 Database Tables

```sql
-- Shared schema additions

CREATE TABLE shared.devices (
    device_id       TEXT PRIMARY KEY,
    tenant_id       TEXT NOT NULL REFERENCES shared.tenants(tenant_id),
    name            TEXT,
    type            TEXT NOT NULL,  -- 'pi' | 'esp32'
    tailscale_ip    TEXT,           -- null when debug not active
    debug_enabled   BOOLEAN DEFAULT FALSE,
    debug_enabled_at TIMESTAMPTZ,
    last_heartbeat  TIMESTAMPTZ,
    firmware_version TEXT,
    cpu_pct         FLOAT,
    ram_pct         FLOAT,
    temperature_c   FLOAT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    FOREIGN KEY (tenant_id) REFERENCES shared.tenants(tenant_id)
);

CREATE TABLE shared.releases (
    id              SERIAL PRIMARY KEY,
    version         TEXT NOT NULL UNIQUE,
    device_type     TEXT NOT NULL,  -- 'pi' | 'esp32'
    url             TEXT NOT NULL,
    sha256          TEXT NOT NULL,
    rollout_pct     INT DEFAULT 5,
    notes           TEXT,
    status          TEXT DEFAULT 'canary',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      TEXT NOT NULL REFERENCES shared.users(user_id)
);

CREATE TABLE shared.ota_status (
    id              BIGSERIAL PRIMARY KEY,
    device_id       TEXT NOT NULL REFERENCES shared.devices(device_id),
    version         TEXT NOT NULL,
    status          TEXT NOT NULL,  -- 'pending' | 'downloading' | 'installing' | 'installed' | 'failed'
    progress        INT DEFAULT 0,
    error_msg       TEXT,
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    FOREIGN KEY (version) REFERENCES shared.releases(version)
);

CREATE TABLE shared.terminal_sessions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         TEXT NOT NULL REFERENCES shared.users(user_id),
    device_id       TEXT NOT NULL REFERENCES shared.devices(device_id),
    started_at      TIMESTAMPTZ DEFAULT NOW(),
    ended_at        TIMESTAMPTZ,
    duration_sec    INT,
    status          TEXT,  -- 'connected' | 'auth_failed' | 'closed'
    remote_ip       TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Indices
CREATE INDEX idx_devices_tenant ON shared.devices(tenant_id);
CREATE INDEX idx_devices_debug ON shared.devices(debug_enabled);
CREATE INDEX idx_ota_device ON shared.ota_status(device_id);
CREATE INDEX idx_ota_version ON shared.ota_status(version);
CREATE INDEX idx_terminal_user ON shared.terminal_sessions(user_id);
CREATE INDEX idx_terminal_device ON shared.terminal_sessions(device_id);
```

---

## 6. Implementation Phases

### Phase 1: Foundation (Week 1-2)

**Goal**: Auth, tenant management, basic device list

- Implement /admin/* route protection (superadmin middleware)
- Add is_superadmin field to shared.users
- Create /admin/tenants endpoints (GET list, GET detail, POST create, DELETE)
- Create shared.devices table
- Build Tenant List UI page
- Build Device List UI page (read-only, no terminal yet)
- Unit tests: auth middleware, tenant CRUD

**Deliverable**: Superadmin can log in to /admin, see all tenants, see all devices

### Phase 2: Device Management + Terminal (Week 3-4)

**Goal**: Device detail view, SSH enable/disable, web terminal

- Create GET /admin/devices/{device_id} endpoint
- Implement Tailscale SSH integration (auth key generation)
- Create POST /admin/devices/{device_id}/ssh/enable and DELETE endpoints
- Build Device Detail UI
- Implement WebSocket terminal endpoint
- Build Xterm.js terminal frontend
- Terminal session audit logging
- Integration tests: SSH connect to test Pi

**Deliverable**: Superadmin can enable debug on a device and open an interactive terminal

### Phase 3: OTA Management (Week 5-6)

**Goal**: Release management, OTA push, canary rollout

- Create shared.releases and shared.ota_status tables
- Implement GET /admin/ota/releases endpoint
- Implement POST /admin/ota/push endpoint (canary logic)
- Implement GET /admin/ota/status endpoint
- Build Release List UI
- Build Release Detail UI (with progress chart)
- Build OTA Push UI (with target selector and emergency override)
- Integration tests: push OTA to test Pi, verify download and install

**Deliverable**: Superadmin can push firmware updates with canary rollout

### Phase 4: Logs and Polish (Week 7+)

**Goal**: Log viewer, crash reporting, zone/asset UI

- Connect to Loki for log querying
- Build Log Viewer UI (filter, search, export)
- Build Crash Report UI
- Build Zone Management UI (from Android Configurator pattern)
- Build Asset Management UI
- Performance testing: terminal latency, log query performance
- Security audit: JWT, WebSocket auth, Tailscale keys

**Deliverable**: Fully functional admin panel with logging and debugging

---

## 7. Security Considerations

### 7.1 Authentication and Authorization

- All /admin/* routes require valid JWT token
- Verify is_superadmin=TRUE or tenant ownership for each request
- Middleware pattern:

```python
@router.get("/admin/devices")
async def get_devices(
    current_user: dict = Depends(require_superadmin),
    db: AsyncSession = Depends(get_async_session)
):
    # current_user is guaranteed superadmin
    ...
```

- WebSocket auth via JWT query param (standard pattern for browser security)

### 7.2 Terminal Session Auditing

- Log every terminal session: user_id, device_id, start_at, end_at, duration, status
- Store in shared.terminal_sessions table
- Export audit reports for compliance

### 7.3 Tailscale SSH Security

- Ephemeral auth keys only (1 hour TTL)
- Auto-expire keys after 1 hour
- Track enabled_at, auto-disable if expired
- Rate limit SSH enable (max 5 enables per device per hour)

```python
# Rate limit example
RATE_LIMIT_ENABLE_SSH = 5  # per hour
last_enables = await redis.get(f"ssh_enable:{device_id}:count")
if last_enables >= RATE_LIMIT_ENABLE_SSH:
    raise HTTPException(status_code=429, detail="Rate limited")
```

### 7.4 OTA Security

- Verify SHA256 checksum before applying update
- Require manual approval before full rollout (no auto-escalation)
- Implement rollback: keep previous 2 versions available
- Sign binaries with RSA-4096 (future)
- Report any install failures with error logs

### 7.5 RBAC

- Tenant admin cannot see other tenants' devices
- Tenant admin cannot enable OTA for other tenants
- Superadmin can do everything
- Implement role check on every API endpoint

### 7.6 Rate Limiting

Apply to sensitive endpoints:

```python
from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)

@router.post("/admin/devices/{device_id}/ssh/enable")
@limiter.limit("5/hour")
async def enable_ssh(...):
    ...

@router.post("/admin/ota/push")
@limiter.limit("10/day")
async def push_ota(...):
    ...
```

### 7.7 Input Validation

- Validate all input strings (no SQL injection)
- Use SQLAlchemy parameterized queries (default behavior)
- Validate device_id format (MAC address regex)
- Validate version format (semantic versioning regex)

---

## 8. Testing Strategy

### Unit Tests

- Auth middleware (superadmin, tenant owner checks)
- OTA canary logic (rollout percentage calculation)
- SHA256 verification
- Terminal WebSocket message parsing

### Integration Tests

- Create test tenant and devices
- Enable SSH on test device
- Open terminal connection, send command, verify output
- Push OTA to test device, verify status updates
- Query logs, verify filter/search

### Performance Tests

- Terminal latency (target: < 100ms for keystroke -> display)
- OTA push to 1000 devices (verify MQTT queue doesn't overflow)
- Log query performance (Loki query time < 5s)

### Security Tests

- Attempt terminal access without token (should fail)
- Attempt terminal access as other tenant (should fail)
- Attempt OTA push as non-superadmin (should fail)
- SQL injection attempts in search/filter

---

## 9. Deployment and Operations

### Deployment Checklist

- [ ] Database migration: create new tables (devices, releases, ota_status, terminal_sessions)
- [ ] Backend: add new FastAPI routers and WebSocket handlers
- [ ] Frontend: build Next.js admin pages and Xterm.js terminal component
- [ ] Add is_superadmin to shared.users for Akshat
- [ ] Configure Tailscale API key on DGX
- [ ] Test terminal connection to staging Pi
- [ ] Deploy to production with feature flag (only Akshat sees /admin route)
- [ ] Monitor logs for errors

### Operational Runbook

**Troubleshooting Terminal Connection**:
1. Check device.debug_enabled is TRUE
2. Check device.tailscale_ip is not null
3. Verify Tailscale connection on Pi: `tailscale status`
4. Test SSH manually: `ssh -v pi@{tailscale_ip}`
5. Check WebSocket URL in browser console

**Troubleshooting OTA Push**:
1. Verify release SHA256 matches binary
2. Check device is online (last_heartbeat < 5 min)
3. Monitor MQTT topic: `ble/scanner/{device_id}` for device messages
4. Check device logs for download errors

**Emergency Rollback**:
1. Go to /admin/ota/releases
2. Click "Rollback" on current version
3. Confirm (will push previous version to all devices with new version)

---

## 10. Future Enhancements

- **Floor plan mapping**: Upload building floor plan, visualize beacon positions
- **Alerts**: Email/Slack alerts on device offline, OTA failure, high temperature
- **Multi-language**: Support for other languages
- **Custom dashboards**: Allow users to build custom analytics
- **API rate limiting dashboard**: Show tenant API usage trends
- **Device groups**: Organize devices by region, customer, or type
- **Batch operations**: Bulk reboot, bulk OTA, bulk settings update
- **Two-factor auth**: TOTP for superadmin accounts

---

## 11. Success Metrics

- Superadmin can manage all tenants from a single interface
- Terminal latency < 100ms
- OTA push to 1000 devices completes in < 30 minutes
- Audit logs capture all admin actions
- Zero successful unauthorized access attempts
- 99% uptime for admin panel

---

## Conclusion

This admin panel transforms BleX from a backend-only system into a complete SaaS platform with multi-tenant support, remote device debugging, and firmware management. The web terminal via Xterm.js + asyncssh + Tailscale is the standout feature - it eliminates the need for admins to manage SSH keys and allows debugging from any browser.

Phased implementation allows validation at each stage, and the architecture is extensible for future features like floor plans and alerting.
