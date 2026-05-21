#!/bin/bash
# deploy.sh — pull latest code and rebuild asset_api
set -e
cd /home/akshat/asset_tracking

echo "[deploy] Pulling latest from SigmaticAI/bleX..."
git pull origin master

echo "[deploy] Rebuilding asset_api..."
docker compose build --no-cache asset_api

echo "[deploy] Restarting asset_api..."
docker compose up -d asset_api

echo "[deploy] Done. Tailing logs..."
docker logs asset_tracking-asset_api-1 --tail 20
