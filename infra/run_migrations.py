# /// script
# dependencies = [
#   "psycopg2-binary",
# ]
# ///
import argparse
import os
import sys
from pathlib import Path

import psycopg2
from psycopg2.extensions import parse_dsn


def run_sql_file(cursor, file_path):
    print(f"Executing: {file_path}")
    if not os.path.exists(file_path):
        print(f"Error: File not found {file_path}")
        sys.exit(1)
    with open(file_path, "r", encoding="utf-8") as file:
        cursor.execute(file.read())
    print(f"Successfully executed {os.path.basename(file_path)}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Reset and bootstrap the local PawsNearMe development database."
    )
    parser.add_argument(
        "--reset-local-development-database",
        action="store_true",
        help="Required acknowledgement that all local development schemas will be dropped.",
    )
    args = parser.parse_args()
    if not args.reset_local_development_database:
        parser.error("refusing destructive reset without --reset-local-development-database")

    conn_str = os.getenv(
        "LOCAL_DEV_DB_URL",
        "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres",
    )
    options = parse_dsn(conn_str)
    if options.get("host", "localhost") not in {"localhost", "127.0.0.1", "::1"}:
        parser.error("LOCAL_DEV_DB_URL must point to localhost")
    if options.get("dbname") != "pawsnearme":
        parser.error("LOCAL_DEV_DB_URL must use the pawsnearme development database")

    root = Path(__file__).resolve().parent.parent
    try:
        connection = psycopg2.connect(conn_str)
        cursor = connection.cursor()
        cursor.execute("""
            DROP SCHEMA IF EXISTS identity CASCADE;
            DROP SCHEMA IF EXISTS providers CASCADE;
            DROP SCHEMA IF EXISTS catalog CASCADE;
            DROP SCHEMA IF EXISTS billing CASCADE;
            DROP SCHEMA IF EXISTS orders CASCADE;
            DROP SCHEMA IF EXISTS appointments CASCADE;
            DROP SCHEMA IF EXISTS dispatch CASCADE;
            DROP SCHEMA IF EXISTS captains CASCADE;
            DROP SCHEMA IF EXISTS payments CASCADE;
            DROP SCHEMA IF EXISTS reviews CASCADE;
            DROP SCHEMA IF EXISTS notifications CASCADE;
            DROP SCHEMA IF EXISTS chat CASCADE;
            DROP SCHEMA IF EXISTS content CASCADE;
            DROP SCHEMA IF EXISTS customer CASCADE;
            DROP SCHEMA IF EXISTS auth CASCADE;
            DROP TABLE IF EXISTS public.bootstrap_status;
        """)

        files = [
            root / "infra" / "supabase_mock.sql",
            root / "infra" / "db_role_definitions.sql",
            root / "backend/provider-service/src/main/resources/db/migration/V1__init_identity_providers.sql",
            root / "backend/provider-service/src/main/resources/db/migration/V5__delivery_contacts.sql",
            root / "backend/provider-service/src/main/resources/db/migration/V6__grant_order_delivery_contact_lookup.sql",
            root / "backend/catalog-service/src/main/resources/db/migration/V1__init_catalog.sql",
            root / "backend/order-service/src/main/resources/db/migration/V1__init_orders.sql",
            root / "backend/order-service/src/main/resources/db/migration/V3__checkout_pricing_columns.sql",
            root / "backend/order-service/src/main/resources/db/migration/V4__delivery_contact_snapshot.sql",
            root / "backend/appointment-service/src/main/resources/db/migration/V1__init_appointments.sql",
            root / "backend/dispatch-service/src/main/resources/db/migration/V1__init_dispatch.sql",
            root / "infra" / "captain_onboarding_bootstrap.sql",
            root / "backend/payment-service/src/main/resources/db/migration/V1__init_payments.sql",
            root / "backend/review-service/src/main/resources/db/migration/V1__init_reviews.sql",
            root / "backend/notification-service/src/main/resources/db/migration/V1__init_notifications.sql",
            root / "backend/chat-service/src/main/resources/db/migration/V1__init_chat.sql",
            root / "backend/content-service/src/main/resources/db/migration/V1__init_content.sql",
            root / "backend/discovery-service/src/main/resources/db/migration/V1__create_service_regions.sql",
            root / "infra" / "db_roles.sql",
            root / "infra" / "bootstrap_complete.sql",
        ]
        for sql_file in files:
            run_sql_file(cursor, sql_file)

        connection.commit()
        cursor.close()
        connection.close()
        print("Database bootstrap completed successfully.")
    except Exception as error:
        if "connection" in locals():
            connection.rollback()
            connection.close()
        print(f"Bootstrap failed: {error}")
        sys.exit(1)
