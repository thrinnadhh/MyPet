CREATE TABLE IF NOT EXISTS identity.delivery_contacts (
    address_id    UUID PRIMARY KEY REFERENCES identity.addresses(address_id) ON DELETE CASCADE,
    user_id       UUID NOT NULL,
    phone_number  VARCHAR(13) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_delivery_contact_india_mobile
        CHECK (phone_number ~ '^\\+91[6-9][0-9]{9}$')
);

CREATE INDEX IF NOT EXISTS idx_delivery_contacts_user
    ON identity.delivery_contacts(user_id);
