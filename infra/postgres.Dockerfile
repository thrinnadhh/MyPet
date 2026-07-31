FROM postgis/postgis:15-3.3

COPY infra/supabase_mock.sql /docker-entrypoint-initdb.d/00_supabase_mock.sql
COPY ["imp files/01_identity_providers.sql", "/docker-entrypoint-initdb.d/01_identity_providers.sql"]
COPY ["imp files/02_catalog_orders_appointments.sql", "/docker-entrypoint-initdb.d/02_catalog_orders_appointments.sql"]
COPY ["imp files/03_dispatch_captains_payments_reviews_notifications.sql", "/docker-entrypoint-initdb.d/03_dispatch_captains_payments_reviews_notifications.sql"]
COPY infra/captain_onboarding_bootstrap.sql /docker-entrypoint-initdb.d/04_captain_onboarding_bootstrap.sql
COPY infra/service_schemas.sql /docker-entrypoint-initdb.d/05_service_schemas.sql
COPY infra/db_roles.sql /docker-entrypoint-initdb.d/06_db_roles.sql
COPY infra/bootstrap_complete.sql /docker-entrypoint-initdb.d/99_bootstrap_complete.sql
