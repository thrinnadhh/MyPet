-- Mock schema for Supabase Auth in local development PostgreSQL
CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email              TEXT UNIQUE,
    phone              TEXT,
    raw_user_meta_data JSONB,
    created_at         TIMESTAMPTZ DEFAULT now()
);

-- Trigger function to sync auth.users inserts into identity.profiles
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO identity.profiles (user_id, role, full_name, phone_number, created_at, updated_at)
  VALUES (
    new.id,
    COALESCE(new.raw_user_meta_data->>'role', 'CUSTOMER'), -- Dynamic role from metadata, default to CUSTOMER
    COALESCE(new.raw_user_meta_data->>'full_name', 'New User'),
    COALESCE(new.phone, new.id::text), -- fallback if phone is null
    now(),
    now()
  );
  
  INSERT INTO identity.user_roles (user_id, role)
  VALUES (new.id, COALESCE(new.raw_user_meta_data->>'role', 'CUSTOMER'));

  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create the trigger
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
