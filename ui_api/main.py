from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from routers import assets, scanners, history

app = FastAPI(title="ZoneTrack UI API")

# API ROUTES
app.include_router(assets.router, prefix="/api")
app.include_router(scanners.router, prefix="/api")
app.include_router(history.router, prefix="/api")

# SERVE UI (Support both stripped and unstripped prefixes)
app.mount("/beam", StaticFiles(directory="www", html=True), name="ui_beam")
app.mount("/", StaticFiles(directory="www", html=True), name="ui")
