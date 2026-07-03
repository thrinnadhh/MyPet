#!/usr/bin/env sh
set -eu

docker run --rm \
  --network host \
  -e BASE_URL="${BASE_URL:-http://localhost:8080}" \
  -e AUTH_TOKEN="${AUTH_TOKEN:-}" \
  -e PROVIDER_ID="${PROVIDER_ID:?Set PROVIDER_ID}" \
  -e OFFERING_ID="${OFFERING_ID:?Set OFFERING_ID}" \
  -e SLOT_ID="${SLOT_ID:?Set SLOT_ID}" \
  -e CUSTOMER_ID="${CUSTOMER_ID:?Set CUSTOMER_ID}" \
  -e PET_ID="${PET_ID:?Set PET_ID}" \
  -v "$PWD/load-tests/k6:/scripts" \
  grafana/k6:latest run /scripts/discovery-appointments-catalog.js
