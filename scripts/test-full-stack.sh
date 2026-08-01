#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mypet-e2e}"
REPORT="${MYPET_SMOKE_REPORT:-$ROOT/build/reports/full-stack-smoke.md}"
KEEP_STACK="${KEEP_STACK:-0}"
TEMP_ENV=""
PROBE_CONTAINER="${PROJECT_NAME}-probe"
DIAGNOSTICS_DIR="$ROOT/build/reports/docker-diagnostics"

mkdir -p "$(dirname "$REPORT")" "$DIAGNOSTICS_DIR"
cat > "$REPORT" <<EOF
# MyPet full-stack validation

- Project: \`$PROJECT_NAME\`
- Started: \`$(date -u '+%Y-%m-%dT%H:%M:%SZ')\`

## Results
EOF

pass() {
  printf '%s\n' "- ✅ $*" | tee -a "$REPORT"
}

fail() {
  printf '%s\n' "- ❌ $*" | tee -a "$REPORT" >&2
  return 1
}

if [[ -n "${MYPET_ENV_FILE:-}" ]]; then
  ENV_FILE="$MYPET_ENV_FILE"
elif [[ -f "$ROOT/.env.local" ]]; then
  ENV_FILE="$ROOT/.env.local"
else
  TEMP_ENV="$(mktemp)"
  ENV_FILE="$TEMP_ENV"
  cat > "$ENV_FILE" <<'EOF'
GATEWAY_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
INTERNAL_API_SECRET=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789
BANK_DATA_ENCRYPTION_KEY=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
MEDICAL_REPORTS_BUCKET=mypet-local-medical-reports
MEDICAL_REPORTS_REGION=ap-south-1
MEDICAL_REPORTS_ACCESS_KEY=local-test-access-key
MEDICAL_REPORTS_SECRET_KEY=local-test-secret-key
RAZORPAY_WEBHOOK_SECRET=local-webhook-secret
EOF
fi

COMPOSE=(
  docker compose
  -p "$PROJECT_NAME"
  --env-file "$ENV_FILE"
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.replicas.yml"
  -f "$ROOT/infra/docker-compose.m4.yml"
  -f "$ROOT/infra/docker-compose.local.yml"
)

save_diagnostics() {
  "${COMPOSE[@]}" ps -a > "$DIAGNOSTICS_DIR/compose-ps.txt" 2>&1 || true
  "${COMPOSE[@]}" logs --no-color --tail=500 > "$DIAGNOSTICS_DIR/compose-logs.txt" 2>&1 || true
  for service in \
    provider-service catalog-service discovery-service order-service \
    appointment-service dispatch-service captain-service notification-service \
    review-service payment-service chat-service content-service api-gateway \
    mypet-application postgres
  do
    "${COMPOSE[@]}" logs --no-color --tail=500 "$service" \
      > "$DIAGNOSTICS_DIR/${service}.log" 2>&1 || true
  done
}

show_diagnostics() {
  save_diagnostics
  echo
  echo "===== Docker Compose status =====" >&2
  cat "$DIAGNOSTICS_DIR/compose-ps.txt" >&2 || true
  echo
  echo "===== Recent Docker Compose logs =====" >&2
  cat "$DIAGNOSTICS_DIR/compose-logs.txt" >&2 || true
}

cleanup() {
  docker rm -f "$PROBE_CONTAINER" >/dev/null 2>&1 || true
  if [[ "$KEEP_STACK" != "1" ]]; then
    "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  if [[ -n "$TEMP_ENV" ]]; then
    rm -f "$TEMP_ENV"
  fi
}

on_error() {
  local exit_code=$?
  local line_number=${1:-unknown}
  printf '%s\n' "- ❌ Validation stopped at line $line_number (exit $exit_code)." >> "$REPORT"
  show_diagnostics
  exit "$exit_code"
}

trap 'on_error $LINENO' ERR
trap cleanup EXIT

command -v docker >/dev/null 2>&1 || fail "Docker CLI is unavailable"
docker info >/dev/null 2>&1 || fail "Docker daemon is not running"

bash "$ROOT/scripts/check-production-hardening.sh"
pass "Production-hardening and Flyway-version checks passed"

bash "$ROOT/scripts/check-compose-config.sh"
pass "Compose configuration renders with all expected services"

(
  cd "$ROOT/backend"
  ./gradlew clean test bootJar --no-daemon
)
python3 "$ROOT/scripts/summarize-gradle-tests.py" \
  --output "$ROOT/build/reports/backend-test-summary.md"
pass "Backend unit/integration tests and executable JAR builds passed"

"${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" up -d --build
pass "Fresh Docker images built and the stack started"

network_name="${PROJECT_NAME}_default"
docker rm -f "$PROBE_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$PROBE_CONTAINER" --network "$network_name" alpine:3.20 sleep 600 >/dev/null

wait_for_service() {
  local service="$1"
  local port="$2"
  local deadline=$((SECONDS + 240))
  local container_id state body

  while (( SECONDS < deadline )); do
    container_id="$("${COMPOSE[@]}" ps -aq "$service")"
    if [[ -n "$container_id" ]]; then
      state="$(docker inspect --format='{{.State.Status}}' "$container_id")"
      if [[ "$state" == "exited" || "$state" == "dead" ]]; then
        "${COMPOSE[@]}" logs --no-color --tail=500 "$service" \
          > "$DIAGNOSTICS_DIR/${service}.log" 2>&1 || true
        cat "$DIAGNOSTICS_DIR/${service}.log" >&2 || true
        fail "$service exited before becoming ready"
        return 1
      fi

      body="$(docker exec "$PROBE_CONTAINER" \
        wget -qO- "http://$service:$port/actuator/health/readiness" 2>/dev/null || true)"
      if printf '%s' "$body" | grep -q '"status":"UP"'; then
        pass "$service readiness probe passed"
        return 0
      fi
    fi
    sleep 5
  done

  "${COMPOSE[@]}" logs --no-color --tail=500 "$service" \
    > "$DIAGNOSTICS_DIR/${service}.log" 2>&1 || true
  cat "$DIAGNOSTICS_DIR/${service}.log" >&2 || true
  fail "$service did not become ready within 240 seconds"
}

service_ports=(
  "provider-service:8081"
  "catalog-service:8082"
  "discovery-service:8083"
  "order-service:8084"
  "appointment-service:8085"
  "dispatch-service:8086"
  "captain-service:8087"
  "notification-service:8088"
  "review-service:8089"
  "payment-service:8090"
  "chat-service:8091"
  "content-service:8092"
  "api-gateway:8080"
  "mypet-application:8093"
)

for entry in "${service_ports[@]}"; do
  wait_for_service "${entry%%:*}" "${entry##*:}"
done

for service in postgres redis kafka; do
  container_id="$("${COMPOSE[@]}" ps -q "$service")"
  health="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
  [[ "$health" == "healthy" ]] || fail "$service is not healthy (state: $health)"
  pass "$service infrastructure health check passed"
done

kafka_init_id="$("${COMPOSE[@]}" ps -aq kafka-init-topics)"
kafka_init_exit="$(docker inspect --format='{{.State.ExitCode}}' "$kafka_init_id")"
[[ "$kafka_init_exit" == "0" ]] || fail "Kafka topic initialization exited with $kafka_init_exit"
pass "Kafka topic initialization completed successfully"

schema_count="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -Atc "
SELECT count(*)
FROM information_schema.schemata
WHERE schema_name IN (
  'auth','identity','providers','catalog','orders','appointments','dispatch',
  'captains','payments','reviews','notifications','chat','content'
);")"
[[ "$schema_count" == "13" ]] || fail "Expected 13 application schemas, found $schema_count"
pass "All 13 application schemas were bootstrapped"

bootstrap_marker="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -Atc "
SELECT count(*) FROM public.bootstrap_status WHERE bootstrap_name = 'base-schema';")"
[[ "$bootstrap_marker" == "1" ]] || fail "Database bootstrap completion marker is missing"
pass "Database bootstrap completed before application startup"

role_count="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -Atc "
SELECT count(*)
FROM pg_roles
WHERE rolname IN (
  'identity_service_role','provider_service_role','catalog_service_role',
  'discovery_service_role','order_service_role','appointment_service_role',
  'dispatch_service_role','captain_service_role','notification_service_role',
  'review_service_role','payment_service_role','chat_service_role','content_service_role'
);")"
[[ "$role_count" == "13" ]] || fail "Expected 13 service database roles, found $role_count"
pass "All 13 service database roles were created"

critical_tables="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -Atc "
SELECT count(*)
FROM (VALUES
  (to_regclass('identity.profiles')),
  (to_regclass('providers.providers')),
  (to_regclass('catalog.offerings')),
  (to_regclass('orders.orders')),
  (to_regclass('appointments.appointments')),
  (to_regclass('captains.captain_profiles')),
  (to_regclass('captains.captain_documents')),
  (to_regclass('payments.transactions')),
  (to_regclass('chat.conversations')),
  (to_regclass('content.promo_banners'))
) AS required_table(table_name)
WHERE table_name IS NOT NULL;")"
[[ "$critical_tables" == "10" ]] || fail "Expected 10 critical tables, found $critical_tables"
pass "Critical identity, commerce, care, delivery, payment, chat, and content tables exist"

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
  || fail "Expected 12 isolated Flyway history tables, found $migration_history_count"
pass "All twelve legacy Flyway history tables were retained"

failed_migrations="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -Atc "
SELECT COALESCE(sum(failed), 0)
FROM (
  SELECT count(*) AS failed FROM providers.flyway_schema_history_provider WHERE NOT success
  UNION ALL SELECT count(*) FROM catalog.flyway_schema_history_catalog WHERE NOT success
  UNION ALL SELECT count(*) FROM providers.flyway_schema_history_discovery WHERE NOT success
  UNION ALL SELECT count(*) FROM orders.flyway_schema_history_order WHERE NOT success
  UNION ALL SELECT count(*) FROM appointments.flyway_schema_history_appointment WHERE NOT success
  UNION ALL SELECT count(*) FROM dispatch.flyway_schema_history_dispatch WHERE NOT success
  UNION ALL SELECT count(*) FROM captains.flyway_schema_history_captain WHERE NOT success
  UNION ALL SELECT count(*) FROM notifications.flyway_schema_history_notification WHERE NOT success
  UNION ALL SELECT count(*) FROM reviews.flyway_schema_history_review WHERE NOT success
  UNION ALL SELECT count(*) FROM payments.flyway_schema_history_payment WHERE NOT success
  UNION ALL SELECT count(*) FROM chat.flyway_schema_history_chat WHERE NOT success
  UNION ALL SELECT count(*) FROM content.flyway_schema_history_content WHERE NOT success
) AS failures;")"
[[ "$failed_migrations" == "0" ]] || fail "Found $failed_migrations failed Flyway migrations"
pass "Consolidated Flyway validation found no failed migration records"

m4_info="$(docker exec "$PROBE_CONTAINER" \
  wget -qO- http://mypet-application:8093/actuator/info)"
printf '%s' "$m4_info" | python3 -c '
import json, sys
payload = json.load(sys.stdin)
database = payload.get("databaseConsolidation", {})
assert database.get("enabled") is True, database
assert database.get("mode") == "application-owned", database
assert database.get("phase") == "READY", database
assert database.get("moduleCount") == 12, database
assert len(database.get("modules", [])) == 12, database
'
pass "Consolidated application reports all twelve database owners READY"

captain_columns="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -Atc "
SELECT count(*)
FROM information_schema.columns
WHERE table_schema = 'captains'
  AND table_name = 'captain_profiles'
  AND column_name IN ('bank_account', 'bank_ifsc', 'selfie_doc_url');")"
[[ "$captain_columns" == "3" ]] || fail "Captain onboarding/bank columns were not bootstrapped"
pass "Captain onboarding and encrypted-bank storage columns exist"

expected_topics=(
  orders.events appointments.events payments.events providers.events
  dispatch.events reviews.events chat.events vaccination.events
)
topics="$("${COMPOSE[@]}" exec -T kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list)"
for topic in "${expected_topics[@]}"; do
  printf '%s\n' "$topics" | grep -qx "$topic" || fail "Kafka topic is missing: $topic"
done
pass "All required Kafka event topics exist"

curl -fsS http://localhost:8080/actuator/health/readiness >/dev/null
curl -fsS http://localhost:8090/actuator/health/readiness >/dev/null
curl -fsS http://localhost:8093/actuator/health/readiness >/dev/null
pass "Gateway, payment, and consolidated application readiness endpoints returned HTTP 200"

local_user_id="11111111-1111-1111-1111-111111111111"
local_jwt="$(python3 - <<'PY'
import base64
import json
import time


def encode(value):
    raw = json.dumps(value, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")

now = int(time.time())
print(
    f"{encode({'alg': 'none', 'typ': 'JWT'})}."
    f"{encode({'sub': '11111111-1111-1111-1111-111111111111', 'iat': now, 'exp': now + 3600})}."
)
PY
)"
auth_headers=(
  -H "Authorization: Bearer $local_jwt"
  -H "X-User-Id: $local_user_id"
  -H "X-User-Role: CUSTOMER"
)

curl -fsS http://localhost:8080/api/v1/content/banners \
  | python3 -m json.tool >/dev/null
pass "Gateway → content-service banner listing succeeded"

curl -fsS http://localhost:8080/api/v1/content/guides/categories \
  | python3 -m json.tool >/dev/null
pass "Gateway → content-service guide-category listing succeeded"

catalog_provider_id="22222222-2222-2222-2222-222222222222"
curl -fsS \
  "http://localhost:8080/api/v1/catalog/offerings?providerId=$catalog_provider_id" \
  | python3 -c 'import json,sys; data=json.load(sys.stdin); assert isinstance(data, list), data'
pass "Gateway → catalog-service offering listing succeeded"

curl -fsS http://localhost:8080/api/v1/service-regions \
  | python3 -m json.tool >/dev/null
pass "Gateway → discovery-service service-region listing succeeded"

curl -fsS "${auth_headers[@]}" \
  http://localhost:8080/api/v1/payments/promotions \
  | python3 -m json.tool >/dev/null
pass "Authenticated gateway → payment-service promotion listing succeeded"

curl -fsS "${auth_headers[@]}" \
  http://localhost:8080/api/v1/payments/cod/config \
  | python3 -m json.tool >/dev/null
pass "Authenticated gateway → payment-service COD configuration lookup succeeded"

protected_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  "${auth_headers[@]}" \
  -X POST http://localhost:8080/api/v1/content/banners \
  -H 'Content-Type: application/json' \
  --data '{"title":"unauthorized","subtitle":"must be blocked"}')"
[[ "$protected_status" == "403" ]] \
  || fail "Protected content write returned unexpected HTTP $protected_status"
pass "Gateway role guard blocked a CUSTOMER content write (HTTP 403)"

cat >> "$REPORT" <<EOF

## Automated backend tests

EOF
cat "$ROOT/build/reports/backend-test-summary.md" >> "$REPORT"

cat >> "$REPORT" <<EOF

## Final result

**PASS** — clean database bootstrap, all infrastructure, all 13 distributed backend applications,
the consolidated M4 database shadow, migration-history continuity, selected public reads,
authenticated payment/COD reads, and a protected write boundary passed.
EOF

pass "Full-stack validation completed successfully"
echo
printf 'Report: %s\n' "$REPORT"
