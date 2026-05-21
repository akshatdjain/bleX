@echo off
set PGPASSWORD=aksh
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h 127.0.0.1 -p 5432 -c "SELECT version();"
