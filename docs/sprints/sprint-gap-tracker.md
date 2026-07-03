# PawsNearMe Sprint Gap Tracker

This tracker reflects the current repo state after Sprint 0-11 checklist files and verification entrypoints were added. A sprint is complete only when code checks pass and live/manual proof is captured without hidden production mock success paths.

## Current Status

| Sprint | Theme | Repo Status | Remaining Gap |
| --- | --- | --- | --- |
| 0 | Foundations | Mostly complete | Run clean local infra/migrations and record Kafka/Redis topic proof |
| 1 | Identity, Auth, Provider Onboarding | Complete for sprint scope | Re-run auth sync/profile onboarding proof during full release regression |
| 2 | Catalog + Discovery | Complete for sprint scope | Re-run live Shop, Vet, and Groom proof during full release regression |
| 3 | Order Creation + Payment Capture | Complete for sprint scope | Re-run UI checkout proof during full release regression; Razorpay sandbox integration resolved in Sprint 11 |
| 4 | Dispatch + Captain Delivery Loop | Complete for sprint scope | Re-run live expiry/reassignment, pickup, delivery, and earnings proof during full release regression |
| 5 | Appointment Booking + Slot Locking | Complete for sprint scope | Re-run appointment-service tests and live appointment proof during full release regression |
| 6 | Merchant Calendar + Reminders | Complete for sprint scope | Real Expo/FCM push-token registration and vendor delivery evidence before production launch |
| 7 | Reviews, Payouts, Discount Controls | Live verifier green | Manual merchant/captain Earnings UI proof with demo mode off |
| 8 | Hardening, Admin Console, Billing | Complete for sprint scope | Re-run load smoke during full release regression and archive p95 results |
| 9 | Legal, Store Submission, Launch | Repo artifacts implemented | Store-console submission, real production secret configuration, and rollback drill evidence outside git |
| 10 | Authorization Hardening | Complete for sprint scope | Re-run Gateway filter checks and verify dispatch OTP flow with mobile apps |
| 11 | Payment Integrity | Complete for sprint scope | Re-run verification script against gateway and verify webhook workflows |
| 12 | Event Reliability (Outbox) | Complete | Transactional outbox, poller, idempotency, and DLQ routing fully implemented and verified |

## Verification Commands

- Baseline: `scripts/verify-all.sh`
- Live flows: `scripts/verify-all.sh --flows`
- Individual sprint checks: `python backend/verify_sprint<N>.py`
- Payment Integrity check: `python backend/verify_payments.py`
- Outbox & DLQ check: `python backend/verify_outbox_dlq.py`

## Production Acceptance Rule

Demo/offline fixtures are allowed only when `EXPO_PUBLIC_ALLOW_DEMO_MODE=true`. Production paths must fail visibly when backend services or required mobile environment variables are missing; they must not silently return mock success.

## Immediate Next Work

1. Configure real Expo/FCM reminder credentials and push-token registration, then capture reminder delivery evidence.
2. Re-run Sprint 1-5 proof during release regression, including Razorpay sandbox evidence for Sprint 3.
3. Complete Sprint 7 merchant/captain Earnings UI proof with `EXPO_PUBLIC_ALLOW_DEMO_MODE=false`.
4. Run Sprint 9 store-console/internal-testing and rollback-drill proof outside git.
