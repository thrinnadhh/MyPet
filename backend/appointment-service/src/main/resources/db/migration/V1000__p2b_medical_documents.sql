CREATE TABLE IF NOT EXISTS appointments.medical_documents (
    document_id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments.appointments(appointment_id),
    owner_user_id UUID NOT NULL,
    uploader_user_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_medical_document_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT ck_medical_document_status CHECK (status IN ('AVAILABLE', 'QUARANTINED', 'DELETED'))
);

CREATE INDEX IF NOT EXISTS idx_medical_documents_owner
    ON appointments.medical_documents (owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_medical_documents_appointment
    ON appointments.medical_documents (appointment_id, created_at DESC);

CREATE TABLE IF NOT EXISTS appointments.medical_document_access_logs (
    access_id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES appointments.medical_documents(document_id),
    actor_user_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    trace_id VARCHAR(160) NOT NULL,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_medical_document_action CHECK (action IN ('UPLOAD', 'SIGNED_URL_ISSUED', 'VIEW', 'DOWNLOAD'))
);

CREATE INDEX IF NOT EXISTS idx_medical_document_access
    ON appointments.medical_document_access_logs (document_id, accessed_at DESC);
