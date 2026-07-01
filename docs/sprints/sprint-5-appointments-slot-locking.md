# Sprint 5: Appointments And Slot Locking

## Goal

A customer can book vet or grooming appointments with no double-booking.

## Acceptance Checklist

- [ ] Customer Vet/Groom booking flow includes pet, service, slot, and checkout.
- [ ] Slot hold and confirmation are separate states.
- [ ] Redis lock TTL and durable catalog slot status agree.
- [ ] Database uniqueness prevents two active appointments on the same slot.
- [ ] Appointment events include `event_id`.
- [ ] Slot generation job owner is documented.
- [ ] Dispatch and captain code paths are not involved in appointment booking.

## Verification

- Run concurrent booking test for the same slot.
- Confirm one customer succeeds and one receives a clear conflict.
- Confirm expired holds release the slot.
