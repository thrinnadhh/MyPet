CREATE TABLE IF NOT EXISTS public.bootstrap_status (
    bootstrap_name TEXT PRIMARY KEY,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO public.bootstrap_status (bootstrap_name, completed_at)
VALUES ('base-schema', now())
ON CONFLICT (bootstrap_name)
DO UPDATE SET completed_at = EXCLUDED.completed_at;
