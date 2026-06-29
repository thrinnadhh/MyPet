#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_FLOWS=0

for arg in "$@"; do
  case "$arg" in
    --flows)
      RUN_FLOWS=1
      ;;
    -h|--help)
      echo "Usage: scripts/verify-all.sh [--flows]"
      echo
      echo "Runs backend tests, mobile TypeScript, and mobile lint."
      echo "Use --flows when local Postgres/Redis/Kafka/backend services are running."
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
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

run_in_dir "$ROOT_DIR/backend" ./gradlew test

run_in_dir "$ROOT_DIR/apps/customer-app" npm run typecheck
run_in_dir "$ROOT_DIR/apps/customer-app" npm run lint

run_in_dir "$ROOT_DIR/apps/merchant-captain-app" npm run typecheck
run_in_dir "$ROOT_DIR/apps/merchant-captain-app" npm run lint

if [[ "$RUN_FLOWS" == "1" ]]; then
  run "$ROOT_DIR/.venv/bin/python" "$ROOT_DIR/backend/verify_appointments.py"
  run "$ROOT_DIR/.venv/bin/python" "$ROOT_DIR/backend/verify_dispatch.py"
  run "$ROOT_DIR/.venv/bin/python" "$ROOT_DIR/backend/verify_sprint6.py"
  run "$ROOT_DIR/.venv/bin/python" "$ROOT_DIR/backend/verify_sprint7.py"
  run "$ROOT_DIR/.venv/bin/python" "$ROOT_DIR/backend/verify_deviations_resolved.py"
fi
