import asyncio
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

# Using the provided credentials
# User: postgres, IP: 100.91.37.6 (Wait, user previously said 100.125.23.80, but now says 100.91.37.6)
# DB: asset_tracking, Pass: 1234
DATABASE_URL = "postgresql+asyncpg://postgres:1234@100.91.37.6/asset_tracking"

async def check_fk():
    engine = create_async_engine(DATABASE_URL)
    async with engine.connect() as conn:
        # SQL to find all foreign keys referencing mst_zone
        query = text("""
            SELECT
                tc.table_name, 
                kcu.column_name, 
                ccu.table_name AS foreign_table_name,
                ccu.column_name AS foreign_column_name 
            FROM 
                information_schema.table_constraints AS tc 
                JOIN information_schema.key_column_usage AS kcu
                  ON tc.constraint_name = kcu.constraint_name
                  AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage AS ccu
                  ON ccu.constraint_name = tc.constraint_name
                  AND ccu.table_schema = tc.table_schema
            WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name='mst_zone';
        """)
        result = await conn.execute(query)
        print("Foreign Keys referencing 'mst_zone':")
        rows = result.fetchall()
        if not rows:
            print("None found.")
        for row in rows:
            print(f"Table: {row.table_name}, Column: {row.column_name} -> {row.foreign_table_name}.{row.foreign_column_name}")
    await engine.dispose()

if __name__ == "__main__":
    try:
        asyncio.run(check_fk())
    except Exception as e:
        print(f"Error connecting to DB: {e}")
