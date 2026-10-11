#!/bin/bash
# RbcTcsWorld E-Commerce - double-click to start the whole store (database, backend, storefront) and open it.
# First start builds the images (a few minutes); later starts take seconds.
cd "$(dirname "$0")/.." || exit 1
URL="http://localhost:5173"

if ! docker info >/dev/null 2>&1; then
  echo "Starting Docker Desktop..."
  open -a Docker
  for i in $(seq 1 90); do docker info >/dev/null 2>&1 && break; sleep 2; done
  docker info >/dev/null 2>&1 || { echo "Docker Desktop did not start. Open it by hand, then run this launcher again."; read -r -p "Press Enter to close"; exit 1; }
fi

echo "Starting RbcTcsWorld (database, backend, storefront)..."
if docker compose --profile app up -d --build --wait --wait-timeout 300; then
  echo "Ready: $URL"
  open "$URL"
  echo "Tip: in Chrome or Edge press 'Install app' in the top bar to get a desktop window and a Dock icon."
  echo "Stop the store with RbcTcsWorld-Stop-Mac.command."
else
  echo "Start failed. Last log lines:"
  docker compose --profile app logs --no-color --tail=40
fi
read -r -p "Press Enter to close this window"
