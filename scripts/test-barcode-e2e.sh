#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mypet-e2e}"
REPORT="${MYPET_SMOKE_REPORT:-$ROOT/build/reports/full-stack-smoke.md}"
ENV_FILE="${MYPET_ENV_FILE:?MYPET_ENV_FILE must be set by test-all.sh}"
GATEWAY_URL="${MYPET_GATEWAY_URL:-http://localhost:8080}"

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

json_value() {
  local expression="$1"
  python3 -c "import json,sys; data=json.load(sys.stdin); print($expression)"
}

http_request() {
  local body_file="$1"
  shift
  curl -sS -o "$body_file" -w '%{http_code}' "$@"
}

jwt_for() {
  local user_id="$1"
  local role="$2"
  python3 - "$user_id" "$role" <<'PY'
import base64
import json
import sys
import time

user_id, role = sys.argv[1:3]

def encode(value):
    raw = json.dumps(value, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")

now = int(time.time())
claims = {
    "sub": user_id,
    "email": f"barcode-e2e-{role.lower()}@mypet.local",
    "iat": now,
    "exp": now + 3600,
    "app_metadata": {"role": role},
    "user_metadata": {
        "full_name": f"Barcode E2E {role.title()}",
        "phone": "+919999999999",
    },
}
print(f"{encode({'alg': 'none', 'typ': 'JWT'})}.{encode(claims)}.")
PY
}

read -r merchant_id customer_id spoofed_staff_id run_suffix <<EOF
$(python3 - <<'PY'
import uuid
print(uuid.uuid4(), uuid.uuid4(), uuid.uuid4(), uuid.uuid4().hex[:10])
PY
)
EOF

merchant_jwt="$(jwt_for "$merchant_id" MERCHANT)"
customer_jwt="$(jwt_for "$customer_id" CUSTOMER)"
MERCHANT_AUTH=(-H "Authorization: Bearer $merchant_jwt")
CUSTOMER_AUTH=(-H "Authorization: Bearer $customer_jwt")

upc="$(python3 - "$run_suffix" <<'PY'
import hashlib
import sys
value = int(hashlib.sha256(sys.argv[1].encode()).hexdigest()[:14], 16) % 10**12
print(f"{value:012d}")
PY
)"
ean_alias="0$upc"
sku="BARCODE-E2E-${run_suffix^^}"
idempotency_key="barcode-e2e-$run_suffix"

cat >> "$REPORT" <<'EOF'

## Barcode scanner end-to-end flow
EOF

profile="$(curl -fsS "${MERCHANT_AUTH[@]}" -X POST "$GATEWAY_URL/api/v1/profiles/sync")"
printf '%s' "$profile" | assert_json "data['userId'] == '$merchant_id' and data['role'] == 'MERCHANT'"
pass "Merchant identity was synchronized through the gateway"

provider_payload="$(python3 - "$merchant_id" "$run_suffix" <<'PY'
import json
import sys
merchant_id, suffix = sys.argv[1:3]
print(json.dumps({
    "ownerUserId": merchant_id,
    "providerType": "PET_STORE",
    "fulfillmentType": "DELIVERY",
    "name": f"Barcode E2E Store {suffix}",
    "description": "Ephemeral provider for the barcode scan integration test",
    "licenseNumber": f"BARCODE-{suffix}",
    "licenseDocUrl": None,
    "addressLine": "Barcode E2E Test Road",
    "city": "Tirupati",
    "pincode": "517501",
    "longitude": 79.4192,
    "latitude": 13.6288,
}))
PY
)"
provider="$(curl -fsS "${MERCHANT_AUTH[@]}" -X POST "$GATEWAY_URL/api/v1/providers" \
  -H 'Content-Type: application/json' --data "$provider_payload")"
provider_id="$(printf '%s' "$provider" | json_value "data['providerId']")"
printf '%s' "$provider" | assert_json "data['ownerUserId'] == '$merchant_id' and data['providerType'] == 'PET_STORE' and data['fulfillmentType'] == 'DELIVERY'"
pass "Merchant created a delivery store for barcode inventory"

second_provider_payload="$(python3 - "$merchant_id" "$run_suffix" <<'PY'
import json
import sys
merchant_id, suffix = sys.argv[1:3]
print(json.dumps({
    "ownerUserId": merchant_id,
    "providerType": "PET_STORE",
    "fulfillmentType": "DELIVERY",
    "name": f"Barcode Isolation Store {suffix}",
    "description": "Second provider used to verify store-scoped barcode lookup",
    "licenseNumber": f"BARCODE-ISO-{suffix}",
    "licenseDocUrl": None,
    "addressLine": "Barcode Isolation Road",
    "city": "Tirupati",
    "pincode": "517501",
    "longitude": 79.4292,
    "latitude": 13.6388,
}))
PY
)"
second_provider="$(curl -fsS "${MERCHANT_AUTH[@]}" -X POST "$GATEWAY_URL/api/v1/providers" \
  -H 'Content-Type: application/json' --data "$second_provider_payload")"
second_provider_id="$(printf '%s' "$second_provider" | json_value "data['providerId']")"
pass "A second merchant store was created for provider-isolation checks"

offering_payload="$(python3 - "$provider_id" "$ean_alias" "$sku" <<'PY'
import json
import sys
provider_id, barcode, sku = sys.argv[1:4]
print(json.dumps({
    "providerId": provider_id,
    "name": "Barcode E2E Adult Dog Food",
    "description": "Complete nutrition uploaded by scanning a product barcode.",
    "category": "Food & Nutrition",
    "price": 499.00,
    "imageUrl": "https://example.invalid/barcode-e2e-product.jpg",
    "status": "ACTIVE",
    "stockQuantity": 5,
    "sku": sku,
    "durationMinutes": None,
    "barcode": barcode,
}))
PY
)"
offering="$(curl -fsS "${MERCHANT_AUTH[@]}" -X POST "$GATEWAY_URL/api/v1/catalog/offerings" \
  -H 'Content-Type: application/json' --data "$offering_payload")"
product_id="$(printf '%s' "$offering" | json_value "data['offeringId']")"
printf '%s' "$offering" | assert_json "data['providerId'] == '$provider_id' and data['barcode'] == '$upc' and data['name'] == 'Barcode E2E Adult Dog Food' and float(data['price']) == 499.0 and data['stockQuantity'] == 5 and data['sku'] == '$sku'"
pass "Inventory upload canonicalized the EAN alias and persisted complete product details"

lookup="$(curl -fsS "${MERCHANT_AUTH[@]}" \
  "$GATEWAY_URL/api/v1/catalog/offerings/by-barcode?storeId=$provider_id&barcode=$ean_alias")"
printf '%s' "$lookup" | assert_json "data['offeringId'] == '$product_id' and data['barcode'] == '$upc' and data['description'].startswith('Complete nutrition') and data['category'] == 'Food & Nutrition' and data['imageUrl'].endswith('barcode-e2e-product.jpg') and data['stockQuantity'] == 5"
pass "Camera-equivalent EAN scan resolved the complete live product through the authenticated API"

tmp_body="$(mktemp)"
trap 'rm -f "$tmp_body"' EXIT
customer_status="$(http_request "$tmp_body" "${CUSTOMER_AUTH[@]}" \
  "$GATEWAY_URL/api/v1/catalog/offerings/by-barcode?storeId=$provider_id&barcode=$upc")"
[[ "$customer_status" == "403" ]] || fail "Customer barcode lookup should be forbidden, received HTTP $customer_status: $(cat "$tmp_body")"
pass "Customer identity cannot access merchant barcode lookup"

isolation_status="$(http_request "$tmp_body" "${MERCHANT_AUTH[@]}" \
  "$GATEWAY_URL/api/v1/catalog/offerings/by-barcode?storeId=$second_provider_id&barcode=$upc")"
[[ "$isolation_status" =~ ^4 ]] || fail "Cross-provider barcode lookup should fail, received HTTP $isolation_status: $(cat "$tmp_body")"
pass "Barcode lookup is isolated to the selected merchant provider"

duplicate_status="$(http_request "$tmp_body" "${MERCHANT_AUTH[@]}" -X POST \
  "$GATEWAY_URL/api/v1/catalog/offerings" -H 'Content-Type: application/json' --data "$offering_payload")"
[[ "$duplicate_status" =~ ^4 ]] || fail "Duplicate provider barcode should fail, received HTTP $duplicate_status: $(cat "$tmp_body")"
grep -Eqi 'barcode|already belongs|conflict' "$tmp_body" \
  || fail "Duplicate barcode response did not explain the conflict: $(cat "$tmp_body")"
pass "Duplicate UPC/EAN aliases are rejected within the provider inventory"

bill_payload="$(python3 - "$provider_id" "$spoofed_staff_id" "$product_id" "$ean_alias" "$idempotency_key" <<'PY'
import json
import sys
store_id, staff_id, product_id, barcode, key = sys.argv[1:6]
print(json.dumps({
    "storeId": store_id,
    "staffId": staff_id,
    "status": "FINALIZED",
    "subtotal": 0.01,
    "totalDiscount": 0.00,
    "tax": 0.00,
    "grandTotal": 0.01,
    "idempotencyKey": key,
    "items": [{
        "productId": product_id,
        "barcodeScanned": barcode,
        "quantity": 2,
        "unitPrice": 0.01,
        "discountAmount": 0.00,
        "discountType": "NONE",
    }],
}))
PY
)"
bill="$(curl -fsS "${MERCHANT_AUTH[@]}" -X POST "$GATEWAY_URL/api/v1/catalog/bills" \
  -H 'Content-Type: application/json' --data "$bill_payload")"
bill_id="$(printf '%s' "$bill" | json_value "data['bill']['id']")"
printf '%s' "$bill" | assert_json "data['bill']['staffId'] == '$merchant_id' and data['bill']['status'] == 'SYNCED' and abs(float(data['bill']['subtotal']) - 998.0) < 0.001 and abs(float(data['bill']['tax']) - 179.64) < 0.001 and abs(float(data['bill']['grandTotal']) - 1177.64) < 0.001 and len(data['successfulItems']) == 1 and len(data['failedItems']) == 0 and float(data['successfulItems'][0]['unitPrice']) == 499.0 and data['successfulItems'][0]['barcodeScanned'] == '$upc'"
pass "POS checkout ignored spoofed client price and staff identity, then used authoritative catalog data"

post_bill_lookup="$(curl -fsS "${MERCHANT_AUTH[@]}" \
  "$GATEWAY_URL/api/v1/catalog/offerings/by-barcode?storeId=$provider_id&barcode=$upc")"
printf '%s' "$post_bill_lookup" | assert_json "data['offeringId'] == '$product_id' and data['stockQuantity'] == 3"
pass "Barcode lookup returned fresh stock after atomic bill deduction"

retry_bill="$(curl -fsS "${MERCHANT_AUTH[@]}" -X POST "$GATEWAY_URL/api/v1/catalog/bills" \
  -H 'Content-Type: application/json' --data "$bill_payload")"
printf '%s' "$retry_bill" | assert_json "data['bill']['id'] == '$bill_id' and len(data['successfulItems']) == 1"
retry_lookup="$(curl -fsS "${MERCHANT_AUTH[@]}" \
  "$GATEWAY_URL/api/v1/catalog/offerings/by-barcode?storeId=$provider_id&barcode=$ean_alias")"
printf '%s' "$retry_lookup" | assert_json "data['stockQuantity'] == 3"
pass "Idempotent bill retry returned the original bill without deducting stock twice"

oversell_payload="$(python3 - "$provider_id" "$merchant_id" "$product_id" "$upc" "$run_suffix" <<'PY'
import json
import sys
store_id, staff_id, product_id, barcode, suffix = sys.argv[1:6]
print(json.dumps({
    "storeId": store_id,
    "staffId": staff_id,
    "status": "FINALIZED",
    "subtotal": 1996.00,
    "totalDiscount": 0.00,
    "tax": 359.28,
    "grandTotal": 2355.28,
    "idempotencyKey": f"barcode-oversell-{suffix}",
    "items": [{
        "productId": product_id,
        "barcodeScanned": barcode,
        "quantity": 4,
        "unitPrice": 499.00,
        "discountAmount": 0.00,
        "discountType": "NONE",
    }],
}))
PY
)"
oversell_status="$(http_request "$tmp_body" "${MERCHANT_AUTH[@]}" -X POST \
  "$GATEWAY_URL/api/v1/catalog/bills" -H 'Content-Type: application/json' --data "$oversell_payload")"
[[ "$oversell_status" =~ ^4 ]] || fail "Oversell checkout should fail, received HTTP $oversell_status: $(cat "$tmp_body")"
grep -Eqi 'stock|finalized' "$tmp_body" || fail "Oversell response did not identify stock failure: $(cat "$tmp_body")"
stock_after_oversell="$(curl -fsS "${MERCHANT_AUTH[@]}" \
  "$GATEWAY_URL/api/v1/catalog/offerings/by-barcode?storeId=$provider_id&barcode=$upc")"
printf '%s' "$stock_after_oversell" | assert_json "data['stockQuantity'] == 3"
pass "Oversell checkout was rejected without changing inventory"

wrong_barcode_payload="$(python3 - "$provider_id" "$merchant_id" "$product_id" "$run_suffix" <<'PY'
import json
import sys
store_id, staff_id, product_id, suffix = sys.argv[1:5]
print(json.dumps({
    "storeId": store_id,
    "staffId": staff_id,
    "status": "FINALIZED",
    "subtotal": 499.00,
    "totalDiscount": 0.00,
    "tax": 89.82,
    "grandTotal": 588.82,
    "idempotencyKey": f"barcode-mismatch-{suffix}",
    "items": [{
        "productId": product_id,
        "barcodeScanned": "999999999999",
        "quantity": 1,
        "unitPrice": 499.00,
        "discountAmount": 0.00,
        "discountType": "NONE",
    }],
}))
PY
)"
mismatch_status="$(http_request "$tmp_body" "${MERCHANT_AUTH[@]}" -X POST \
  "$GATEWAY_URL/api/v1/catalog/bills" -H 'Content-Type: application/json' --data "$wrong_barcode_payload")"
[[ "$mismatch_status" =~ ^4 ]] || fail "Mismatched barcode checkout should fail, received HTTP $mismatch_status: $(cat "$tmp_body")"
grep -Eqi 'barcode|match' "$tmp_body" || fail "Mismatched barcode response was not explicit: $(cat "$tmp_body")"
pass "A scanned barcode cannot be paired with a different product ID"

persisted="$("${COMPOSE[@]}" exec -T postgres psql -U postgres -d pawsnearme -Atc "
SELECT o.barcode || '|' || o.stock_quantity || '|' || bi.barcode_scanned || '|' || bi.unit_price
FROM catalog.offerings o
JOIN billing.bill_items bi ON bi.product_id = o.offering_id
JOIN billing.bills b ON b.id = bi.bill_id
WHERE o.offering_id = '$product_id'::uuid
  AND b.id = '$bill_id'::uuid;")"
[[ "$persisted" == "$upc|3|$upc|499.00" ]] \
  || fail "Unexpected persisted barcode/bill state: $persisted"
pass "Canonical barcode, authoritative price and remaining stock persisted in PostgreSQL"

printf '%s\n' "- ✅ Barcode inventory upload → scan lookup → POS checkout flow passed" >> "$REPORT"
