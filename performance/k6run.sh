#!/usr/bin/env bash
# Runs k6 with the local binary when installed, otherwise with the grafana/k6 image inside the compose network.
# Usage: performance/k6run.sh [k6 run options] performance/tests/<test>.js
mkdir -p performance/reports   # k6 writes the summary and HTML report here but does not create the folder
if command -v k6 >/dev/null 2>&1; then
  exec k6 run -q "$@"
fi
exec docker run --rm --network "${NETWORK:-rbctcsworld_default}" -v "$PWD:/work" -w /work \
  -e BASE_URL="${K6_BASE_URL:-http://backend:8081}" grafana/k6 run -q "$@"
