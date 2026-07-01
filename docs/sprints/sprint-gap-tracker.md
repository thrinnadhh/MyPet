# PawsNearMe Sprint Gap Tracker

This tracker reflects the current repo state after Sprint 0-9 checklist files and verification entrypoints were added. A sprint is complete only when code checks pass and live/manual proof is captured without hidden production mock success paths.

## Current Status

| Sprint | Theme | Repo Status | Remaining Gap |
| --- | --- | --- | --- |
| 0 | Foundations | Mostly complete | Run clean local infra/migrations and record Kafka/Redis topic proof |
| 1 | Identity, Auth, Provider Onboarding | Partial | Real Supabase signup/profile/address sync, document upload proof, admin approval proof |
| 2 | Catalog + Discovery | Partial | Live mobile proof for Shop, Vet, Groom with demo mode disabled |
| 3 | Order Creation + Payment Capture | In progress | Live checkout proof with default address, Razorpay sandbox capture/failure evidence, and stock rollback behavior |
| 4 | Dispatch + Captain Delivery Loop | Partial | End-to-end offer expiry/retry, pickup, delivery, and earnings proof |
| 5 | Appointment Booking + Slot Locking | Partial | Concurrent same-slot booking proof and expired hold release proof |
| 6 | Merchant Calendar + Reminders | Partial | Real Expo/FCM credentials, visit notes/prescription upload decision, delivery evidence |
| 7 | Reviews, Payouts, Discount Controls | Partial | Live payout reconciliation and merchant/captain UI proof |
| 8 | Hardening, Admin Console, Billing | In progress | Super Admin web/API, load tests, dashboards, backup/DR runbook |
| 9 | Legal, Store Submission, Launch | Missing | Legal pages, GST invoice proof, store listings, production secret checklist, rollback drill |

## Verification Commands

- Baseline: `scripts/verify-all.sh`
- Live flows: `scripts/verify-all.sh --flows`
- Individual sprint checks: `python backend/verify_sprint<N>.py`

## Production Acceptance Rule

Demo/offline fixtures are allowed only when `EXPO_PUBLIC_ALLOW_DEMO_MODE=true`. Production paths must fail visibly when backend services or required mobile environment variables are missing; they must not silently return mock success.

## Immediate Next Work

1. Seed or create a real default customer address, then capture the Sprint 3 checkout success/failure proof through the customer app.
2. Capture live proof for Sprints 1-5 using local infra and real backend services.
3. Configure real Expo/FCM reminder credentials or explicitly defer push delivery from launch scope.
4. Add legal pages and app-store launch artifacts for Sprint 9.
