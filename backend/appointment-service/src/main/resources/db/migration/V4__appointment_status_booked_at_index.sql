-- Sprint 14: Add composite index on (status, booked_at) to support the appointment
-- hold-expiry poller query:
--   SELECT * FROM appointments.appointments WHERE status IN ('SLOT_HELD') ORDER BY booked_at
-- Without this index, the scheduler performs a full table scan every 5 seconds.

CREATE INDEX IF NOT EXISTS idx_appointments_status_booked_at
    ON appointments.appointments(status, booked_at);
