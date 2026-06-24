# /// script
# dependencies = [
#   "psycopg2-binary",
# ]
# ///
import psycopg2
import os
import sys

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
    conn_str = "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres"
    
    print("Connecting to PostgreSQL database...")
    try:
        conn = psycopg2.connect(conn_str)
        conn.autocommit = True
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
            DROP SCHEMA IF EXISTS auth CASCADE;
        """)
        
        # 1. Run supabase mock script first to create auth.users
        run_sql_file(cursor, "/Users/trinadh/projects/Mypet/infra/supabase_mock.sql")
        
        # 2. Run DDL files in sequence
        sql_files = [
            "/Users/trinadh/projects/Mypet/imp files/01_identity_providers.sql",
            "/Users/trinadh/projects/Mypet/imp files/02_catalog_orders_appointments.sql",
            "/Users/trinadh/projects/Mypet/imp files/03_dispatch_captains_payments_reviews_notifications.sql",
            "/Users/trinadh/projects/Mypet/infra/db_roles.sql"
        ]
        
        for sf in sql_files:
            run_sql_file(cursor, sf)
            
        print("All migrations completed successfully!")
        cursor.close()
        conn.close()
    except Exception as e:
        print(f"Migration failed: {e}")
        sys.exit(1)
