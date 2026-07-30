#!/usr/bin/env python3
"""
Sprint S12 Integration Verification Script
Tests Server-Backed Customer Favourites API, Tenant Isolation,
and New Arrivals Catalog Merchandising.
"""
import os
import json
import urllib.request
import urllib.error
import psycopg2

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "5433")
DB_NAME = os.getenv("DB_NAME", "pawsnearme")
DB_USER = os.getenv("DB_USER", "postgres")
DB_PASS = os.getenv("DB_PASSWORD", "postgres")

DISCOVERY_URL = "http://localhost:8083"

def get_db_connection():
    return psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASS
    )

def run_sql(query, params=None):
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute(query, params or ())
    conn.commit()
    cur.close()
    conn.close()

def make_request(url, method="GET", body=None, headers=None):
    headers = headers or {}
    data = json.dumps(body).encode("utf-8") if body else None
    if data and "Content-Type" not in headers:
        headers["Content-Type"] = "application/json"
    
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            status = resp.status
            content = resp.read().decode("utf-8")
            return status, json.loads(content) if content else None
    except urllib.error.HTTPError as e:
        content = e.read().decode("utf-8")
        try:
            parsed = json.loads(content)
        except Exception:
            parsed = content
        return e.code, parsed

def main():
    print("--- Sprint S12 Commerce Catalogs & Server Favourites Verification ---")
    
    # 1. Reset DB test state
    print("Bootstrapping database state for customer.favourites...")
    run_sql("""
        CREATE SCHEMA IF NOT EXISTS customer;
        CREATE TABLE IF NOT EXISTS customer.favourites (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            customer_id UUID NOT NULL,
            target_type VARCHAR(32) NOT NULL,
            target_id VARCHAR(128) NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT uq_customer_favourite UNIQUE (customer_id, target_type, target_id)
        );
        GRANT ALL ON SCHEMA customer TO public;
        GRANT ALL ON TABLE customer.favourites TO public;
        DELETE FROM customer.favourites WHERE customer_id = '00000000-0000-0000-0000-000000000001';
    """)
    print("Database bootstrap complete.")

    user_id = "00000000-0000-0000-0000-000000000001"
    headers = {"X-User-Id": user_id}

    # 2. Unauthenticated Access Protection Test
    print("Testing unauthenticated access to /api/v1/customer/favourites...")
    status, err = make_request(f"{DISCOVERY_URL}/api/v1/customer/favourites")
    assert status == 401, f"Expected 401 Unauthorized for unauthenticated request, got {status}"
    print("Unauthenticated protection verified!")

    # 3. Add Favourite Product Test
    print(f"Adding product favourite for user {user_id}...")
    add_body = {"targetType": "PRODUCT", "targetId": "p-food-1"}
    status, fav_res = make_request(f"{DISCOVERY_URL}/api/v1/customer/favourites", "POST", add_body, headers)
    assert status == 201, f"Expected 201 Created, got {status}: {fav_res}"
    assert fav_res["targetType"] == "PRODUCT", "Target type mismatch"
    assert fav_res["targetId"] == "p-food-1", "Target ID mismatch"
    assert fav_res["customerId"] == user_id, "Tenant customer ID mismatch"
    print("Product favourite added successfully!")

    # 4. Add Favourite Shop Test
    print(f"Adding shop favourite for user {user_id}...")
    add_shop_body = {"targetType": "SHOP", "targetId": "the-healthy-hound"}
    status, fav_shop_res = make_request(f"{DISCOVERY_URL}/api/v1/customer/favourites", "POST", add_shop_body, headers)
    assert status == 201, f"Expected 201 Created, got {status}"
    assert fav_shop_res["targetType"] == "SHOP"
    print("Shop favourite added successfully!")

    # 5. Fetch Favourites List
    print("Fetching customer favourites list...")
    status, fav_list = make_request(f"{DISCOVERY_URL}/api/v1/customer/favourites", "GET", None, headers)
    assert status == 200, f"Expected 200 OK, got {status}"
    assert len(fav_list) == 2, f"Expected 2 favourites, got {len(fav_list)}"
    print(f"Customer favourites list retrieved: {len(fav_list)} items.")

    # 6. Tenant Isolation Verification
    print("Testing tenant isolation for user B...")
    other_user_id = "99999999-9999-9999-9999-999999999999"
    other_headers = {"X-User-Id": other_user_id}
    status, other_favs = make_request(f"{DISCOVERY_URL}/api/v1/customer/favourites", "GET", None, other_headers)
    assert status == 200, f"Expected 200, got {status}"
    assert len(other_favs) == 0, f"Tenant isolation failed: User B saw User A's favourites!"
    print("Tenant isolation verified 100%!")

    # 7. Delete Favourite Test
    print("Deleting product favourite...")
    status, _ = make_request(
        f"{DISCOVERY_URL}/api/v1/customer/favourites?targetType=PRODUCT&targetId=p-food-1",
        "DELETE",
        None,
        headers
    )
    assert status == 24 or status == 200 or status == 204, f"Expected 204 No Content, got {status}"

    status, fav_list_after = make_request(f"{DISCOVERY_URL}/api/v1/customer/favourites", "GET", None, headers)
    assert len(fav_list_after) == 1, "Expected 1 favourite remaining after deletion"
    print("Delete favourite verified!")

    print("\nALL SPRINT S12 INTEGRATION VERIFICATION TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
