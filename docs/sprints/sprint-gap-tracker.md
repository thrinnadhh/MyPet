# PawsNearMe Sprint Gap Tracker

This tracker reflects the current repo state after Sprint 0-9 checklist files and verification entrypoints were added. A sprint is complete only when code checks pass and live/manual proof is captured without hidden production mock success paths.

## Current Status

| Sprint | Theme | Repo Status | Remaining Gap |
| --- | --- | --- | --- |
| 0 | Foundations | Mostly complete | Run clean local infra/migrations and record Kafka/Redis topic proof |
| 1 | Identity, Auth, Provider Onboarding | Complete for sprint scope | Re-run auth sync/profile onboarding proof during full release regression |
| 2 | Catalog + Discovery | Complete for sprint scope | Re-run live Shop, Vet, and Groom proof during full release regression |
| 3 | Order Creation + Payment Capture | Complete for sprint scope | Re-run UI checkout proof during full release regression and replace sandbox-equivalent proof with Razorpay sandbox evidence before production launch |
| 4 | Dispatch + Captain Delivery Loop | Complete for sprint scope | Re-run live expiry/reassignment, pickup, delivery, and earnings proof during full release regression |
| 5 | Appointment Booking + Slot Locking | Complete for sprint scope | Re-run appointment-service tests and live appointment proof during full release regression |
| 6 | Merchant Calendar + Reminders | Partial | Real Expo/FCM credentials, visit notes/prescription upload decision, delivery evidence |
| 7 | Reviews, Payouts, Discount Controls | Partial | Live payout reconciliation and merchant/captain UI proof |
| 8 | Hardening, Admin Console, Billing | Complete for sprint scope | Re-run load smoke during full release regression and archive p95 results |
| 9 | Legal, Store Submission, Launch | Missing | Legal pages, GST invoice proof, store listings, production secret checklist, rollback drill |

## Verification Commands

- Baseline: `scripts/verify-all.sh`
- Live flows: `scripts/verify-all.sh --flows`
- Individual sprint checks: `python backend/verify_sprint<N>.py`

## Production Acceptance Rule

Demo/offline fixtures are allowed only when `EXPO_PUBLIC_ALLOW_DEMO_MODE=true`. Production paths must fail visibly when backend services or required mobile environment variables are missing; they must not silently return mock success.

## Immediate Next Work

1. Configure real Expo/FCM reminder credentials or explicitly defer push delivery from launch scope.
2. Add legal pages and app-store launch artifacts for Sprint 9.
3. Re-run Sprint 1-5 proof during release regression, including Razorpay sandbox evidence for Sprint 3.
