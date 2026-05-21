# schemas.py
from pydantic import BaseModel
from typing import Literal
from typing import Optional

class MovementIn(BaseModel):
    asset_mac: str
    from_zone_id: Optional[int] = None
    to_zone_id: Optional[int] = None
    state: Literal["ZONE", "EXIT"] = "ZONE"
    deciding_rssi: Optional[float] = None
    timestamp: str

class MovementOut(BaseModel):
    ok: bool
    detail: Optional[str] = None

# ─── CRUD Input Schemas ──────────────────────────────

class ZoneIn(BaseModel):
    zone_name: str
    description: Optional[str] = None

class AssetIn(BaseModel):
    bluetooth_id: str
    asset_name: Optional[str] = None

class ScannerIn(BaseModel):
    mac_id: str
    name: Optional[str] = None
    type: Optional[str] = None  # "pi" | "esp32"

class ZoneScannerIn(BaseModel):
    scanner_id: int
