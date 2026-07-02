# Sprint 5 Appointment Slot Locking Plan

## Goal

Complete Sprint 5 by proving Vet and Groom appointment booking uses a real two-stage hold/confirm flow, prevents double booking, releases expired holds, and does not involve dispatch or captain code paths.

## Tasks

- [ ] Add appointment event contract fields in `AppointmentService`: `event_id`, `occurred_at`, `actor_id`, `appointment_id`, and `slot_id` where relevant. Verify with appointment service tests and `rg "event_id|occurred_at|actor_id" backend/appointment-service`.
- [ ] Add a durable uniqueness guard for one active appointment per slot, covering `SLOT_HELD` and `CONFIRMED` while allowing `CANCELLED` and `EXPIRED`. Verify with a repository/service test that only one active appointment can exist for the same slot.
- [ ] Change slot holds to keep a Redis hold key with TTL until confirm or expiry, while catalog slot status remains `HELD`. Verify that hold creates a TTL key, confirm clears it and books the slot, and conflict returns a clear error.
- [ ] Harden expired hold release in both confirm-time expiry and scheduled cleanup. Verify expired holds become `EXPIRED`, Redis hold keys are gone, and catalog slots return to `AVAILABLE`.
- [ ] Wire customer Vet and Groom booking UI to the live hold/confirm flow with pet, service, slot, and checkout or pay-at-clinic state. Verify with `EXPO_PUBLIC_ALLOW_DEMO_MODE=false` that the UI cannot silently succeed with demo data.
- [ ] Add focused Sprint 5 backend tests for concurrent one-winner booking, expired hold release, event contract fields, and no dispatch/captain involvement. Verify with `./gradlew :appointment-service:test`.
- [ ] Extend `backend/verify_appointments.py` into the Sprint 5 live proof: create two customers for one slot, hold/confirm one winner, prove the other gets conflict, then prove expired release. Verify with `.venv/bin/python backend/verify_appointments.py`.
- [ ] Update `backend/verify_sprint5.py`, `docs/sprints/sprint-5-appointments-slot-locking.md`, and the sprint gap tracker only after proof exists. Verify with `.venv/bin/python backend/verify_sprint5.py`.
- [ ] Run final Sprint 5 verification: appointment tests, customer app typecheck/lint, static Sprint 5 verifier, and live appointment verifier. Record the live proof run ID in the Sprint 5 doc.

## Done When

- [ ] A customer can book Vet and Groom appointments through live backend data.
- [ ] Slot hold and confirmation are separate states with TTL-backed expiry.
- [ ] Concurrent booking of one slot has exactly one winner.
- [ ] Expired holds release the slot for another customer.
- [ ] Appointment events include stable IDs and timestamps.
- [ ] Sprint 5 verification passes without manual caveats or hidden mock fallback behavior.

## Notes

- Keep dispatch and captain services out of Sprint 5.
- Use the existing multi-service backend structure.
- Demo/offline behavior may remain only behind explicit development flags.
