from fastapi import FastAPI, Request
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import asyncio
import json
import os
from datetime import datetime, timezone

from routers import assets, scanners, history, zones, health, notifications


# --------------------------------------------------------------- heartbeat --
async def _mqtt_heartbeat_listener():
    """
    Background task: subscribes to blex/heartbeat/# and updates
    mst_scanner.last_heartbeat in the database on each ping.
    Falls back silently if MQTT broker is unavailable.
    """
    import paho.mqtt.client as mqtt
    from database import AsyncSessionLocal
    from models import MstScanner
    from sqlalchemy import select, update

    BROKER = os.getenv("MQTT_BROKER", "localhost")
    PORT   = int(os.getenv("MQTT_PORT", "1883"))

    def on_message(client, userdata, msg):
        try:
            payload = json.loads(msg.payload.decode())
            scanner_mac = payload.get("scanner_id") or msg.topic.split("/")[-1]
            if not scanner_mac:
                return
            scanner_mac = scanner_mac.upper()
            # schedule DB update in the running event loop
            asyncio.get_event_loop().call_soon_threadsafe(
                lambda: asyncio.create_task(_update_heartbeat(scanner_mac))
            )
        except Exception:
            pass

    async def _update_heartbeat(mac: str):
        async with AsyncSessionLocal() as session:
            await session.execute(
                update(MstScanner)
                .where(MstScanner.mac_id == mac)
                .values(last_heartbeat=datetime.now(timezone.utc))
            )
            await session.commit()

    def _connect():
        client = mqtt.Client(
            client_id="ui-api-heartbeat",
            protocol=mqtt.MQTTv311,
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        )
        client.on_message = on_message
        client.connect(BROKER, PORT, keepalive=60)
        client.subscribe("blex/heartbeat/#")
        client.loop_start()
        return client

    try:
        client = _connect()
        while True:
            await asyncio.sleep(30)  # keep-alive
    except Exception as e:
        print(f"[HEARTBEAT] MQTT listener failed: {e} — scanner health will show 'unknown'")


# -------------------------------------------------------------------- app ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    # Start heartbeat listener in background (non-fatal if MQTT unavailable)
    task = asyncio.create_task(_mqtt_heartbeat_listener())
    yield
    task.cancel()


app = FastAPI(title="BleX UI API", version="2.0.0", lifespan=lifespan)

# CORS — allow Vite dev server and same-origin production requests
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",  # Vite dev
        "http://localhost:5174",
        "http://127.0.0.1:5173",
        "*",                      # production: same-origin (overridden per env if needed)
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# API ROUTES
app.include_router(assets.router,        prefix="/api")
app.include_router(scanners.router,      prefix="/api")
app.include_router(history.router,       prefix="/api")
app.include_router(zones.router,         prefix="/api")
app.include_router(health.router,        prefix="/api")
app.include_router(notifications.router, prefix="/api")

# SERVE UI (built Vite dist goes in www/)
# 1. First, serve the static assets folder explicitly
if os.path.exists("www/assets"):
    app.mount("/assets", StaticFiles(directory="www/assets"), name="assets")

# 2. Add an explicit route for manifest/vite icons if they exist
@app.get("/vite.svg")
async def get_vite_icon():
    if os.path.exists("www/vite.svg"):
        return FileResponse("www/vite.svg")
    return JSONResponse({"detail": "Not Found"}, status_code=404)

# 3. Explicit root route (for http://address:port/)
@app.get("/")
async def get_root():
    index_path = os.path.join("www", "index.html")
    if os.path.exists(index_path):
        return FileResponse(index_path)
    return JSONResponse(
        {"detail": f"Root index.html not found. CWD={os.getcwd()}"},
        status_code=404
    )

# 4. Catch-all: for ANY other path, return index.html (SPA logic)
@app.get("/{full_path:path}")
async def serve_spa(full_path: str):
    # If the path starts with "api", we shouldn't have hit this anyway if routes are correct,
    # but let's be safe and only serve HTML for non-API requests or the root.
    if full_path.startswith("api"):
        return JSONResponse({"detail": "Not Found"}, status_code=404)
        
    index_path = os.path.join("www", "index.html")
    if os.path.exists(index_path):
        return FileResponse(index_path)
    
    return JSONResponse(
        {"detail": f"UI path '{full_path}' not found and no index.html backup. CWD={os.getcwd()}"},
        status_code=404
    )
