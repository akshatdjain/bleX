from sqlalchemy import Column, Integer, ForeignKey
from database import Base

class MstZoneScanner(Base):
    __tablename__ = "mst_zone_scanner"

    id = Column(Integer, primary_key=True)
    mst_zone_id = Column(Integer, ForeignKey("mst_zone.id"), nullable=False)
    mst_scanner_id = Column(Integer, ForeignKey("mst_scanner.id"), nullable=False)
