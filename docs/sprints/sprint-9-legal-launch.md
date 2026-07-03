# Sprint 9: Legal, Store Submission, Phased Launch

## Goal

Both apps can launch legally and operationally in one locality with rollback plans.

## Acceptance Checklist

- [x] Terms of Service, Privacy Policy, and Refund/Cancellation Policy are published and linked in-app.
- [x] GST-compliant invoice behavior is documented for orders, appointments, and in-store bills.
- [x] Support/dispute workflow lets staff resolve issues without raw SQL.
- [x] Empty states handle no providers, no orders, no appointments, and unavailable services.
- [x] Production Razorpay, APNs, FCM, Supabase, Kafka, and Redis secrets have a repo checklist without committed secret values.
- [x] App Store and Play Store listings, screenshots, data safety, and privacy disclosures are ready as launch artifacts.
- [x] Soft launch is limited to one city/locality.
- [x] Rollback plan names trigger conditions, owner, and service rollback order.

## Verification

- Submit release candidates to internal testing.
- Complete launch readiness checklist and rollback drill.
- Static repo proof: `python backend/verify_sprint9.py`.

## Proof Captured

- Passed on 2026-07-03 IST: `.venv/bin/python backend/verify_sprint9.py` with `20 passed, 0 failed`.
- Passed on 2026-07-03 IST: `./gradlew :appointment-service:test`.
- Passed on 2026-07-03 IST: both mobile apps `npm run typecheck` and `npm run lint`.
- Legal and support policy artifacts live under `docs/legal/`.
- Store listing, data safety, production secrets, soft-launch, rollback, and GST proof artifacts live under `docs/launch/`.
- Customer app profile links to the legal route at `apps/customer-app/src/app/legal.tsx`.
- Merchant/captain app home links to the legal route at `apps/merchant-captain-app/src/app/legal.tsx`.
- Appointment invoices are generated when an appointment reaches `COMPLETED`, with a migration, API endpoint, and unit tests.

## Launch Gates Outside Git

- Real production/staging secret values must be configured in the deployment environment, not committed.
- App Store and Play Store internal testing submission must be completed in the store consoles.
- Rollback drill must be run against the selected staging or production-like environment.
- Razorpay sandbox evidence and Expo/FCM delivery evidence remain production-launch proof items tracked in the gap tracker.
- Full `scripts/verify-all.sh` release regression was not completed in this run because Gradle cache access escalation was blocked by the approval/usage limit.
