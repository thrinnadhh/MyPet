#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mypet-e2e}"
REPORT="${MYPET_SMOKE_REPORT:-$ROOT/build/reports/full-stack-smoke.md}"
ENV_FILE="$(mktemp)"

cat > "$ENV_FILE" <<'EOF'
POSTGRES_PASSWORD=postgres
PUBLIC_URL=http://localhost:8080
GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8081
SUPABASE_JWT_JWK_SET_URI=https://your-project.supabase.co/rest/v1/auth/keys
GATEWAY_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
INTERNAL_API_SECRET=abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789
BANK_DATA_ENCRYPTION_KEY=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
MEDICAL_REPORTS_BUCKET=mypet-local-medical-reports
MEDICAL_REPORTS_REGION=ap-south-1
MEDICAL_REPORTS_ACCESS_KEY=local-test-access-key
MEDICAL_REPORTS_SECRET_KEY=local-test-secret-key
MEDICAL_DOCUMENT_SIGNING_KEY=0123456789abcdef0123456789abcdef
CASE_EVIDENCE_SIGNING_KEY=abcdef0123456789abcdef0123456789
CASHFREE_CLIENT_ID=ci-interpolation-only-client-id
CASHFREE_CLIENT_SECRET=ci-interpolation-only-client-secret
CASHFREE_WEBHOOK_SECRET=local-cashfree-webhook-secret
PAYMENT_CHECKOUT_TOKEN_SECRET=0123456789abcdef0123456789abcdef
# Retained only for legacy payment-service unit/contract coverage; active customer
# online payments are routed through Cashfree.
RAZORPAY_WEBHOOK_SECRET=local-legacy-webhook-secret
EOF

COMPOSE=(
  docker compose
  -p "$PROJECT_NAME"
  --env-file "$ENV_FILE"
  -f "$ROOT/infra/docker-compose.yml"
  -f "$ROOT/infra/docker-compose.replicas.yml"
  -f "$ROOT/infra/docker-compose.local.yml"
)

cleanup() {
  "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -f "$ENV_FILE"
}
trap cleanup EXIT

export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
export MYPET_ENV_FILE="$ENV_FILE"
export MYPET_SMOKE_REPORT="$REPORT"
export KEEP_STACK=1

bash "$ROOT/scripts/test-full-stack.sh"
bash "$ROOT/scripts/test-feature-flows.sh"
bash "$ROOT/scripts/test-barcode-e2e.sh"
python3 "$ROOT/scripts/run-m8-feature-matrix.py"
python3 "$ROOT/scripts/run-p2b-connected-e2e-entry.py"

cat >> "$REPORT" <<'EOF'

## Overall result

**PASS** — automated builds, clean-volume infrastructure, all backend service
readiness probes, barcode inventory upload → scan lookup → POS checkout,
the connected M8 fourteen-domain matrix, and the exact ten P2B customer →
merchant → captain → admin journeys completed successfully. Authorization,
concurrency, idempotency, private-document access, scheduler, persistence,
outbox, notification/UI contracts, inventory isolation, authoritative pricing,
and asynchronous projection evidence were retained in the same report.
EOF

echo "Complete validation report: $REPORT"
