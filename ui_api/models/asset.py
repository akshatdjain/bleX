from sqlalchemy import Column, Integer, Text, DateTime, Numeric, JSON
from database import Base

class MstAsset(Base):
    __tablename__ = "mst_asset"

    id = Column(Integer, primary_key=True)
    bluetooth_id = Column(Text, nullable=False)
    asset_name = Column(Text)
    last_movement_dt = Column(DateTime)
    distance_from_scanner = Column(Numeric)
    current_zone_id = Column(Integer)
    extra = Column(JSON)
