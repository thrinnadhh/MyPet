CREATE TABLE IF NOT EXISTS appointments.appointment_invoices (
    invoice_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id   UUID UNIQUE NOT NULL REFERENCES appointments.appointments(appointment_id) ON DELETE CASCADE,
    invoice_number   TEXT UNIQUE NOT NULL,
    subtotal_amount  NUMERIC(12, 2) NOT NULL CHECK (subtotal_amount >= 0),
    tax_amount       NUMERIC(12, 2) NOT NULL CHECK (tax_amount >= 0),
    total_amount     NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    generated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_appointment_invoices_appointment
    ON appointments.appointment_invoices(appointment_id);
