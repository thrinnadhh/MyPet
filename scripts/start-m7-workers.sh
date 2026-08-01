#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

: "${GATEWAY_SECRET:?GATEWAY_SECRET must be set}"
: "${INTERNAL_API_SECRET:?INTERNAL_API_SECRET must be set}"

COMPOSE=(
  docker compose
  -f infra/docker-compose.yml
  -f infra/docker-compose.replicas.yml
  -f infra/docker-compose.m7.yml
)

WORKER_SERVICES=(
  provider-service
  order-service
  appointment-service
  dispatch-service
  notification-service
  review-service
  payment-service
  content-service
)

for service in "${WORKER_SERVICES[@]}"; do
  container="mypet-${service%-service}-worker"
  docker rm -f "$container" >/dev/null 2>&1 || true
  "${COMPOSE[@]}" run -d --no-deps --name "$container" \
    -e SPRING_PROFILES_ACTIVE=docker,worker \
    -e MYPET_SCHEDULING_ROLE=WORKER \
    -e WORKER_SERVER_PORT=0 \
    -e WORKER_MANAGEMENT_PORT=0 \
    "$service" >/dev/null
  echo "Started $container"
done

echo "M7 workers started. API services must be launched with infra/docker-compose.m7.yml."
