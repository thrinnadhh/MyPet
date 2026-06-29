# Sprint 8: Hardening, Admin Console, Billing

## Goal

The system can be operated, monitored, tested under load, and administered before launch.

## Acceptance Checklist

- [ ] Super Admin API and web console exist for approval queue, disputes, and commission config.
- [ ] Load tests cover discovery, orders, appointments, and billing.
- [ ] Security test covers auth bypass, IDOR, injection, and privileged routes.
- [ ] Circuit breakers are configured for high-risk sync calls.
- [ ] Prometheus/Grafana dashboards cover request rate, errors, latency, Kafka, Redis, and billing.
- [ ] Backup/DR runbook is explicit for Supabase, Kafka, Redis, and service rollback order.
- [ ] Billing screen submits or queues the current cart before clearing it.
- [ ] Billing stock decrement is atomic and idempotent.
- [ ] Billing endpoints are merchant-scoped by authenticated store context.

## Verification

- Run backend tests and mobile TypeScript.
- Run billing sync online and offline.
- Run concurrent low-stock billing test.
