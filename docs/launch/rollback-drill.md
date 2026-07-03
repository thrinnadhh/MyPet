# Rollback Drill

## Owners

- Incident commander: launch owner
- Backend rollback owner: backend lead
- Mobile release owner: app release lead
- Database owner: platform/database lead
- Support owner: support lead

## Triggers

- Payment capture/refund mismatch
- Appointment double-booking or slot-lock failure
- Dispatch jobs stuck after reassignment
- Billing stock decrement/idempotency failure
- Auth bypass, IDOR, or privileged route exposure
- Severe notification spam or missed critical reminders

## Service Rollback Order

1. Pause app rollout or unlist release candidate from testing track.
2. Disable risky feature flag or admin config.
3. Roll back gateway route/config if auth or routing is involved.
4. Roll back affected backend service image.
5. Re-run verifier for impacted sprint.
6. Apply database rollback only when the new schema is not backward-compatible with the restored service.

## Drill Evidence

- Record date, owner, trigger simulated, services rolled back, verification command, result, and support communication.
