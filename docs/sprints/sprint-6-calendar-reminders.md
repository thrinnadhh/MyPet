# Sprint 6: Merchant Calendar And Reminders

## Goal

Hospitals and groomers can manage appointment days, and customers receive reminders.

## Acceptance Checklist

- [x] Merchant calendar shows confirmed appointments.
- [x] Merchant can mark appointment completed.
- [x] Visit notes are supported; prescription file upload is explicitly deferred for Sprint 6 launch proof.
- [x] Notification worker schedules T-24h and T-1h reminders.
- [x] Reminder delivery adapter is chosen and auditable for v1; real Expo/FCM push-token registration remains a launch follow-up.
- [x] Customer can rate a completed appointment.
- [x] Review service prevents duplicate review for the same target.

## Verification

- `python backend/verify_sprint6.py`
- Trigger due reminders and verify delivery/log result.
- Passed on 2026-07-03 IST: `./gradlew :appointment-service:test :notification-service:test :review-service:test`.
- Passed on 2026-07-03 IST: customer app `npm run typecheck` and `npm run lint`.
- Passed on 2026-07-03 IST: merchant app `npm run typecheck` and `npm run lint`.
- Passed on 2026-07-03 IST: `.venv/bin/python backend/verify_sprint6.py` with 31 passed, 0 failed, 0 skipped.

## Implementation Notes

- Merchant Bookings now uses live provider appointments when demo mode is off and persists completion notes through the appointment status endpoint.
- Customer History now loads live appointments when demo mode is off and submits appointment reviews through review-service.
- Notification reminders now track delivery status, attempts, provider, failure reason, and retryability.
- Expo/FCM remains the selected v1 push path, but production push-token registration is still required before reminders can be marked delivered through a real vendor.
