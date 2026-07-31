CREATE TABLE IF NOT EXISTS identity.medical_reports (
    report_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pet_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(64) NOT NULL,
    lab_or_clinic_name VARCHAR(255),
    doctor_name VARCHAR(255),
    object_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_medical_reports_pet ON identity.medical_reports(pet_id);
CREATE INDEX IF NOT EXISTS idx_medical_reports_owner ON identity.medical_reports(owner_id);
