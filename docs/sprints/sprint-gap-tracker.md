# PawsNearMe Sprint Gap Tracker

This tracker reconciles the DOCX sprint roadmap with the current repo state. Use it as the repo source of truth until each sprint has its own detailed plan and verification script.

## Current Gap

The roadmap defines Sprint 0 through Sprint 9, but the repo only has:

- `sprint3-auth-booking-caching.md`, which mixes Sprint 1 auth, Sprint 3 ordering, Sprint 5 appointment locking, uploads, and caching.
- `backend/verify_sprint6.py`
- `backend/verify_sprint7.py`

That means Sprints 0, 1, 2, 4, 5, 6, 7, 8, and 9 are missing clear repo-level acceptance checklists, and Sprint 3 needs to be renamed or split.

## Sprint Status

| Sprint | Theme | Repo Status | Missing Acceptance Work |
| --- | --- | --- | --- |
| 0 | Foundations | Partial | CI skeleton, local infra bootstrap, migration runbook, Kafka/Redis topic setup checklist |
| 1 | Identity, Auth, Provider Onboarding | Partial | Supabase profile sync verification, document upload flow, provider approval checklist, gateway role matrix |
| 2 | Catalog + Discovery | Partial | Live mobile browse proof, Redis geo cache policy, ProviderApproved indexing verification |
| 3 | Order Creation + Payment Capture | Misfiled | Razorpay sandbox flow, payment capture/webhook verification, order event contract tests |
| 4 | Dispatch + Captain Delivery Loop | Partial | Captain offer expiry/retry proof, failed dispatch ops path, delivery proof flow |
| 5 | Appointment Booking + Slot Locking | Partial | Race/concurrency test, slot generation owner, pay-at-clinic/payment confirmation matrix |
| 6 | Merchant Calendar + Reminders | Partial | Reminder vendor decision, push/SMS proof, visit notes and prescription upload verification |
| 7 | Reviews, Payouts, Discount Controls | Partial | Rating aggregation proof, payout reconciliation, promotion authorization tests |
| 8 | Hardening, Admin Console, Billing Add-on | In progress | Super Admin web/API, load tests, security pass, billing sync/concurrency tests |
| 9 | Legal, Store Submission, Launch | Missing | Terms/privacy/refund pages, GST invoices, support/dispute workflow, app store checklist |

## Immediate Cleanup

1. Split `sprint3-auth-booking-caching.md` into sprint-specific files under `docs/sprints/`.
2. Add one verification command per sprint, even if it starts as a manual checklist.
3. Track hardening separately from feature sprints: auth boundary, event idempotency/DLQ, metrics/logging, backup/DR.
4. Make demo/mock fallbacks explicit dev-mode behavior and require live API proof for sprint completion.

## Definition Of Done For Future Sprints

Each sprint file should include:

- Goal and non-goals
- Backend, mobile, and infra tickets
- Acceptance tests or manual verification steps
- Required roles and authorization rules
- Events emitted/consumed, including idempotency key or `event_id`
- Known technical debt carried forward
