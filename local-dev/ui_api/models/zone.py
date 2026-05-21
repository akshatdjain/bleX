from sqlalchemy import Column, Integer, Text, JSON
from database import Base

class MstZone(Base):
    __tablename__ = "mst_zone"

    id = Column(Integer, primary_key=True)
    zone_name = Column(Text, nullable=False)
    description = Column(Text)
    dimension = Column(JSON)
