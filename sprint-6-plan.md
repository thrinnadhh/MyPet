# Sprint 6 Merchant Calendar And Reminders Plan

## Goal

Complete Sprint 6 by proving merchants can manage confirmed appointments, customers receive appointment reminders through a real v1 delivery path or explicit launch deferral, and completed appointments can be reviewed once.

## Tasks

- [x] Add live merchant appointment calendar API usage in `apps/merchant-captain-app/src/app/explore.tsx`, replacing `MOCK_BOOKINGS` when demo mode is false. Verify with `EXPO_PUBLIC_ALLOW_DEMO_MODE=false` that the screen loads `/api/v1/appointments/provider/{providerId}` data or shows a real error.
- [x] Persist merchant completion from the calendar by calling `PUT /api/v1/appointments/{id}/status?status=COMPLETED` with visit notes and optional prescription/document URL. Verify the appointment changes from `CONFIRMED` to `COMPLETED` in backend data.
- [x] Decide and encode prescription upload scope: either wire an upload URL flow for completed visits or explicitly mark prescription upload deferred in the Sprint 6 acceptance doc. Verify the UI and docs do not imply a fake upload path.
- [x] Harden notification delivery state by extending `notifications.scheduled_reminders` beyond `fired` into statuses such as `SCHEDULED`, `ATTEMPTED`, `DELIVERED_LOGGED`, `FAILED`, and retryable failure reason. Verify worker tests cover success, retryable failure, and non-retryable failure.
- [x] Replace the current log-only adapter with a named v1 adapter boundary for Expo/FCM push, keeping log delivery only as an explicit local/dev fallback. Verify production config cannot silently mark reminders delivered without configured delivery mode.
- [x] Prove appointment confirmation schedules T-24h and T-1h reminders from `appointments.events`. Verify with notification-service tests and a live reminder query for the confirmed appointment.
- [x] Wire customer appointment review submission in `apps/customer-app/src/app/explore.tsx` to `POST /api/v1/reviews`, using real `customerId`, `providerId`, `targetType`, and `targetId`. Verify duplicate review returns 409 and the UI shows already-reviewed state.
- [x] Update `backend/verify_sprint6.py` so it checks merchant completion, reminder scheduling/status, review duplicate rejection, and gateway routing with no skipped services during a live proof run.
- [x] Update `docs/sprints/sprint-6-calendar-reminders.md` and `docs/sprints/sprint-gap-tracker.md` only after tests and live proof pass.
- [x] Run final Sprint 6 verification: `./gradlew :appointment-service:test :notification-service:test :review-service:test`, customer app typecheck/lint, merchant app typecheck/lint, and `.venv/bin/python backend/verify_sprint6.py`.

## Done When

- [x] Merchant calendar uses live appointment data and can mark visits completed.
- [x] Visit notes are persisted, and prescription/document upload is either real or clearly deferred.
- [x] T-24h and T-1h reminders are scheduled and have auditable delivery status.
- [x] Customer review submission is live and duplicate-safe.
- [x] Sprint 6 docs show complete only after backend tests, app checks, and live proof pass.

## Notes

- Keep SMS deferred unless launch requirements change.
- Do not allow production paths to silently succeed through demo bookings, fake uploads, or log-only reminder delivery.
- Build on Sprint 5 appointment events and existing review-service endpoints instead of creating a parallel calendar/review model.
- Verification passed on 2026-07-03: backend Gradle tests, both app typecheck/lint checks, and `backend/verify_sprint6.py` with 31 passed, 0 failed, 0 skipped.
- Production launch follow-up: real Expo/FCM push-token registration and vendor delivery evidence are still required before launch.
