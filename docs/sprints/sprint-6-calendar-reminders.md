# Sprint 6: Merchant Calendar And Reminders

## Goal

Hospitals and groomers can manage appointment days, and customers receive reminders.

## Acceptance Checklist

- [ ] Merchant calendar shows confirmed appointments.
- [ ] Merchant can mark appointment completed.
- [ ] Visit notes and prescription upload are supported or explicitly deferred.
- [ ] Notification worker schedules T-24h and T-1h reminders.
- [ ] Reminder delivery vendor is chosen and configured.
- [ ] Customer can rate a completed appointment.
- [ ] Review service prevents duplicate review for the same target.

## Verification

- `python backend/verify_sprint6.py`
- Trigger due reminders and verify delivery/log result.
