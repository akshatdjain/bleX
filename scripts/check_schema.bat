@echo off
set PGPASSWORD=1234
echo --- mst_asset ---
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d asset_tracking -h localhost -c "\d mst_asset"
echo --- mst_zone ---
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d asset_tracking -h localhost -c "\d mst_zone"
echo --- mst_scanner ---
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d asset_tracking -h localhost -c "\d mst_scanner"
