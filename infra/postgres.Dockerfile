FROM postgis/postgis:15-3.3

COPY infra/supabase_mock.sql /docker-entrypoint-initdb.d/00_supabase_mock.sql
COPY infra/db_role_definitions.sql /docker-entrypoint-initdb.d/01_db_role_definitions.sql
COPY backend/provider-service/src/main/resources/db/migration/V1__init_identity_providers.sql /docker-entrypoint-initdb.d/02_identity_providers.sql
COPY backend/catalog-service/src/main/resources/db/migration/V1__init_catalog.sql /docker-entrypoint-initdb.d/03_catalog.sql
COPY backend/order-service/src/main/resources/db/migration/V1__init_orders.sql /docker-entrypoint-initdb.d/04_orders.sql
COPY backend/appointment-service/src/main/resources/db/migration/V1__init_appointments.sql /docker-entrypoint-initdb.d/05_appointments.sql
COPY backend/dispatch-service/src/main/resources/db/migration/V1__init_dispatch.sql /docker-entrypoint-initdb.d/06_dispatch.sql
COPY infra/captain_onboarding_bootstrap.sql /docker-entrypoint-initdb.d/07_captains.sql
COPY backend/payment-service/src/main/resources/db/migration/V1__init_payments.sql /docker-entrypoint-initdb.d/08_payments.sql
COPY backend/review-service/src/main/resources/db/migration/V1__init_reviews.sql /docker-entrypoint-initdb.d/09_reviews.sql
COPY backend/notification-service/src/main/resources/db/migration/V1__init_notifications.sql /docker-entrypoint-initdb.d/10_notifications.sql
COPY backend/chat-service/src/main/resources/db/migration/V1__init_chat.sql /docker-entrypoint-initdb.d/11_chat.sql
COPY backend/content-service/src/main/resources/db/migration/V1__init_content.sql /docker-entrypoint-initdb.d/12_content.sql
COPY backend/discovery-service/src/main/resources/db/migration/V1__create_service_regions.sql /docker-entrypoint-initdb.d/13_service_regions.sql
COPY infra/db_roles.sql /docker-entrypoint-initdb.d/14_db_roles.sql
COPY infra/bootstrap_complete.sql /docker-entrypoint-initdb.d/99_bootstrap_complete.sql
