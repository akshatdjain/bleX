"""
AsseTrack — Asset Zone Movement API (Pi Master)

Entry point: uvicorn main:app --host 0.0.0.0 --port 8000
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

from routers import movement, runtime, zones, assets, scanners
from udp_discovery import start_udp_listener

# --- App Factory ---

app = FastAPI(
    title="AsseTrack API",
    description="Zone-based asset tracking — movement events, CRUD, runtime registration",
    version="1.0.0",
)

# --- CORS ---

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- Routers ---

app.include_router(movement.router)
app.include_router(runtime.router)
app.include_router(zones.router)
app.include_router(assets.router)
app.include_router(scanners.router)

# --- Pi-specific: UDP scanner discovery ---

@app.on_event("startup")
def startup_udp():
    start_udp_listener()

# --- Pi-specific: Serve dashboard UI ---

app.mount("/ui", StaticFiles(directory="/home/pi5/phase_2/ui"), name="ui")

@app.get("/")
def root():
    return FileResponse("/home/pi5/phase_2/ui/index.html")

# --- Health ---

@app.get("/health", tags=["Health"])
async def health():
    return {"status": "ok"}
