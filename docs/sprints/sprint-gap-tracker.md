# PawsNearMe Sprint Gap Tracker

This tracker reflects the current repo state after Sprint 0-11 checklist files and verification entrypoints were added. A sprint is complete only when code checks pass and live/manual proof is captured without hidden production mock success paths.

## Current Status

| Sprint | Theme | Repo Status | Remaining Gap |
| --- | --- | --- | --- |
| 0 | Foundations | ✅ Complete | Verified clean local infra migrations, Kafka, and Redis topics. |
| 1 | Identity, Auth, Provider Onboarding | ✅ Complete | Roles secured strictly under app_metadata. |
| 2 | Catalog + Discovery | ✅ Complete | Filter applied before Redis geo caching. |
| 3 | Order Creation + Payment Capture | ✅ Complete | Real refundPayment and payment webhooks validated. |
| 4 | Dispatch + Captain Delivery Loop | ✅ Complete | Concurrency timeout queries targeted directly. |
| 5 | Appointment Booking + Slot Locking | ✅ Complete | DB indexes added, slot reversion compensatory actions active. |
| 6 | Merchant Calendar + Reminders | ✅ Complete | Network I/O isolated outside active transactions. |
| 7 | Reviews, Payouts, Discount Controls | ✅ Complete | DB-level commission and payout aggregation completed. |
| 8 | Hardening, Admin Console, Billing | ✅ Complete | Gateway path filtering verified. |
| 9 | Legal, Store Submission, Launch | ✅ Repo Ready | Configured for production deployment. |
| 10 | Authorization Hardening | ✅ Complete | Strict path guards and ownership verification tested. |
| 11 | Payment Integrity | ✅ Complete | Webhook signature validation active. |
| 12 | Event Reliability (Outbox) | ✅ Complete | Outbox poller and transaction dual-write fully secured. |
| 13 | Service Hygiene | ✅ Complete | println() logs replaced with SLF4J loggers. |
| 18 | Observability & Readiness | ✅ Complete | Liveness/readiness probes configured, k6 baseline established. |
| 19 | Race Condition Fix | ✅ Complete | Optimistic locking on DispatchOffer resolved concurrency issues. |

## Verification Commands

- Baseline: `scripts/verify-all.sh`
- Live flows: `scripts/verify-all.sh --flows`
- Individual sprint checks: `python backend/verify_sprint<N>.py`
- Payment Integrity check: `python backend/verify_payments.py`
- Outbox & DLQ check: `python backend/verify_outbox_dlq.py`
- Concurrency & Optimistic Lock verification: `python backend/verify_sprint19.py`

## Production Acceptance Rule

Demo/offline fixtures are allowed only when `EXPO_PUBLIC_ALLOW_DEMO_MODE=true`. Production paths must fail visibly when backend services or required mobile environment variables are missing; they must not silently return mock success. Both mobile apps have been successfully configured for production with `EXPO_PUBLIC_ALLOW_DEMO_MODE=false`.

## Immediate Next Work

1. Complete App Store console submission (Google Play / Apple App Store).
2. Inject real production credentials to secret manager variables outside git.
