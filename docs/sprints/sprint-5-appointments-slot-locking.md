# Sprint 5: Appointments And Slot Locking

## Goal

A customer can book vet or grooming appointments with no double-booking.

## Acceptance Checklist

- [x] Customer Vet/Groom booking flow includes pet identity, service, slot, and checkout/confirm state.
- [x] Slot hold and confirmation are separate states.
- [x] Redis hold TTL and durable catalog slot status are wired through the hold/confirm/expiry paths.
- [x] Database uniqueness prevents two active appointments on the same slot.
- [x] Appointment events include `event_id`, `occurred_at`, `actor_id`, and stable appointment/slot IDs.
- [x] Slot generation job owner is documented as catalog-service/provider operations seed data for Sprint 5 proof; recurring production generation remains a future scheduler hardening item.
- [x] Dispatch and captain code paths are not involved in appointment booking.

## Verification

- Customer app typecheck: passed with `npm run typecheck` from `apps/customer-app`.
- Customer app lint: passed with `npm run lint` from `apps/customer-app`.
- Appointment service tests: passed on 2026-07-03 IST with `./gradlew :appointment-service:test`.
- Static Sprint 5 verifier: passed with `python3 backend/verify_sprint5.py`.
- Live appointment proof: passed on 2026-07-03 IST with `.venv/bin/python backend/verify_appointments.py`.
- Live proof confirmed one customer held/confirmed the slot and the second customer received a clear conflict.
- Live proof confirmed expired holds release the slot back to `AVAILABLE`.
