# /// script
# dependencies = [
#   "psycopg2-binary",
# ]
# ///
import psycopg2
from psycopg2.extensions import parse_dsn
import argparse
import os
import sys
from pathlib import Path


def run_sql_file(cursor, file_path):
    print(f"Executing: {file_path}")
    if not os.path.exists(file_path):
        print(f"Error: File not found {file_path}")
        sys.exit(1)

    with open(file_path, "r", encoding="utf-8") as f:
        sql = f.read()

    try:
        cursor.execute(sql)
        print(f"Successfully executed {os.path.basename(file_path)}")
    except Exception as e:
        print(f"Error executing {file_path}: {e}")
        raise e


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
        parser.error(
            "refusing destructive reset without --reset-local-development-database"
        )

    conn_str = os.getenv(
        "LOCAL_DEV_DB_URL",
        "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres",
    )
    connection_options = parse_dsn(conn_str)
    allowed_hosts = {"localhost", "127.0.0.1", "::1"}
    if connection_options.get("host", "localhost") not in allowed_hosts:
        parser.error("LOCAL_DEV_DB_URL must point to localhost")
    if connection_options.get("dbname") != "pawsnearme":
        parser.error("LOCAL_DEV_DB_URL must use the pawsnearme development database")

    repository_root = Path(__file__).resolve().parent.parent

    print("Connecting to the local PostgreSQL development database...")
    try:
        conn = psycopg2.connect(conn_str)
        cursor = conn.cursor()
        print("Connected!")

        # Reset schemas for a clean run
        print("Resetting database schemas...")
        cursor.execute("""
            DROP SCHEMA IF EXISTS identity CASCADE;
            DROP SCHEMA IF EXISTS providers CASCADE;
            DROP SCHEMA IF EXISTS catalog CASCADE;
            DROP SCHEMA IF EXISTS orders CASCADE;
            DROP SCHEMA IF EXISTS appointments CASCADE;
            DROP SCHEMA IF EXISTS dispatch CASCADE;
            DROP SCHEMA IF EXISTS captains CASCADE;
            DROP SCHEMA IF EXISTS payments CASCADE;
            DROP SCHEMA IF EXISTS reviews CASCADE;
            DROP SCHEMA IF EXISTS notifications CASCADE;
            DROP SCHEMA IF EXISTS chat CASCADE;
            DROP SCHEMA IF EXISTS content CASCADE;
            DROP SCHEMA IF EXISTS auth CASCADE;
            DROP TABLE IF EXISTS public.bootstrap_status;
        """)

        # 1. Run supabase mock script first to create auth.users
        run_sql_file(cursor, repository_root / "infra" / "supabase_mock.sql")

        # 2. Run base DDL and service-role bootstrap in dependency order
        sql_files = [
            repository_root / "imp files" / "01_identity_providers.sql",
            repository_root / "imp files" / "02_catalog_orders_appointments.sql",
            repository_root / "imp files" / "03_dispatch_captains_payments_reviews_notifications.sql",
            repository_root / "infra" / "captain_onboarding_bootstrap.sql",
            repository_root / "infra" / "service_schemas.sql",
            repository_root / "infra" / "db_roles.sql",
            repository_root / "infra" / "bootstrap_complete.sql",
        ]

        for sf in sql_files:
            run_sql_file(cursor, sf)

        conn.commit()
        print("All migrations completed successfully!")
        cursor.close()
        conn.close()
    except Exception as e:
        if "conn" in locals():
            conn.rollback()
            conn.close()
        print(f"Migration failed: {e}")
        sys.exit(1)
