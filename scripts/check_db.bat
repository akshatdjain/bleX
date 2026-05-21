@echo off
set PGPASSWORD=1234
echo --- TABLES ---
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d asset_tracking -h localhost -c "\dt"
echo --- ASSETS ---
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d asset_tracking -h localhost -c "SELECT * FROM mst_asset;"
