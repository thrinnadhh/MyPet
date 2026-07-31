#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mypet-e2e}"
REPORT="${MYPET_SMOKE_REPORT:-$ROOT/build/reports/full-stack-smoke.md}"
KEEP_STACK="${KEEP_STACK:-0}"
TEMP_ENV=""
PROBE_CONTAINER="${PROJECT_NAME}-probe"

mkdir -p "$(dirname "$REPORT")"
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
BANK_DATA_ENCRYPTION_KEY=bG9jYWwtZGV2LWJhbmsrZW5jcnlwdGlvbi1rZXktMzI=
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
  -f "$ROOT/infra/docker-compose.local.yml"
)

show_diagnostics() {
  echo
  echo "===== Docker Compose status =====" >&2
  "${COMPOSE[@]}" ps -a >&2 || true
  echo
  echo "===== Recent Docker Compose logs =====" >&2
  "${COMPOSE[@]}" logs --no-color --tail=200 >&2 || true
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
    container_id="$("${COMPOSE[@]}" ps -q "$service")"
    if [[ -n "$container_id" ]]; then
      state="$(docker inspect --format='{{.State.Status}}' "$container_id")"
      if [[ "$state" == "exited" || "$state" == "dead" ]]; then
        "${COMPOSE[@]}" logs --no-color --tail=160 "$service" >&2 || true
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

  "${COMPOSE[@]}" logs --no-color --tail=160 "$service" >&2 || true
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

schema_count="$(docker exec pawsnearme-postgres psql -U postgres -d pawsnearme -Atc "
SELECT count(*)
FROM information_schema.schemata
WHERE schema_name IN (
  'auth','identity','providers','catalog','orders','appointments','dispatch',
  'captains','payments','reviews','notifications','chat','content'
);")"
[[ "$schema_count" == "13" ]] || fail "Expected 13 application schemas, found $schema_count"
pass "All 13 application schemas were bootstrapped"

role_count="$(docker exec pawsnearme-postgres psql -U postgres -d pawsnearme -Atc "
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

critical_tables="$(docker exec pawsnearme-postgres psql -U postgres -d pawsnearme -Atc "
SELECT count(*)
FROM (VALUES
  (to_regclass('identity.profiles')),
  (to_regclass('providers.providers')),
  (to_regclass('catalog.offerings')),
  (to_regclass('orders.orders')),
  (to_regclass('appointments.appointments')),
  (to_regclass('captains.captain_profiles')),
  (to_regclass('payments.transactions')),
  (to_regclass('chat.conversations')),
  (to_regclass('content.promo_banners'))
) AS required_table(table_name)
WHERE table_name IS NOT NULL;")"
[[ "$critical_tables" == "9" ]] || fail "Expected 9 critical tables, found $critical_tables"
pass "Critical identity, commerce, care, delivery, payment, chat, and content tables exist"

expected_topics=(
  orders.events appointments.events payments.events providers.events
  dispatch.events reviews.events chat.events vaccination.events
)
topics="$(docker exec pawsnearme-kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list)"
for topic in "${expected_topics[@]}"; do
  printf '%s\n' "$topics" | grep -qx "$topic" || fail "Kafka topic is missing: $topic"
done
pass "All required Kafka event topics exist"

curl -fsS http://localhost:8080/actuator/health/readiness >/dev/null
curl -fsS http://localhost:8090/actuator/health/readiness >/dev/null
pass "Gateway and payment host readiness endpoints returned HTTP 200"

curl -fsS http://localhost:8080/api/v1/content/banners \
  | python3 -m json.tool >/dev/null
pass "Gateway → content-service banner listing succeeded"

curl -fsS http://localhost:8080/api/v1/content/guides/categories \
  | python3 -m json.tool >/dev/null
pass "Gateway → content-service guide-category listing succeeded"

curl -fsS http://localhost:8080/api/v1/payments/promotions \
  | python3 -m json.tool >/dev/null
pass "Gateway → payment-service promotion listing succeeded"

curl -fsS http://localhost:8080/api/v1/payments/cod/config \
  | python3 -m json.tool >/dev/null
pass "Gateway → payment-service COD configuration lookup succeeded"

protected_status="$(curl -sS -o /dev/null -w '%{http_code}' \
  -X POST http://localhost:8080/api/v1/content/banners \
  -H 'Content-Type: application/json' \
  --data '{"title":"unauthorized","subtitle":"must be blocked"}')"
case "$protected_status" in
  401|403)
    pass "Gateway role guard blocked an unauthenticated content write (HTTP $protected_status)"
    ;;
  *)
    fail "Protected content write returned unexpected HTTP $protected_status"
    ;;
esac

cat >> "$REPORT" <<EOF

## Automated backend tests

EOF
cat "$ROOT/build/reports/backend-test-summary.md" >> "$REPORT"

cat >> "$REPORT" <<EOF

## Final result

**PASS** — clean database bootstrap, all infrastructure, all 13 backend applications,
selected public read paths, payment/COD reads, and a protected write boundary passed.
EOF

pass "Full-stack validation completed successfully"
echo
printf 'Report: %s\n' "$REPORT"
