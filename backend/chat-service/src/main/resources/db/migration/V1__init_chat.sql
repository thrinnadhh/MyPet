CREATE SCHEMA IF NOT EXISTS chat;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'chat_context_type' AND typnamespace = 'chat'::regnamespace) THEN
        CREATE TYPE chat.chat_context_type AS ENUM ('ORDER', 'APPOINTMENT');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'chat_message_type' AND typnamespace = 'chat'::regnamespace) THEN
        CREATE TYPE chat.chat_message_type AS ENUM ('TEXT', 'IMAGE');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'chat_sender_role' AND typnamespace = 'chat'::regnamespace) THEN
        CREATE TYPE chat.chat_sender_role AS ENUM ('CUSTOMER', 'MERCHANT');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS chat.conversations (
    conversation_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id             UUID NOT NULL,
    provider_id             UUID NOT NULL,
    context_type            chat.chat_context_type NOT NULL,
    context_id              UUID NOT NULL,
    provider_type           TEXT NOT NULL DEFAULT 'PET_STORE',
    customer_phone_visible  BOOLEAN NOT NULL DEFAULT false,
    doctor_phone_visible    BOOLEAN NOT NULL DEFAULT false,
    assigned_doctor_user_id UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_conversations_context
    ON chat.conversations(context_type, context_id);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_customer
    ON chat.conversations(customer_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_provider
    ON chat.conversations(provider_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS chat.messages (
    message_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID NOT NULL REFERENCES chat.conversations(conversation_id) ON DELETE CASCADE,
    sender_id         UUID NOT NULL,
    sender_role       chat.chat_sender_role NOT NULL,
    message_type      chat.chat_message_type NOT NULL,
    body              TEXT,
    image_url         TEXT,
    image_mime_type   TEXT,
    sent_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation
    ON chat.messages(conversation_id, sent_at);
