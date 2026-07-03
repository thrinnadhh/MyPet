#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN="${PYTHON_BIN:-$ROOT_DIR/.venv/bin/python}"
RUN_LIVE=0

usage() {
  cat <<'EOF'
Usage: scripts/verify-sprints-1-3.sh [--live]

Runs the complete local verification pack for Sprints 1-3.

Default checks:
  - required mobile environment variables are present
  - backend unit tests for Sprint 1-3 services
  - customer and merchant app TypeScript + lint
  - static sprint verifiers for Sprint 1, 2, and 3

With --live:
  - expects local provider/catalog/discovery/order/payment services to be running
  - runs live Sprint 1-2 provider/catalog/discovery proof
  - runs live Sprint 3 checkout/payment/stock proof

Start services in another terminal with:
  scripts/start-sprint3-stack.sh start
EOF
}

for arg in "$@"; do
  case "$arg" in
    --live)
      RUN_LIVE=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage >&2
      exit 2
      ;;
  esac
done

run() {
  echo
  echo "==> $*"
  "$@"
}

run_in_dir() {
  local dir="$1"
  shift
  echo
  echo "==> ($dir) $*"
  (cd "$dir" && "$@")
}

require_mobile_env() {
  local app_dir="$1"
  local env_file="$app_dir/.env"
  local app_name
  app_name="$(basename "$app_dir")"

  echo
  echo "==> Checking $app_name mobile environment"
  if [[ ! -f "$env_file" ]]; then
    echo "Missing $env_file" >&2
    exit 1
  fi

  local required=(
    EXPO_PUBLIC_API_BASE_URL
    EXPO_PUBLIC_SUPABASE_URL
    EXPO_PUBLIC_SUPABASE_ANON_KEY
    EXPO_PUBLIC_ALLOW_DEMO_MODE
  )

  for key in "${required[@]}"; do
    if ! grep -Eq "^${key}=.+" "$env_file"; then
      echo "$env_file is missing $key" >&2
      exit 1
    fi
  done

  if grep -Eq "^EXPO_PUBLIC_ALLOW_DEMO_MODE=true" "$env_file"; then
    echo "$env_file has EXPO_PUBLIC_ALLOW_DEMO_MODE=true; Sprint 1-3 proof requires false." >&2
    exit 1
  fi

  echo "$app_name env is configured with demo mode disabled."
}

require_mobile_env "$ROOT_DIR/apps/customer-app"
require_mobile_env "$ROOT_DIR/apps/merchant-captain-app"

run_in_dir "$ROOT_DIR/backend" ./gradlew \
  :api-gateway:test \
  :provider-service:test \
  :catalog-service:test \
  :discovery-service:test \
  :order-service:test \
  :payment-service:test

run "$ROOT_DIR/scripts/check-no-generated-artifacts.sh"

run_in_dir "$ROOT_DIR/apps/customer-app" npm run typecheck
run_in_dir "$ROOT_DIR/apps/customer-app" npm run lint
run_in_dir "$ROOT_DIR/apps/merchant-captain-app" npm run typecheck
run_in_dir "$ROOT_DIR/apps/merchant-captain-app" npm run lint

run "$PYTHON_BIN" "$ROOT_DIR/backend/verify_sprint1.py"
run "$PYTHON_BIN" "$ROOT_DIR/backend/verify_sprint2.py"
run "$PYTHON_BIN" "$ROOT_DIR/backend/verify_sprint3.py"

if [[ "$RUN_LIVE" == "1" ]]; then
  run "$PYTHON_BIN" "$ROOT_DIR/backend/verify_sprints_1_2_live.py"
  run "$PYTHON_BIN" "$ROOT_DIR/backend/verify_sprint3_live.py"
fi

echo
echo "Sprints 1-3 verification completed."
