"""
AsseTrack — Asset Zone Movement API
Entry point: uvicorn main:app --host 0.0.0.0 --port 8000
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from routers import movement, runtime, zones, assets, scanners, health, tenants, auth

app = FastAPI(
    title="AsseTrack API",
    description="Zone-based asset tracking — movement events, CRUD, runtime registration",
    version="1.0.0",
    root_path="/asset"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(movement.router)
app.include_router(runtime.router)
app.include_router(zones.router)
app.include_router(assets.router)
app.include_router(scanners.router)
app.include_router(health.router)
app.include_router(tenants.router)


@app.get("/health", tags=["Health"])
async def health_check():
    return {"status": "ok"}
