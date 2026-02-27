"""
Runtime registration — master and scanner heartbeats → site.json.
"""

from fastapi import APIRouter
from pydantic import BaseModel
from pathlib import Path
from threading import Lock
import json
import os

router = APIRouter(prefix="/api/runtime", tags=["Runtime"])

# ─── Config ──────────────────────────────────────────────────────────────────

RUNTIME_DIR = Path(os.environ.get("RUNTIME_DIR", "/home/pi5/master/runtime"))
RUNTIME_FILE = RUNTIME_DIR / "site.json"
RUNTIME_DIR.mkdir(parents=True, exist_ok=True)

_lock = Lock()


# ─── Schemas ─────────────────────────────────────────────────────────────────

class MasterRegisterIn(BaseModel):
    role: str
    mac: str
    ip: str
    timestamp: str


class ScannerRegisterIn(BaseModel):
    role: str
    mac: str
    ip: str
    scanner_type: str
    timestamp: str


# ─── Master ──────────────────────────────────────────────────────────────────

@router.post("/master")
def register_master(payload: MasterRegisterIn):
    with _lock:
        if RUNTIME_FILE.exists():
            try:
                with open(RUNTIME_FILE, "r") as f:
                    data = json.load(f)
            except Exception:
                data = {}
        else:
            data = {}

        data["master"] = {
            "mac": payload.mac,
            "ip": payload.ip,
            "timestamp": payload.timestamp
        }

        data.setdefault("scanners", [])

        with open(RUNTIME_FILE, "w") as f:
            json.dump(data, f, indent=2)

    return {
        "ok": True,
        "master_ip": payload.ip
    }


# ─── Scanner ─────────────────────────────────────────────────────────────────

@router.post("/scanner")
def register_scanner(payload: ScannerRegisterIn):
    with _lock:
        if RUNTIME_FILE.exists():
            data = json.loads(RUNTIME_FILE.read_text())
        else:
            data = {}

        # Ensure scanners section
        if not isinstance(data.get("scanners"), dict):
            data["scanners"] = {}

        scanners = data["scanners"]

        scanners[payload.mac] = {
            "type": payload.scanner_type,
            "ip": payload.ip,
            "timestamp": payload.timestamp
        }

        RUNTIME_FILE.write_text(json.dumps(data, indent=2))

        master = data.get("master")
        if not master:
            return {
                "ok": False,
                "detail": "master not registered yet"
            }

        return {
            "ok": True,
            "master_ip": master["ip"]
        }
