#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
LOG_DIR="${LOG_DIR:-$ROOT_DIR/logs/sprint3-stack}"

services=(
  "provider-service:8081"
  "catalog-service:8082"
  "discovery-service:8083"
  "order-service:8084"
  "payment-service:8090"
  "api-gateway:8080"
)

usage() {
  cat <<'EOF'
Usage: scripts/start-sprint3-stack.sh [start|stop|restart|status]

Starts the local Sprint 1-3 verification stack from the current workspace:
  - provider-service:8081
  - catalog-service:8082
  - discovery-service:8083
  - order-service:8084
  - payment-service:8090
  - api-gateway:8080

The gateway needs Supabase JWT validation settings. This script loads only the
gateway process with values from .env, so domain services keep their local DB
defaults unless you start them separately with explicit overrides.

For this local database, provider-service starts with Flyway validation disabled
because the existing schema history has a checksum mismatch for migration V2.
This does not repair or rewrite the database; it only allows local proof runs to
boot against the already-applied schema.

The start/restart commands keep this script in the foreground. Leave the
terminal open while testing, or press Ctrl-C to stop all started services.
EOF
}

pid_for_port() {
  local port="$1"
  lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true
}

wait_for_port() {
  local service="$1"
  local port="$2"
  local attempts=60

  for _ in $(seq 1 "$attempts"); do
    if [[ -n "$(pid_for_port "$port")" ]]; then
      echo "$service is listening on $port"
      return 0
    fi
    sleep 1
  done

  echo "Timed out waiting for $service on $port. See $LOG_DIR/$service.log" >&2
  return 1
}

service_pids=()

stop_started_services() {
  for entry in "${services[@]}"; do
    stop_service "${entry%%:*}" "${entry##*:}"
  done
}

stop_service() {
  local service="$1"
  local port="$2"
  local pids
  pids="$(pid_for_port "$port")"

  if [[ -z "$pids" ]]; then
    echo "$service is not running on $port"
    return 0
  fi

  echo "Stopping $service on $port: $pids"
  kill $pids

  for _ in $(seq 1 20); do
    if [[ -z "$(pid_for_port "$port")" ]]; then
      return 0
    fi
    sleep 1
  done

  echo "Force stopping $service on $port: $(pid_for_port "$port")"
  kill -9 $(pid_for_port "$port")
}

require_gateway_jwt_config() {
  if [[ ! -f "$ROOT_DIR/.env" ]]; then
    echo "Missing $ROOT_DIR/.env. Gateway requires SUPABASE_JWT_JWK_SET_URI, SUPABASE_JWT_SECRET, or ALLOW_UNSIGNED_JWT=true." >&2
    return 1
  fi

  if ! grep -Eq '^(SUPABASE_JWT_JWK_SET_URI|SUPABASE_JWT_SECRET|ALLOW_UNSIGNED_JWT)=' "$ROOT_DIR/.env"; then
    echo "Gateway JWT validation is not configured in .env." >&2
    echo "Set SUPABASE_JWT_JWK_SET_URI, SUPABASE_JWT_SECRET, or ALLOW_UNSIGNED_JWT=true for local-only development." >&2
    return 1
  fi
}

start_service() {
  local service="$1"
  local port="$2"
  local existing
  existing="$(pid_for_port "$port")"

  if [[ -n "$existing" ]]; then
    echo "$service is already running on $port: $existing"
    return 0
  fi

  mkdir -p "$LOG_DIR"
  echo "Starting $service on $port"

  if [[ "$service" == "api-gateway" ]]; then
    require_gateway_jwt_config
    (
      set -a
      # shellcheck source=/dev/null
      source "$ROOT_DIR/.env"
      set +a
      cd "$BACKEND_DIR"
      exec ./gradlew ":$service:bootRun"
    ) > "$LOG_DIR/$service.log" 2>&1 &
  elif [[ "$service" == "provider-service" ]]; then
    (
      cd "$BACKEND_DIR"
      exec ./gradlew ":$service:bootRun" --args='--spring.flyway.validate-on-migrate=false'
    ) > "$LOG_DIR/$service.log" 2>&1 &
  else
    (
      cd "$BACKEND_DIR"
      exec ./gradlew ":$service:bootRun"
    ) > "$LOG_DIR/$service.log" 2>&1 &
  fi

  local pid="$!"
  service_pids+=("$pid")
  echo "$pid" > "$LOG_DIR/$service.pid"
  wait_for_port "$service" "$port"
}

status_service() {
  local service="$1"
  local port="$2"
  local pids
  pids="$(pid_for_port "$port")"

  if [[ -n "$pids" ]]; then
    echo "$service running on $port: $pids"
  else
    echo "$service not running on $port"
  fi
}

command="${1:-start}"
case "$command" in
  start)
    trap stop_started_services INT TERM EXIT
    for entry in "${services[@]}"; do
      start_service "${entry%%:*}" "${entry##*:}"
    done
    echo "Sprint 3 stack is running. Logs are in $LOG_DIR. Press Ctrl-C to stop."
    wait "${service_pids[@]}"
    ;;
  stop)
    for entry in "${services[@]}"; do
      stop_service "${entry%%:*}" "${entry##*:}"
    done
    ;;
  restart)
    for entry in "${services[@]}"; do
      stop_service "${entry%%:*}" "${entry##*:}"
    done
    trap stop_started_services INT TERM EXIT
    for entry in "${services[@]}"; do
      start_service "${entry%%:*}" "${entry##*:}"
    done
    echo "Sprint 3 stack is running. Logs are in $LOG_DIR. Press Ctrl-C to stop."
    wait "${service_pids[@]}"
    ;;
  status)
    for entry in "${services[@]}"; do
      status_service "${entry%%:*}" "${entry##*:}"
    done
    ;;
  -h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
