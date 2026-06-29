# Sprint 7: Reviews, Payouts, Discount Controls

## Goal

Ratings, payouts, and promotion governance are reliable enough for real-money operation.

## Acceptance Checklist

- [ ] Review creation updates provider aggregate rating.
- [ ] Duplicate reviews are rejected.
- [ ] Payout batch creates merchant and captain payouts for a period.
- [ ] Captain earnings link to payout records.
- [ ] Promotion creation enforces discount-war-prevention rules.
- [ ] Platform-wide promotions are admin-only.
- [ ] Light security review covers auth, authorization, IDOR, and validation.
- [ ] Shared mobile component rules are documented for new screens.

## Verification

- `python backend/verify_sprint7.py`
- Manually verify merchant/captain earnings screens against backend data.
