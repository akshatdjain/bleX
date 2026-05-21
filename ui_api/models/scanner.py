from sqlalchemy import Column, Integer, Text, DateTime
from database import Base

class MstScanner(Base):
    __tablename__ = "mst_scanner"

    id             = Column(Integer, primary_key=True)
    mac_id         = Column(Text, nullable=False)
    name           = Column(Text)           # already exists in DB
    type           = Column(Text)           # "pi" | "esp32" | "android"
    created_at     = Column(DateTime)
    last_heartbeat = Column(DateTime(timezone=True))  # added via: ALTER TABLE mst_scanner ADD COLUMN last_heartbeat TIMESTAMPTZ
