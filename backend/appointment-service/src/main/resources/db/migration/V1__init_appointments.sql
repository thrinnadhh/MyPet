CREATE SCHEMA IF NOT EXISTS appointments;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS appointments.appointments (
    appointment_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id           UUID NOT NULL,
    provider_id           UUID NOT NULL,
    offering_id           UUID NOT NULL,
    slot_id               UUID NOT NULL,
    pet_id                UUID NOT NULL,
    status                TEXT NOT NULL DEFAULT 'SLOT_HELD',
    price_amount          NUMERIC(12,2) NOT NULL,
    payment_id            UUID,
    pay_at_clinic         BOOLEAN NOT NULL DEFAULT false,
    visit_notes           TEXT,
    prescription_doc_url  TEXT,
    booked_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    cancellation_reason   TEXT
);

CREATE INDEX IF NOT EXISTS idx_appointments_customer
    ON appointments.appointments(customer_id);

CREATE INDEX IF NOT EXISTS idx_appointments_provider
    ON appointments.appointments(provider_id);

CREATE INDEX IF NOT EXISTS idx_appointments_slot
    ON appointments.appointments(slot_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_appointments_active_slot
    ON appointments.appointments(slot_id)
    WHERE status IN ('SLOT_HELD', 'CONFIRMED');

CREATE TABLE IF NOT EXISTS appointments.appointment_status_history (
    history_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id      UUID NOT NULL,
    from_status         TEXT,
    to_status           TEXT NOT NULL,
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by_user_id  UUID,
    note                TEXT
);

CREATE INDEX IF NOT EXISTS idx_appointment_status_history_appointment
    ON appointments.appointment_status_history(appointment_id);
