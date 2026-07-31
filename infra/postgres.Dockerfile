FROM postgis/postgis:15-3.3

COPY infra/supabase_mock.sql /docker-entrypoint-initdb.d/00_supabase_mock.sql
COPY backend/provider-service/src/main/resources/db/migration/V1__init_identity_providers.sql /docker-entrypoint-initdb.d/01_identity_providers.sql
COPY backend/catalog-service/src/main/resources/db/migration/V1__init_catalog.sql /docker-entrypoint-initdb.d/02_catalog.sql
COPY backend/order-service/src/main/resources/db/migration/V1__init_orders.sql /docker-entrypoint-initdb.d/03_orders.sql
COPY backend/appointment-service/src/main/resources/db/migration/V1__init_appointments.sql /docker-entrypoint-initdb.d/04_appointments.sql
COPY ["imp files/03_dispatch_captains_payments_reviews_notifications.sql", "/docker-entrypoint-initdb.d/05_dispatch_captains_payments_reviews_notifications.sql"]
COPY infra/captain_onboarding_bootstrap.sql /docker-entrypoint-initdb.d/06_captain_onboarding_bootstrap.sql
COPY infra/service_schemas.sql /docker-entrypoint-initdb.d/07_service_schemas.sql
COPY infra/db_roles.sql /docker-entrypoint-initdb.d/08_db_roles.sql
COPY infra/bootstrap_complete.sql /docker-entrypoint-initdb.d/99_bootstrap_complete.sql
