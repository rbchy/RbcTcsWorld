#!/usr/bin/env bash
# RbcTcsWorld E-Commerce - start everything and open the browser (Linux). Stop: docker compose --profile app down
set -e
cd "$(dirname "$0")/.."
docker info >/dev/null 2>&1 || { echo "Docker is not running (sudo systemctl start docker)"; exit 1; }
docker compose --profile app up -d --build --wait --wait-timeout 300
xdg-open http://localhost:5173 >/dev/null 2>&1 || echo "Open http://localhost:5173"
