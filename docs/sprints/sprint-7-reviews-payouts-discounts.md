# Sprint 7: Reviews, Payouts, Discount Controls

## Goal

Ratings, payouts, and promotion governance are reliable enough for real-money operation.

## Acceptance Checklist

- [x] Review creation updates provider aggregate rating.
- [x] Duplicate reviews are rejected.
- [x] Payout batch creates merchant and captain payouts for a period.
- [x] Captain earnings link to payout records.
- [x] Promotion creation enforces discount-war-prevention rules.
- [x] Platform-wide promotions are admin-only.
- [x] Light security review covers auth, authorization, IDOR, and validation.
- [x] Shared mobile component rules are documented for new screens.

## Verification

- `python backend/verify_sprint7.py`
- Manually verify merchant/captain earnings screens against backend data.

## Proof Captured

- Passed on 2026-07-03 IST: `./gradlew :review-service:test :provider-service:test :payment-service:test :api-gateway:test :captain-service:test`.
- Passed on 2026-07-03 IST: merchant/captain app `npm run typecheck` and `npm run lint`.
- Passed on 2026-07-03 IST: customer app `npm run typecheck` and `npm run lint`.
- Passed on 2026-07-03 IST: `.venv/bin/python backend/verify_sprint7.py` with `20 passed | 0 failed | 0 skipped`.

## Remaining Proof

- Manually verify the merchant/captain Earnings UI with `EXPO_PUBLIC_ALLOW_DEMO_MODE=false`.
- Local note: provider-service required `SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false` during live proof because the existing local database has an old Flyway checksum for provider migration V2. This is local schema-history drift, not a Sprint 7 verifier failure.

## Mobile Component Rule

- Sprint 7 reuses the merchant app's existing themed primitives (`ThemedView`, `ThemedText`, `Spacing`, and `Colors`) and keeps backend calls in the Earnings screen until a broader shared earnings service is introduced.
- Demo rows are allowed only when `EXPO_PUBLIC_ALLOW_DEMO_MODE=true`; production failures must surface as visible error states.
