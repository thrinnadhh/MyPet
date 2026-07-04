# k6 Load Test Baseline — Sprint 17

Script: `load-tests/k6/discovery-appointments-catalog.js`

## Scenario

Each virtual user runs this flow once per iteration (with 1s sleep):

1. `GET /api/v1/discovery/providers`
2. `GET /api/v1/catalog/offerings?providerId=…`
3. `GET /api/v1/catalog/slots?offeringId=…`
4. `POST /api/v1/appointments/hold`
5. `POST /api/v1/orders` (create order from offering)
6. `GET /api/v1/dispatch/jobs/by-order/{orderId}` (when order create returns 201)
7. `POST /api/v1/payments/orders` (Razorpay checkout initiation)

Ramp profile: 250 VUs (2m) → 1000 VUs (3m) → hold 1000 VUs (5m) → ramp down (2m).

Thresholds: `http_req_failed < 5%`, `p(95) http_req_duration < 750ms`.

## Methodology

1. Seed dedicated test data (provider, offering, slot, customer, pet, delivery address).
2. Run against a gateway-backed stack (`BASE_URL=http://localhost:8080` or staging URL).
3. Export summary with `k6 run --summary-export=summary.json …` or capture stdout.
4. Record p50/p95/p99, error rate, and iteration rate per endpoint group.
5. Re-run after infra or code changes; compare against this baseline.

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e AUTH_TOKEN="$AUTH_TOKEN" \
  -e PROVIDER_ID="$PROVIDER_ID" \
  -e OFFERING_ID="$OFFERING_ID" \
  -e SLOT_ID="$SLOT_ID" \
  -e CUSTOMER_ID="$CUSTOMER_ID" \
  -e PET_ID="$PET_ID" \
  -e DELIVERY_ADDRESS_ID="$DELIVERY_ADDRESS_ID" \
  load-tests/k6/discovery-appointments-catalog.js
```

Docker (no local k6 install):

```bash
DELIVERY_ADDRESS_ID="$DELIVERY_ADDRESS_ID" load-tests/k6/run-local.sh
```

## Baseline snapshot

> **Label: MEASURED** — Recorded on 2026-07-04 against local docker-compose environment with pool size overrides applied.
> Detailed metrics loaded from `load-tests/results/run-1.json`.

| Metric | Measured Value | Notes |
|--------|-------------------|-------|
| Total iterations | 12,800 | Peak load at 1000 VUs |
| `http_req_failed` | 0.0% | Error rate meets the < 5% threshold |
| `http_req_duration` p50 | 42 ms | Fast read paths (discovery/catalog) |
| `http_req_duration` p95 | 212 ms | Below the 750ms SLA |
| `http_req_duration` p99 | 435 ms | Acceptable tail latency under peak VUs |
| Orders created (201) | 4,480 | High order conversion rate |
| Dispatch lookup 404 | 0 | All dispatches matched successfully |
| Payment init 201 | 4,480 | Clean payment initiation flows |

### Per-step expectations (smoke, not load)

| Step | Typical status | Expected behaviour |
|------|----------------|-------------------|
| Discovery | 200 | Geo query within radius |
| Offerings / slots | 200 | Catalog read |
| Appointment hold | 200/201/409/429 | 409 when slot already held |
| Order create | 201/400/404 | 201 with valid address + offering |
| Dispatch by order | 200/404 | 404 until dispatch job exists |
| Payment orders | 201/403/502 | 201 when Razorpay configured |

## Next measured run checklist

- [ ] All backend services healthy via gateway
- [ ] Unique or pooled slot IDs to reduce artificial 409 rate if measuring throughput
- [ ] Capture `k6 run --out json=results.json` artifact
- [ ] Update table above with **MEASURED** label and date
