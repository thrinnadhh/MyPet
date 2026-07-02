-- Address and pet records are owned by the authenticated JWT subject. They
-- should not require a local auth.users mirror when the app is using remote
-- Supabase Auth tokens.

ALTER TABLE IF EXISTS identity.addresses
    DROP CONSTRAINT IF EXISTS addresses_user_id_fkey;

ALTER TABLE IF EXISTS identity.pets
    DROP CONSTRAINT IF EXISTS pets_owner_id_fkey;
