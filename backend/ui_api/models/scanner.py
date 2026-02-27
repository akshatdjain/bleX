from sqlalchemy import Column, Integer, Text, DateTime
from database import Base

class MstScanner(Base):
    __tablename__ = "mst_scanner"

    id = Column(Integer, primary_key=True)
    mac_id = Column(Text, nullable=False)
    name = Column(Text)
    type = Column(Text)
    created_at = Column(DateTime)
