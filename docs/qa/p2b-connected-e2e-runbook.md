# P2B Order 11 — Connected end-to-end certification

## Authority

The existing clean-volume M8 graph remains the primary connected runtime. Order 11 adds an exact ten-journey release contract and extends that graph with recurring-order, private medical-document and customer-case probes. It does not replace M8 with disconnected mocks.

## Exact journeys

The authoritative machine-readable list is `qa/p2b-connected-journeys.json`:

1. Guest browsing → OTP/auth intent → checkout
2. Merchant creates product → customer discovers it
3. Customer pays → merchant receives the order
4. Merchant marks ready → dispatch creates an offer
5. Captain accepts → pickup proof → delivery proof
6. Delivered order → loyalty → review
7. Appointment booking → provider completion
8. Private medical upload → customer signed view
9. Customer case/evidence → admin resolution/refund lifecycle
10. Push payload → authenticated role-scoped deep link

Every journey declares HTTP, database, event, notification, UI-contract and idempotency evidence. The static checker rejects a missing dimension.

## Runtime sequence

`bash scripts/test-all.sh` performs:

1. Backend/mobile build and static gates
2. Clean-volume distributed stack start
3. Existing feature-flow checks
4. Contract-aware M8 fourteen-domain runtime
5. P2B persisted-graph verification
6. Recurring-order creation, duplicate rejection, pause and resume
7. Customer-case creation, private evidence upload, one-time token rejection and admin resolution
8. Private medical upload, signed-link issue and content round-trip
9. Source-level UI, role-guard and deep-link contract checks
10. One combined Markdown certification report

## Failure behavior

Any failed HTTP status, missing database row, missing published outbox evidence, absent M8 domain result, broken UI contract or failed idempotency assertion terminates the clean-stack workflow. The stack diagnostics from the M8 wrapper remain available for debugging.

## Device boundary

Journey 10 proves notification payload, token registration, auth-intent restoration and role-guard routing in automated integration. Physical push receipt/tap, TalkBack/VoiceOver and manufacturer-specific background behavior remain evidence-gated by Order 10. The connected test must never mark those physical checks as passed.

## Commands

Static contract:

```bash
python scripts/check-p2b-connected-e2e.py
```

Full connected certification:

```bash
bash scripts/test-all.sh
```
