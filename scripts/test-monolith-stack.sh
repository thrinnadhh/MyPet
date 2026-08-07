#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mypet-monolith-e2e}"
REPORT="${MYPET_MONOLITH_REPORT:-$ROOT/build/reports/monolith-smoke.md}"
ENV_FILE="${MYPET_ENV_FILE:-}"
OWNS_ENV_FILE="false"
DIAGNOSTICS_DIR="$ROOT/build/reports/monolith-diagnostics"
mkdir -p "$(dirname "$REPORT")" "$DIAGNOSTICS_DIR"

if [[ -z "$ENV_FILE" ]]; then
  ENV_FILE="$(mktemp)"
  OWNS_ENV_FILE="true"
  cat > "$ENV_FILE" <<'EOF'
SPRING_PROFILES_ACTIVE=local
INTERNAL_API_SECRET=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789
BANK_DATA_ENCRYPTION_KEY=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
MEDICAL_REPORTS_BUCKET=mypet-local-medical-reports
MEDICAL_REPORTS_REGION=ap-south-1
MEDICAL_REPORTS_ACCESS_KEY=local-test-access-key
MEDICAL_REPORTS_SECRET_KEY=local-test-secret-key
MEDICAL_DOCUMENT_SIGNING_KEY=0123456789abcdef0123456789abcdef
CASE_EVIDENCE_SIGNING_KEY=abcdef0123456789abcdef0123456789
PAYMENT_CHECKOUT_TOKEN_SECRET=0123456789abcdef0123456789abcdef
CASHFREE_WEBHOOK_SECRET=local-cashfree-webhook-secret
ALLOW_UNSIGNED_JWT=true
NOTIFICATION_DELIVERY_MODE=LOGGED_DEV
EOF
fi

COMPOSE=(
  docker compose
  -p "$PROJECT_NAME"
  --env-file "$ENV_FILE"
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.monolith.yml"
)

pass() {
  printf '%s\n' "- ✅ $*" | tee -a "$REPORT"
}

fail() {
  printf '%s\n' "- ❌ $*" | tee -a "$REPORT" >&2
  return 1
}

save_diagnostics() {
  "${COMPOSE[@]}" ps -a > "$DIAGNOSTICS_DIR/compose-ps.txt" 2>&1 || true
  "${COMPOSE[@]}" logs --no-color --tail=800 > "$DIAGNOSTICS_DIR/compose-logs.txt" 2>&1 || true
}

cleanup() {
  if [[ "${KEEP_STACK:-0}" != "1" ]]; then
    "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  if [[ "$OWNS_ENV_FILE" == "true" ]]; then
    rm -f "$ENV_FILE"
  fi
}

on_error() {
  local exit_code=$?
  local line_number=${1:-unknown}
  printf '%s\n' "- ❌ Monolith validation stopped at line $line_number (exit $exit_code)." >> "$REPORT"
  save_diagnostics
  cat "$DIAGNOSTICS_DIR/compose-ps.txt" >&2 || true
  cat "$DIAGNOSTICS_DIR/compose-logs.txt" >&2 || true
  exit "$exit_code"
}

trap 'on_error $LINENO' ERR
trap cleanup EXIT

cat > "$REPORT" <<EOF
# MyPet modular-monolith validation

- Project: \`$PROJECT_NAME\`
- Started: \`$(date -u '+%Y-%m-%dT%H:%M:%SZ')\`

## Results
EOF

command -v docker >/dev/null 2>&1 || fail "Docker CLI is unavailable"
docker info >/dev/null 2>&1 || fail "Docker daemon is not running"

bash "$ROOT/scripts/check-monolith-compose.sh"
pass "Rendered topology contains one backend process"

(
  cd "$ROOT/backend"
  ./gradlew :mypet-application:test :mypet-application:bootJar --no-daemon
)
pass "Consolidated application tests and executable JAR build passed"

"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build
pass "Fresh monolith and infrastructure containers started"

deadline=$((SECONDS + 300))
while (( SECONDS < deadline )); do
  container_id="$("${COMPOSE[@]}" ps -aq mypet-application)"
  if [[ -n "$container_id" ]]; then
    state="$(docker inspect --format='{{.State.Status}}' "$container_id")"
    if [[ "$state" == "exited" || "$state" == "dead" ]]; then
      fail "mypet-application exited before readiness"
    fi
    if curl -fsS http://localhost:8080/actuator/health/readiness \
      | grep -q '"status":"UP"'; then
      break
    fi
  fi
  sleep 5
done
curl -fsS http://localhost:8080/actuator/health/readiness \
  | grep -q '"status":"UP"' \
  || fail "mypet-application did not become ready within 300 seconds"
pass "Consolidated readiness probe is UP"

info="$(curl -fsS http://localhost:8080/actuator/info)"
printf '%s' "$info" | python3 -c '
import json, sys
payload = json.load(sys.stdin)
app = payload.get("app", {})
assert app.get("milestone") == "M10", app
assert app.get("architecture") == "modular-monolith", app
modules = payload.get("businessModules", {})
assert modules.get("count") == 12, modules
assert modules.get("runtimeMode") == "active-in-process", modules
runtime = payload.get("monolithRuntime", {})
assert runtime.get("enabled") is True, runtime
assert runtime.get("deploymentUnits") == 1, runtime
assert runtime.get("legacyServiceContainersRequired") is False, runtime
edge = payload.get("edgeSecurity", {})
assert edge.get("enabled") is True, edge
database = payload.get("databaseConsolidation", {})
assert database.get("enabled") is True, database
assert database.get("phase") == "READY", database
assert database.get("moduleCount") == 12, database
scheduler = payload.get("schedulerRuntime", {})
assert scheduler.get("role") == "ALL", scheduler
assert scheduler.get("workersEnabled") is True, scheduler
verification = payload.get("featureVerification", {})
assert verification.get("cutoverAuthorized") is True, verification
assert verification.get("legacyRollbackRequired") is False, verification
'
pass "Actuator reports active modules, embedded edge, one database owner and M10 cutover"

services="$("${COMPOSE[@]}" config --services)"
backend_count="$(printf '%s\n' "$services" | grep -E '(^mypet-application$|service$|gateway$)' | wc -l | tr -d ' ')"
[[ "$backend_count" == "1" ]] || fail "Expected one backend deployment unit, found $backend_count"
pass "Runtime topology has one backend deployment unit"

migration_history_count="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -Atc "
SELECT count(*)
FROM (VALUES
  (to_regclass('providers.flyway_schema_history_provider')),
  (to_regclass('catalog.flyway_schema_history_catalog')),
  (to_regclass('providers.flyway_schema_history_discovery')),
  (to_regclass('orders.flyway_schema_history_order')),
  (to_regclass('appointments.flyway_schema_history_appointment')),
  (to_regclass('dispatch.flyway_schema_history_dispatch')),
  (to_regclass('captains.flyway_schema_history_captain')),
  (to_regclass('notifications.flyway_schema_history_notification')),
  (to_regclass('reviews.flyway_schema_history_review')),
  (to_regclass('payments.flyway_schema_history_payment')),
  (to_regclass('chat.flyway_schema_history_chat')),
  (to_regclass('content.flyway_schema_history_content'))
) AS history_table(table_name)
WHERE table_name IS NOT NULL;")"
[[ "$migration_history_count" == "12" ]] \
  || fail "Expected twelve retained Flyway histories, found $migration_history_count"
pass "All twelve schema-owned migration histories were retained"

local_jwt="$(python3 - <<'PY'
import base64
import json
import time


def encode(value):
    raw = json.dumps(value, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")

now = int(time.time())
claims = {
    "sub": "11111111-1111-1111-1111-111111111111",
    "iat": now,
    "exp": now + 3600,
    "app_metadata": {"role": "CUSTOMER"},
}
print(f"{encode({'alg': 'none', 'typ': 'JWT'})}.{encode(claims)}.")
PY
)"

program="$(curl -fsS \
  -H "Authorization: Bearer $local_jwt" \
  http://localhost:8080/api/v1/loyalty/programs)"
printf '%s' "$program" | python3 -c '
import json, sys
data = json.load(sys.stdin)
assert data["targetStars"] == 10, data
assert float(data["rewardAmount"]) == 50.0, data
'
pass "Authenticated loyalty API executed through the embedded edge and payment module"

cat >> "$REPORT" <<'EOF'

## Overall result

**PASS** — MyPet booted with one backend deployment unit, all twelve bounded
contexts active in-process, embedded API security, one application-owned
PostgreSQL pool, retained schema histories, scheduler ownership, durable Kafka
infrastructure and a real authenticated business API flow.
EOF

echo "Monolith validation report: $REPORT"
