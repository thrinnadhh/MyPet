#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$(mktemp)"
CONFIG_JSON="$(mktemp)"
trap 'rm -f "$ENV_FILE" "$CONFIG_JSON"' EXIT

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

COMPOSE=(
  docker compose
  --env-file "$ENV_FILE"
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.replicas.yml"
  -f "$ROOT/infra/docker-compose.m4.yml"
  -f "$ROOT/infra/docker-compose.local.yml"
)

"${COMPOSE[@]}" config > /dev/null
"${COMPOSE[@]}" config --format json > "$CONFIG_JSON"

services="$("${COMPOSE[@]}" config --services)"
expected=(
  postgres redis kafka kafka-init-topics
  provider-service catalog-service discovery-service order-service
  appointment-service dispatch-service captain-service notification-service
  review-service payment-service chat-service content-service api-gateway
  mypet-application prometheus grafana
)

for service in "${expected[@]}"; do
  if ! printf '%s\n' "$services" | grep -qx "$service"; then
    echo "ERROR: rendered Compose config is missing service: $service" >&2
    exit 1
  fi
done

python3 - "$CONFIG_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    config = json.load(handle)

expected = {
    "provider-service": "flyway_schema_history_provider",
    "catalog-service": "flyway_schema_history_catalog",
    "discovery-service": "flyway_schema_history_discovery",
    "order-service": "flyway_schema_history_order",
    "appointment-service": "flyway_schema_history_appointment",
    "dispatch-service": "flyway_schema_history_dispatch",
    "captain-service": "flyway_schema_history_captain",
    "notification-service": "flyway_schema_history_notification",
    "review-service": "flyway_schema_history_review",
    "payment-service": "flyway_schema_history_payment",
    "chat-service": "flyway_schema_history_chat",
    "content-service": "flyway_schema_history_content",
}

actual = {}
for service, table in expected.items():
    environment = config["services"][service].get("environment", {})
    value = environment.get("SPRING_FLYWAY_TABLE")
    if value != table:
        raise SystemExit(
            f"ERROR: {service} must use SPRING_FLYWAY_TABLE={table}; found {value!r}"
        )
    actual[service] = value

if len(set(actual.values())) != len(actual):
    raise SystemExit("ERROR: Flyway history tables must be unique per service")

m4_environment = config["services"]["mypet-application"].get("environment", {})
if str(m4_environment.get("MYPET_DATABASE_ENABLED", "")).lower() != "true":
    raise SystemExit("ERROR: M4 shadow runtime must enable database consolidation")
if str(m4_environment.get("MYPET_EDGE_ENABLED", "")).lower() != "false":
    raise SystemExit("ERROR: M4 shadow runtime must not replace the API gateway")
if "pawsnearme" not in str(m4_environment.get("MYPET_DB_URL", "")):
    raise SystemExit("ERROR: M4 shadow runtime must target the existing pawsnearme database")

print(f"Flyway history is isolated across {len(actual)} legacy owners and M4 is shadow-enabled.")
PY

service_count="$(printf '%s\n' "$services" | awk 'NF { count++ } END { print count + 0 }')"
echo "Compose configuration rendered successfully (${service_count} services)."
