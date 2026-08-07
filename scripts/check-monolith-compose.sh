#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$(mktemp)"
CONFIG_JSON="$(mktemp)"
trap 'rm -f "$ENV_FILE" "$CONFIG_JSON"' EXIT

cat > "$ENV_FILE" <<'EOF'
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
ORDER_RECURRING_REMINDER_CRON="*/5 * * * * *"
EOF

COMPOSE=(
  docker compose
  --env-file "$ENV_FILE"
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.monolith.yml"
)

"${COMPOSE[@]}" config > /dev/null
"${COMPOSE[@]}" config --format json > "$CONFIG_JSON"

grep -Fq 'ORDER_RECURRING_REMINDER_CRON: ${ORDER_RECURRING_REMINDER_CRON:-0 0 * * * *}' \
  "$ROOT/infra/docker-compose.monolith.yml" || {
    echo "ERROR: monolith recurring reminder cron must retain the hourly production default" >&2
    exit 1
  }

services="$("${COMPOSE[@]}" config --services)"
expected=(
  postgres redis kafka kafka-init-topics
  mypet-application prometheus grafana
)

for service in "${expected[@]}"; do
  if ! printf '%s\n' "$services" | grep -qx "$service"; then
    echo "ERROR: monolith Compose is missing service: $service" >&2
    exit 1
  fi
done

for forbidden in \
  api-gateway provider-service catalog-service discovery-service order-service \
  appointment-service dispatch-service captain-service notification-service \
  review-service payment-service chat-service content-service
 do
  if printf '%s\n' "$services" | grep -qx "$forbidden"; then
    echo "ERROR: monolith Compose still deploys legacy backend: $forbidden" >&2
    exit 1
  fi
done

service_count="$(printf '%s\n' "$services" | awk 'NF { count++ } END { print count + 0 }')"
[[ "$service_count" == "7" ]] || {
  echo "ERROR: expected 7 monolith/infrastructure services, found $service_count" >&2
  exit 1
}

python3 - "$CONFIG_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    config = json.load(handle)

application = config["services"]["mypet-application"]
environment = application.get("environment", {})
required = {
    "GRPC_PORT": "-1",
    "MYPET_MODULES_ENABLED": "true",
    "MYPET_EDGE_ENABLED": "true",
    "MYPET_DATABASE_ENABLED": "true",
    "MYPET_SCHEDULING_ROLE": "ALL",
    "MYPET_SCHEDULING_LOCK_TABLE": "orders.shedlock",
    "GATEWAY_TRUST_ENABLED": "false",
    "ORDER_RECURRING_REMINDER_CRON": "*/5 * * * * *",
}
for key, expected in required.items():
    actual = str(environment.get(key, ""))
    if actual.upper() != expected.upper():
        raise SystemExit(f"ERROR: {key} must be {expected}; found {actual!r}")

url = str(environment.get("MYPET_DB_URL", ""))
for schema in (
    "orders", "appointments", "providers", "catalog", "dispatch", "captains",
    "notifications", "reviews", "payments", "chat", "content", "identity",
    "customer", "public",
):
    if schema not in url:
        raise SystemExit(f"ERROR: consolidated DB search path is missing {schema}")

if environment.get("CATALOG_SERVICE_URL") or environment.get("ORDER_SERVICE_URL"):
    raise SystemExit("ERROR: monolith must not configure internal service URLs")

build_args = application.get("build", {}).get("args", {})
if build_args.get("SERVICE_NAME") != "mypet-application":
    raise SystemExit("ERROR: monolith must build the mypet-application executable")

ports = application.get("ports", [])

def publishes_api(port):
    if isinstance(port, dict):
        return str(port.get("published")) == "8080" and int(port.get("target", 0)) == 8080
    return str(port).startswith("8080:")

if not any(publishes_api(port) for port in ports):
    raise SystemExit("ERROR: monolith must retain the public API on host port 8080")

print("Monolith Compose cutover contract passed: one backend process and six infrastructure services.")
PY
