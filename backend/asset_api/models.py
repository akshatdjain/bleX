# models.py
from sqlalchemy import Column, Integer, BigInteger, Text, DateTime, Numeric, JSON, ForeignKey
from sqlalchemy.sql import func
from database import Base

class MstZone(Base):
    __tablename__ = "mst_zone"

    id = Column(Integer, primary_key=True)
    zone_name = Column(Text, nullable=False)
    description = Column(Text)
    dimension = Column(JSON)

class MstZoneScanner(Base):
    __tablename__ = "mst_zone_scanner"

    id = Column(Integer, primary_key=True)
    mst_zone_id = Column(Integer, ForeignKey("mst_zone.id"), nullable=False)
    mst_scanner_id = Column(Integer, ForeignKey("mst_scanner.id"), nullable=False)

class MstAsset(Base):
    __tablename__ = "mst_asset"

    id = Column(Integer, primary_key=True)
    bluetooth_id = Column(Text, nullable=False, unique=True)
    current_zone_id = Column(Integer)
    asset_name = Column(Text)
    last_movement_dt = Column(DateTime(timezone=True))
    created_at = Column(DateTime(timezone=True), server_default=func.now())

class MstScanner(Base):
    __tablename__ = "mst_scanner"

    id = Column(Integer, primary_key=True)
    mac_id = Column(Text, nullable=False)
    name = Column(Text)
    type = Column(Text)
    created_at = Column(DateTime(timezone=True), server_default=func.now())


class MovementLog(Base):
    __tablename__ = "movement_log"

    id = Column(BigInteger, primary_key=True)
    bluetooth_id = Column(Text, nullable=False)
    from_zone_id = Column(Integer)
    to_zone_id = Column(Integer)
    deciding_rssi = Column(Numeric(6, 2))
    timestamp_movement = Column(DateTime(timezone=True), nullable=False)
