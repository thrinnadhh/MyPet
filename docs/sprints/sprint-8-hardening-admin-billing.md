# Sprint 8: Hardening, Admin Console, Billing

## Goal

The system can be operated, monitored, tested under load, and administered before launch.

## Acceptance Checklist

- [x] Super Admin API and admin console exist for approval queue, disputes, and commission config.
- [x] Load tests cover discovery, orders, appointments, and billing.
- [x] Security test covers auth bypass, IDOR, injection, and privileged routes.
- [x] Circuit breakers are configured for high-risk sync calls.
- [x] Prometheus/Grafana dashboards cover request rate, errors, latency, Kafka, Redis, and billing.
- [x] Backup/DR runbook is explicit for Supabase, Kafka, Redis, and service rollback order.
- [x] Billing screen submits or queues the current cart before clearing it.
- [x] Billing stock decrement is atomic and idempotent.
- [x] Billing endpoints are merchant-scoped by authenticated store context.

## Verification

- Run backend tests and mobile TypeScript.
- Run billing sync online and offline.
- Run concurrent low-stock billing test.

## Current Proof Notes

- Merchant/Captain app includes a Super Admin console at `/admin` for provider approvals, disputes, refund mode config, commission config, and support actions.
- Provider approval, provider commission update, dispute listing/resolution, dispute creation, refund mode config, and support case actions call backend endpoints through the configured API base URL.
- Support case persistence covers provider info requests, refund escalations, payout claim reviews, and customer callback tasks.
- Live local proof captured: provider `87a3e587-833f-45f3-9e0f-7ab09c35797a` moved `DRAFT -> PENDING_APPROVAL -> ACTIVE`; provider `11111111-1111-4111-8111-111111111201` commission updated through ADMIN endpoint and MERCHANT update was rejected; dispute `4b199941-8ea8-4527-b65e-ca116b361b3d` was created/resolved; support case `ff393af8-f778-4bc8-9b7f-a8de8ef158dd` was created/resolved.
- Load smoke script exists at `scripts/load-smoke-sprint8.py`; runbook exists at `docs/operations/sprint-8-hardening-runbook.md`; Grafana dashboard JSON exists at `docs/operations/grafana-sprint8-dashboard.json`.
