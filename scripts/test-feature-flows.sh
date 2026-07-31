#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mypet-e2e}"
REPORT="${MYPET_SMOKE_REPORT:-$ROOT/build/reports/full-stack-smoke.md}"
ENV_FILE="${MYPET_ENV_FILE:?MYPET_ENV_FILE must be set by test-all.sh}"

COMPOSE=(
  docker compose
  -p "$PROJECT_NAME"
  --env-file "$ENV_FILE"
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.replicas.yml"
  -f "$ROOT/infra/docker-compose.local.yml"
)

pass() {
  printf '%s\n' "- ✅ $*" | tee -a "$REPORT"
}

fail() {
  printf '%s\n' "- ❌ $*" | tee -a "$REPORT" >&2
  exit 1
}

assert_json() {
  local expression="$1"
  python3 -c "import json,sys; data=json.load(sys.stdin); assert $expression, data"
}

cat >> "$REPORT" <<'EOF'

## Extended feature flows
EOF

loyalty_table_count="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -Atc "
SELECT count(*) FROM information_schema.tables
WHERE table_schema = 'payments'
  AND table_name IN (
    'loyalty_programs', 'customer_loyalty_accounts',
    'loyalty_ledger_entries', 'loyalty_reward_instances',
    'loyalty_processed_events', 'loyalty_audit_logs'
  );")"
[[ "$loyalty_table_count" == "6" ]] || fail "Expected 6 loyalty tables, found $loyalty_table_count"
pass "Loyalty database schema is complete"

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
AUTH_HEADERS=(-H "Authorization: Bearer $local_jwt")
provider_id="22222222-2222-2222-2222-222222222222"
order_id="33333333-3333-3333-3333-333333333333"

program="$(curl -fsS "${AUTH_HEADERS[@]}" \
  "http://localhost:8080/api/v1/loyalty/programs")"
printf '%s' "$program" | assert_json 'data["targetStars"] == 10 and data["rewardAmount"] == 50.00'
pass "Default ten-star loyalty program was created and read through the gateway"

welcome="$(curl -fsS "${AUTH_HEADERS[@]}" -X POST \
  "http://localhost:8080/api/v1/loyalty/welcome-star/claim?providerId=$provider_id")"
printf '%s' "$welcome" | assert_json 'data["starBalance"] == 1 and data["welcomeStarClaimed"] is True'
pass "Customer welcome-star claim succeeded"

welcome_repeat="$(curl -fsS "${AUTH_HEADERS[@]}" -X POST \
  "http://localhost:8080/api/v1/loyalty/welcome-star/claim?providerId=$provider_id")"
printf '%s' "$welcome_repeat" | assert_json 'data["starBalance"] == 1 and data["welcomeStarClaimed"] is True'
pass "Welcome-star claim is idempotent"

order_event="$(curl -fsS "${AUTH_HEADERS[@]}" -X POST \
  http://localhost:8080/api/v1/loyalty/events/order-delivered \
  -H 'Content-Type: application/json' \
  --data "{\"orderId\":\"$order_id\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"providerId\":\"$provider_id\",\"netAmount\":250.00}")"
printf '%s' "$order_event" | assert_json 'data["processed"] is True'
pass "Delivered-order event awarded one purchase star"

order_event_repeat="$(curl -fsS "${AUTH_HEADERS[@]}" -X POST \
  http://localhost:8080/api/v1/loyalty/events/order-delivered \
  -H 'Content-Type: application/json' \
  --data "{\"orderId\":\"$order_id\",\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"providerId\":\"$provider_id\",\"netAmount\":250.00}")"
printf '%s' "$order_event_repeat" | assert_json 'data["processed"] is False'
pass "Duplicate delivered-order loyalty event was rejected idempotently"

progress="$(curl -fsS "${AUTH_HEADERS[@]}" \
  "http://localhost:8080/api/v1/loyalty/progress?providerId=$provider_id")"
printf '%s' "$progress" | assert_json 'data["starBalance"] == 2 and data["totalStarsEarned"] == 2'
pass "Loyalty progress persisted the welcome and purchase stars"

ledger="$(curl -fsS "${AUTH_HEADERS[@]}" \
  "http://localhost:8080/api/v1/loyalty/ledger?providerId=$provider_id")"
printf '%s' "$ledger" | assert_json 'len(data) == 2'
pass "Loyalty ledger recorded exactly two star entries"

region_check="$(curl -fsS \
  "http://localhost:8080/api/v1/service-regions/check?pincode=517501")"
printf '%s' "$region_check" | assert_json 'data["serviceable"] is True'
pass "Tirupati pincode serviceability lookup succeeded"

printf '%s\n' "- ✅ Extended loyalty and service-region feature flows passed" >> "$REPORT"
