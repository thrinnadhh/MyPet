# MyPet Merchant — Play Store Certification

Decision: **NO-GO (provisional certification)**

Integration PR: #97
Orchestrator: #96
Parent merchant hardening: #88

This document is intentionally fail-closed. A readiness percentage is not release authorization. The final GO decision requires the exact signed Android App Bundle commit to pass the automated, physical-device, policy, and closed-testing gates below.

## Provisional score

Current evidence-weighted code readiness: **88.2 / 100**.

| Domain | Weight | Current evidence score | Weighted contribution | Status |
|---|---:|---:|---:|---|
| Security / authentication / RBAC | 15% | 92 | 13.80 | Strong candidate |
| Orders / payment / stock / concurrency | 20% | 94 | 18.80 | Strong candidate |
| Catalog / inventory / barcode / POS | 15% | 89 | 13.35 | Public product-media gap remains |
| Onboarding / store management | 10% | 86 | 8.60 | Post-onboarding persisted profile now implemented; full document/contact operational coverage remains |
| Appointments | 8% | 87 | 6.96 | Server-backed; device/operational validation remains |
| Recurring orders | 8% | 92 | 7.36 | Revalidation/no-silent-charge/cart rebuild contracts implemented; exact final E2E must remain green |
| Finance / payouts | 8% | 75 | 6.00 | Core reconciliation fixtures exist; production ledger/payout evidence remains |
| Notifications / deep links / offline | 6% | 85 | 5.10 | Tap/cold-start/token lifecycle implemented; physical Android evidence missing |
| Accessibility / physical-device QA | 5% | 80 | 4.00 | Contract checks exist; real-device release matrix not complete |
| Release / Play Store configuration | 5% | 85 | 4.25 | Merchant AAB profile/policy gates exist; signed AAB/closed testing/legal URLs are not yet evidenced |
| **Total** | **100%** |  | **88.22** | **NO-GO** |

## Evidence implemented on the orchestrator branch

### Merchant release boundary

- explicit `merchant` vs `operations` variants;
- Merchant identity `MyPet Merchant`, Android package `com.mypet.merchant`;
- Play production profile emits an AAB;
- Merchant build blocks captain-only `ACCESS_BACKGROUND_LOCATION` and `FOREGROUND_SERVICE_LOCATION`;
- foreground location remains available for storefront/clinic verification;
- Merchant build accepts/provisions only trusted merchant operational roles;
- editable user metadata is never authorization evidence;
- demo mode is disabled in release profiles;
- production API must use HTTPS.

### Merchant operations

- live dashboard/order/catalog/booking foundations from PR #88 retained;
- owner-scoped post-onboarding business profile mutation added;
- provider type, fulfilment type, approval status, commission and licence identity remain locked from merchant self-edit;
- cross-merchant provider-profile writes are rejected;
- Store and Subscriptions are present in default, native and web navigation registries;
- barcode/POS server authority and idempotent stock behavior remain part of the connected gates.

### Recurring-order safety

- cadence remains 7 / 15 / 25 / 30 / 35 days;
- no silent prepaid charging is stated and enforced by the normal payment lifecycle;
- merchant/serviceability/stock/price are revalidated;
- migrated recurring confirmation rebuilds the cart only from revalidated server results;
- checkout still creates a fresh authoritative quote;
- Merchant recurring demand remains read-only to customer terms.

### Notifications

- response listener handles notification taps while the app is running;
- cold-start notification response is handled;
- merchant notification templates route to Orders, Bookings, Subscriptions or Finance;
- push token is unregistered on sign-out;
- actual foreground/background/killed-app delivery receipts still require signed physical-device evidence.

### Play policy gates

- production Merchant config requires a real HTTPS privacy-policy URL;
- production Merchant config requires a real HTTPS external account-deletion URL;
- Legal/Account screen exposes both resources prominently;
- no placeholder URL is invented;
- release remains blocked until the real resources and corresponding Play Console declarations are verified.

### Dependency security

The mobile dependency gate rejects all high/critical advisories except the exact temporary unpatched `image-size` advisory chain through Metro build tooling. The exception:

- is limited to the exact two reviewed GHSA identifiers;
- rejects application-source imports of `image-size`;
- rejects any new high/critical advisory;
- requires the expected Metro build-tool chain;
- automatically expires on **2026-09-01**.

## Automated convergence gate

The exact final SHA must have all required workflows green. Earlier intermediate heads already demonstrated green Connected E2E, Barcode E2E, Internal Beta configuration, and device/accessibility contract runs at different points, but **only the final exact SHA counts for release**.

Required final workflows include at least:

- P2B Connected E2E Contract;
- Barcode Scanner E2E;
- Full Stack Smoke / modular-monolith certification;
- MyPet Internal Beta / Merchant rendered-config validation;
- Customer Complete E2E where merchant changes depend on customer behavior;
- P2B Device QA contract validation;
- any required Java/mobile CI and security gates configured on the final integration path.

## Remaining P0/P1 release blockers

1. **Final CI convergence** — all required workflows must be green on the exact final commit.
2. **Public catalog media** — customer-visible product/service image lifecycle needs a production-safe public object-storage/media path; protected KYC/document uploads must not be reused as public catalog media.
3. **Finance production reconciliation** — reconcile a staging/production-like provider ledger, delivered orders, completed appointments and merchant-account payouts against the Merchant Finance screen.
4. **Physical Android notification evidence** — foreground, background and cold-start receipt/tap/deep-link behavior.
5. **Physical barcode/camera evidence** — permission grant/deny/permanent-denial recovery and real scan on a signed build.
6. **Offline/restart evidence** — queue bill offline, restart, reconnect, idempotent sync, stale-stock rejection.
7. **Real privacy-policy HTTPS resource** — legal-approved and accessible publicly.
8. **Real external account-deletion HTTPS resource** — functional without reinstalling the app and able to initiate account/data deletion.
9. **Signed Merchant AAB** — build from the exact final SHA with production signing credentials.
10. **Play closed-test install/update** — install the AAB through the Play track and verify an upgrade from the previous versionCode path.
11. **Play Console declarations** — Data safety, app access/reviewer instructions, content rating, support contact, privacy policy, account deletion, screenshots/listing and any required test account.
12. **Independent Lane F certification** — zero open P0/P1 defects and overall evidence-weighted score >=95.

## Target score after blocker closure

| Domain | Target |
|---|---:|
| Security / auth / RBAC | >=95 |
| Orders / payment / stock / concurrency | >=97 |
| Catalog / inventory / POS | >=95 |
| Onboarding / store management | >=95 |
| Appointments | >=95 |
| Recurring orders | >=95 |
| Finance / payouts | >=95 |
| Notifications / deep links / offline | >=95 |
| Accessibility / device QA | >=95 |
| Release / Play Store configuration | 100% mandatory gates |
| **Overall** | **>=95 and zero P0/P1** |

## Final GO rule

The orchestrator may change this document to **GO** only when:

1. all six specialist lanes #90–#95 are closed with evidence;
2. issue #96 has zero unresolved P0/P1 blockers;
3. all required workflows are green on the exact release SHA;
4. the signed Merchant AAB identity/versionCode is recorded;
5. physical-device evidence is complete;
6. privacy/deletion/Data Safety/Play listing evidence is complete;
7. Lane F independently reruns the feature matrix and records >=95% overall readiness.
