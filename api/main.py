"""
BleX API — unified backend + dashboard
- /asset/api/*         Android app + Pi device endpoints
- /asset/dashboard/*   Web dashboard read endpoints (cookie auth)
- /asset/ui/*          Static web UI (React SPA)
"""

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy.exc import TimeoutError as DBTimeoutError
import os
import redis.asyncio as aioredis
from fastapi_limiter import FastAPILimiter

from routers import movement, runtime, zones, assets, scanners, health, tenants, tenants_config, auth, system, admin_users
from routers.dashboard import assets as dash_assets
from routers.dashboard import zones as dash_zones
from routers.dashboard import scanners as dash_scanners
from routers.dashboard import health as dash_health
from routers.dashboard import notifications as dash_notifications
from routers.dashboard import history as dash_history

# ── App ───────────────────────────────────────────────────────────────────────

REDIS_URL = os.getenv("REDIS_URL", "redis://redis:6379")

app = FastAPI(
    title="BleX API",
    description="BLE asset tracking — device API + web dashboard",
    version="2.0.0",
    root_path=os.getenv("APP_ROOT_PATH", ""),
)

@app.on_event("startup")
async def startup():
    r = await aioredis.from_url(REDIS_URL, encoding="utf-8", decode_responses=True)
    await FastAPILimiter.init(r)

@app.on_event("shutdown")
async def shutdown():
    await FastAPILimiter.close()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
    allow_credentials=True,
)

@app.exception_handler(DBTimeoutError)
async def db_pool_exhausted_handler(request: Request, exc: DBTimeoutError):
    return JSONResponse(
        status_code=503,
        content={"detail": "Server busy — please retry in a moment"},
    )

# ── Device / Android / Pi endpoints (/asset/api/*) ───────────────────────────

app.include_router(auth.router)
app.include_router(movement.router)
app.include_router(runtime.router)
app.include_router(zones.router)
app.include_router(assets.router)
app.include_router(scanners.router)
app.include_router(health.router)
app.include_router(tenants.router)
app.include_router(tenants_config.router)
app.include_router(system.router)
app.include_router(admin_users.router)

# ── Web dashboard endpoints (/asset/dashboard/*) ─────────────────────────────

dash_api = FastAPI(title="BleX Dashboard API")
dash_api.include_router(dash_assets.router)
dash_api.include_router(dash_zones.router)
dash_api.include_router(dash_scanners.router)
dash_api.include_router(dash_health.router)
dash_api.include_router(dash_notifications.router)
dash_api.include_router(dash_history.router)

app.mount("/dashboard", dash_api)

# ── Health ────────────────────────────────────────────────────────────────────

@app.get("/health", tags=["Health"])
async def health_check():
    return {"status": "ok"}

# ── Static UI + SPA fallback (/asset/ui/*) ────────────────────────────────────

WWW_DIR = os.path.join(os.path.dirname(__file__), "www")

if os.path.isdir(WWW_DIR):
    # Serve real static assets (JS, CSS, images) — no html=True so we handle fallback ourselves
    app.mount("/ui/assets", StaticFiles(directory=os.path.join(WWW_DIR, "assets")), name="ui-assets")

    @app.get("/ui/{full_path:path}", include_in_schema=False)
    async def spa_catch_all(full_path: str):
        """Serve index.html for all SPA routes so React Router handles navigation."""
        # Serve real file if it exists (robots.txt, favicon, etc.)
        file_path = os.path.join(WWW_DIR, full_path)
        if os.path.isfile(file_path):
            return FileResponse(file_path)
        return FileResponse(os.path.join(WWW_DIR, "index.html"))

    @app.get("/ui", include_in_schema=False)
    async def spa_root():
        return FileResponse(os.path.join(WWW_DIR, "index.html"))
