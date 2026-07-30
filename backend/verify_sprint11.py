#!/usr/bin/env python3
"""
Sprint S11 Integration Verification Script
Tests Admin-controlled Service Cities, Serviceability Check, Checkout Boundary Enforcement,
and Universal Search.
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
ORDER_URL = "http://localhost:8084"

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
    print("--- Sprint S11 Service Cities & Universal Search Integration Verification ---")
    
    # 1. Reset DB test state
    print("Bootstrapping database state...")
    run_sql("""
        CREATE TABLE IF NOT EXISTS providers.service_regions (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            city_identity VARCHAR(64) NOT NULL UNIQUE,
            display_name VARCHAR(128) NOT NULL,
            state VARCHAR(128) NOT NULL,
            country VARCHAR(128) NOT NULL DEFAULT 'India',
            center_latitude DOUBLE PRECISION NOT NULL,
            center_longitude DOUBLE PRECISION NOT NULL,
            radius_km DOUBLE PRECISION NOT NULL DEFAULT 25.0,
            pincodes TEXT,
            status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
            sort_order INT NOT NULL DEFAULT 1,
            allow_products BOOLEAN NOT NULL DEFAULT true,
            allow_grooming BOOLEAN NOT NULL DEFAULT true,
            allow_vet BOOLEAN NOT NULL DEFAULT true,
            allow_own_delivery BOOLEAN NOT NULL DEFAULT true,
            allow_3p_delivery BOOLEAN NOT NULL DEFAULT true,
            allow_cod BOOLEAN NOT NULL DEFAULT true,
            allow_online_payment BOOLEAN NOT NULL DEFAULT true,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        GRANT ALL ON TABLE providers.service_regions TO public;
        GRANT ALL ON ALL TABLES IN SCHEMA providers TO public;
        DELETE FROM providers.service_regions WHERE city_identity != 'tirupati';

        INSERT INTO providers.service_regions (
            id, city_identity, display_name, state, country, center_latitude, center_longitude, radius_km, pincodes, status, sort_order
        ) VALUES (
            '81111111-1111-1111-1111-111111111111', 'tirupati', 'Tirupati', 'Andhra Pradesh', 'India', 13.6288, 79.4192, 25.0, '517501,517502,517507', 'ENABLED', 1
        ) ON CONFLICT (city_identity) DO UPDATE SET status = 'ENABLED';
    """)
    print("Database bootstrap complete.")


    # 2. Public Active Cities API
    print("Checking GET /api/v1/service-regions/active...")
    status, active_cities = make_request(f"{DISCOVERY_URL}/api/v1/service-regions/active")
    assert status == 200, f"Expected status 200, got {status}"
    assert isinstance(active_cities, list), "Expected list of active cities"
    tirupati = next((c for c in active_cities if c["cityIdentity"] == "tirupati"), None)
    assert tirupati is not None, "Tirupati active region not found"
    assert tirupati["status"] == "ENABLED", "Tirupati region should be ENABLED"
    print("Active cities API verified! Tirupati is enabled.")

    # 3. Serviceability Check API
    print("Checking GET /api/v1/service-regions/check for Tirupati...")
    status, check_res = make_request(f"{DISCOVERY_URL}/api/v1/service-regions/check?city=tirupati")
    assert status == 200, f"Expected 200, got {status}"
    assert check_res["serviceable"] is True, "Tirupati should be serviceable"
    print("Serviceability check for Tirupati passed!")

    # 4. Admin API: Add 2nd Test City (Vijayawada) without code changes
    print("Admin creating 2nd service city (Vijayawada)...")
    admin_headers = {"X-User-Role": "ADMIN"}
    create_body = {
        "cityIdentity": "vijayawada",
        "displayName": "Vijayawada",
        "state": "Andhra Pradesh",
        "country": "India",
        "centerLatitude": 16.5062,
        "centerLongitude": 80.6480,
        "radiusKm": 30.0,
        "pincodes": "520001,520002",
        "status": "ENABLED",
        "sortOrder": 2
    }
    status, created_city = make_request(f"{DISCOVERY_URL}/api/v1/admin/service-regions", "POST", create_body, admin_headers)
    assert status == 201, f"Expected 201 Created, got {status}: {created_city}"
    vj_id = created_city["id"]
    print(f"Vijayawada created successfully with ID: {vj_id}")

    # 5. Verify cache invalidation & discovery list
    status, active_cities_2 = make_request(f"{DISCOVERY_URL}/api/v1/service-regions/active")
    assert any(c["cityIdentity"] == "vijayawada" for c in active_cities_2), "Vijayawada should appear in active cities immediately after admin creation"
    print("Super Admin 2nd city dynamic launch verified without code changes!")

    # 6. Admin Disable City & Checkout Boundary Rejection
    print("Admin pausing Vijayawada city...")
    update_body = {"status": "DISABLED"}
    status, updated_city = make_request(f"{DISCOVERY_URL}/api/v1/admin/service-regions/{vj_id}", "PUT", update_body, admin_headers)
    assert status == 200, f"Expected 200, got {status}"
    assert updated_city["status"] == "DISABLED", "Vijayawada status should be updated to DISABLED"

    print("Checking serviceability check for disabled city...")
    status, check_disabled = make_request(f"{DISCOVERY_URL}/api/v1/service-regions/check?city=vijayawada")
    assert check_disabled["serviceable"] is False, "Disabled city must be unserviceable"
    print("Disabled city correctly reported as unserviceable.")

    print("Testing Order Checkout Boundary Enforcement for disabled region...")
    order_body = {
        "customerId": "11111111-2222-3333-4444-555555555555",
        "providerId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
        "deliveryAddressId": "99999999-8888-7777-6666-555555555555",
        "city": "vijayawada",
        "items": [{"offeringId": "f5e4d3c2-b1a0-0987-6543-210fedcba987", "quantity": 1}]
    }
    status, order_err = make_request(f"{ORDER_URL}/api/v1/orders", "POST", order_body)
    assert status in (400, 500), f"Expected checkout rejection HTTP 400/500, got {status}: {order_err}"
    assert "UNSERVICEABLE_REGION" in str(order_err), f"Expected UNSERVICEABLE_REGION error, got {order_err}"
    print("Checkout boundary enforcement successfully rejected unserviceable region!")

    # 7. Universal Search Verification
    print("Testing Universal Search endpoint GET /api/v1/discovery/search?q=PetCare...")
    status, search_res = make_request(f"{DISCOVERY_URL}/api/v1/discovery/search?q=PetCare")
    assert status == 200, f"Expected status 200, got {status}"
    assert "query" in search_res, "Response must include query field"
    assert "results" in search_res, "Response must include results list"
    print(f"Universal Search returned {search_res['totalResults']} results!")

    print("\nALL SPRINT S11 INTEGRATION VERIFICATION TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
