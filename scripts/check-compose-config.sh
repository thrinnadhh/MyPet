#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$(mktemp)"
trap 'rm -f "$ENV_FILE"' EXIT

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

COMPOSE=(
  docker compose
  --env-file "$ENV_FILE"
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.replicas.yml"
  -f "$ROOT/infra/docker-compose.local.yml"
)

"${COMPOSE[@]}" config > /dev/null

services="$("${COMPOSE[@]}" config --services)"
expected=(
  postgres redis kafka kafka-init-topics
  provider-service catalog-service discovery-service order-service
  appointment-service dispatch-service captain-service notification-service
  review-service payment-service chat-service content-service api-gateway
  prometheus grafana
)

for service in "${expected[@]}"; do
  if ! printf '%s\n' "$services" | grep -qx "$service"; then
    echo "ERROR: rendered Compose config is missing service: $service" >&2
    exit 1
  fi
done

service_count="$(printf '%s\n' "$services" | awk 'NF { count++ } END { print count + 0 }')"
echo "Compose configuration rendered successfully (${service_count} services)."
