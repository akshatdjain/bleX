from pydantic import BaseModel
from typing import Optional
from datetime import datetime


# ---------- ZONES ----------
class ZoneOut(BaseModel):
    id: int
    zone_name: str
    description: Optional[str]

    class Config:
        from_attributes = True


# ---------- SCANNERS ----------
class ScannerOut(BaseModel):
    id: int
    mac_id: str
    name: Optional[str]
    type: Optional[str]

    class Config:
        from_attributes = True


# ---------- LIVE ASSETS ----------
class LiveAssetOut(BaseModel):
    bluetooth_id: str
    asset_name: Optional[str]
    current_zone_id: Optional[int]
    last_seen: datetime
    deciding_rssi: Optional[float]

    class Config:
        from_attributes = True


# ---------- HISTORY ----------
class MovementLogOut(BaseModel):
    id: int
    bluetooth_id: str
    timestamp_movement: datetime
    from_zone_id: Optional[int]
    to_zone_id: Optional[int]
    deciding_rssi: Optional[float]

    class Config:
        from_attributes = True
