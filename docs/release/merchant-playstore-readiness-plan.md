# MyPet Merchant — Play Store Readiness Orchestration Plan

Status: **NO-GO until the final certification gate passes**

Integration branch: `agent/merchant-playstore-readiness`
Parent hardening branch: `agent/merchant-customer-production-readiness` (PR #88)
Orchestrator: issue #96

## Objective

Raise the Merchant application from the audited pre-release baseline to an evidence-backed Google Play production candidate. The target is not a cosmetic score: every critical merchant workflow must be server-authoritative, cross-app consistent, regression tested, physically validated on Android, and reproducible from the exact signed release commit.

## Operating model

The work runs as six specialist lanes under one orchestrator.

| Lane | Issue | Ownership | Can run in parallel with |
|---|---:|---|---|
| A | #90 | Auth, RBAC, Merchant build identity/permissions | E |
| B | #91 | Store management, catalog, finance | C, E |
| C | #92 | Orders, appointments, recurring orders | B, E |
| D | #93 | Notifications, deep links, offline, physical devices | late B/C, E |
| E | #94 | CI, release gate, Play Store packaging/policy | A–D |
| F | #95 | Independent regression/performance/accessibility certification | none until A–E converge |

The orchestrator owns merge order, conflict resolution, severity, score calculation and the final GO/NO-GO. A specialist lane cannot self-certify release.

## Phase 0 — Baseline and invariants

Before feature work, preserve these invariants:

1. Identity and operational roles are server-controlled.
2. A Merchant may mutate only providers they own.
3. Backend/database state is authoritative for price, stock, payment, order, booking, payout and subscription state.
4. Production code never substitutes demo/fabricated records when live data is unavailable.
5. Money or inventory mutations are idempotent where retries are expected.
6. Customer ↔ Merchant ↔ Admin/Captain views must converge on one authoritative state.
7. Release evidence belongs to the exact final commit and signed artifact.

## Phase 1 — Lane A: merchant-only release boundary

Target score: **95%+**

Required work:

- finish trusted-role migration and fail closed for missing/unknown claims;
- preserve self-service provisioning only through protected server-side promotion;
- create a dedicated Merchant Play Store build variant from the operational codebase;
- give the merchant artifact a distinct name, scheme and package ID;
- remove captain-only background location/foreground-service permissions from the merchant artifact;
- keep foreground location only for merchant storefront/clinic verification;
- hide captain signup in the Merchant variant;
- reject Captain/Admin identities in the Merchant variant even if a route is manually deep-linked;
- forbid demo mode in release builds;
- require HTTPS backend configuration in production.

Gate:

```text
Merchant rendered Expo config
  -> name = MyPet Merchant
  -> package = com.mypet.merchant
  -> appVariant = merchant
  -> ACCESS_BACKGROUND_LOCATION blocked
  -> FOREGROUND_SERVICE_LOCATION blocked
  -> demo mode false
```

## Phase 2 — Lanes B and C in parallel

### Lane B — store/catalog/finance

Target score: **95%+**

Close:

- post-onboarding store profile editing;
- supported business contact/location/document changes;
- truthful KYC/GST/settlement fields only after backend persistence exists;
- complete retail/service catalog fields and validation;
- large catalog pagination/search;
- ownership isolation across multiple providers;
- provider revenue vs merchant-account payout semantics;
- finance reconciliation fixtures against authoritative database/ledger values.

Finance is not certified by rendering totals. Certification requires exact ledger/database reconciliation for known fixtures.

### Lane C — orders/appointments/recurring

Target score: **97% orders, 95% appointments/subscriptions**

Close:

- order detail completeness;
- accept/reject/preparing/ready state machine;
- payment-state gating;
- stock reservation/restoration;
- stale customer-cancel vs merchant-accept races;
- appointment hold/double-book prevention/terminal states/invoices;
- recurring cadences 7/15/25/30/35;
- pause/resume/skip/cancel/reactivate;
- one occurrence -> at most one order;
- current merchant/serviceability/stock/price revalidation for every occurrence;
- no silent prepaid charging;
- generated recurring order must use the normal order/payment lifecycle;
- merchant recurring-demand projection must match scheduler/order state.

## Phase 3 — Lane D: device and recovery evidence

Target score: **95%+**

Run only after the merchant release identity and core journeys are stable.

Minimum Android matrix:

- one Android 10/11-class device or emulator for lower supported behavior;
- one Android 13 device for notification-permission behavior;
- one Android 14/15+ device for current foreground/background restrictions;
- at least one physical device with a real camera/barcode scan.

Required scenarios:

- sign in/session restore/sign out;
- merchant onboarding foreground location grant/deny/recovery;
- camera grant/deny/permanent denial/recovery;
- incoming order in foreground;
- incoming order in background;
- notification tap from killed app;
- correct deep link to Orders/appointment context;
- offline POS checkout queue;
- restart while bills are pending;
- reconnect and idempotent synchronization;
- stale stock rejection after reconnect;
- accessibility/touch-target checks.

Each result records device, Android version, build SHA, build profile and outcome.

## Phase 4 — Lane E: release engineering and Play Store gate

Target: **100% of required gates**

Production artifact requirements:

- Android App Bundle (AAB), not APK;
- store distribution profile;
- immutable version/versionCode strategy;
- production API/Supabase environment;
- demo mode disabled;
- no placeholder secrets/configuration;
- minimal justified permissions;
- exact Git commit recorded;
- closed-test install and upgrade path verified.

Security dependency policy:

- all new high/critical advisories fail the build;
- temporary exceptions require exact advisory IDs, non-runtime reachability evidence, an owner/reason, and an automatic expiry;
- expired exceptions fail automatically.

Play Console checklist:

- MyPet Merchant app name/package;
- icon/adaptive icon/splash;
- short/full description;
- phone/tablet screenshots as required;
- support contact;
- privacy-policy URL;
- Data safety answers derived from actual collection/sharing behavior;
- account deletion/data deletion path where required by product behavior;
- test-account/reviewer instructions when login is required;
- content rating and app-access declarations;
- closed-testing track evidence before production promotion.

## Phase 5 — Lane F: independent final certification

Lane F reruns the system from a clean state after A–E converge.

Required feature matrix:

1. Authentication/session
2. RBAC/provider isolation
3. Merchant onboarding
4. Store management
5. Dashboard
6. Catalog
7. Inventory
8. Barcode
9. POS/offline billing
10. Orders
11. Order concurrency/idempotency
12. Vet/grooming appointments
13. Slot integrity/invoices
14. Recurring orders
15. Finance
16. Payouts
17. Notifications/deep links
18. Chat/privacy
19. Health guides
20. Legal/settings/account state
21. Accessibility/performance
22. Release configuration/device evidence

Any P0/P1 found by Lane F reopens the owning lane and returns the overall decision to NO-GO.

## Scoring model

Scores are evidence-weighted rather than feature-count weighted.

| Domain | Weight |
|---|---:|
| Security/auth/RBAC | 15% |
| Orders/payment/stock/concurrency | 20% |
| Catalog/inventory/POS | 15% |
| Onboarding/store management | 10% |
| Appointments | 8% |
| Recurring orders | 8% |
| Finance/payouts | 8% |
| Notifications/offline/deep links | 6% |
| Accessibility/device QA | 5% |
| Release/Play Store configuration | 5% |

A weighted score >=95% is necessary but not sufficient. Release is still blocked by any mandatory blocker below.

## Mandatory blockers

Play Store production promotion is forbidden while any of these remains:

- open P0/P1 defect;
- untrusted/self-grantable operational role;
- cross-provider authorization failure;
- fake/demo merchant data reachable in production;
- captain-only background-location permission in Merchant artifact;
- payment/stock/finance reconciliation defect;
- recurring silent charge or duplicate occurrence/order;
- red required CI on final SHA;
- missing signed AAB or closed-test install evidence;
- incomplete physical-device matrix;
- incomplete privacy/legal/Data safety approval.

## Final artifact

The orchestrator produces `docs/release/merchant-playstore-certification.md` containing:

- final commit SHA;
- AAB identity/versionCode;
- each feature score;
- automated test evidence;
- physical-device evidence;
- open P2/P3 risks;
- rollback/support runbook references;
- explicit **GO** or **NO-GO**.
