# models/movement.py
from sqlalchemy import (
    Column,
    Integer,
    Text,
    DateTime,
    Numeric,
    ForeignKey,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.sql import func

from database import Base


class MovementLog(Base):
    __tablename__ = "movement_log"

    id = Column(Integer, primary_key=True)
    bluetooth_id = Column(Text, nullable=False)

    timestamp_movement = Column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now()
    )

    from_zone_id = Column(Integer, ForeignKey("mst_zone.id"))
    to_zone_id = Column(Integer, ForeignKey("mst_zone.id"))

    deciding_rssi = Column(Numeric(6, 2))
