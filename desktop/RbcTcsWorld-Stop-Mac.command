#!/bin/bash
# Stops the store. Your data (database volume) is kept; add -v to the command below to delete it as well.
cd "$(dirname "$0")/.." || exit 1
docker compose --profile app down
read -r -p "Stopped. Press Enter to close"
