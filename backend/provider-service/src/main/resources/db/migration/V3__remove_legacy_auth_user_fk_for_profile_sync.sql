-- JWT-authenticated profile sync should not depend on a local auth.users mirror.
-- Supabase Auth remains the identity source of truth; identity.profiles stores
-- app-specific role/profile data keyed by the authenticated JWT subject.

ALTER TABLE IF EXISTS identity.profiles
    DROP CONSTRAINT IF EXISTS profiles_user_id_fkey;

ALTER TABLE IF EXISTS identity.user_roles
    DROP CONSTRAINT IF EXISTS user_roles_user_id_fkey;
