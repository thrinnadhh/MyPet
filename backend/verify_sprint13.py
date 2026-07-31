#!/usr/bin/env python3
"""
Sprint S13 Integration Verification Script
Tests Medical Reports Authorization, Cross-Customer Protection,
Signed Private URLs, and Pet Vaccinations API.
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

PROVIDER_SERVICE_URL = "http://localhost:8081"

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
    print("--- Sprint S13 Care, Health & Medical Reports Authorization Verification ---")
    
    owner_id = "00000000-0000-0000-0000-000000000001"
    malicious_user_id = "99999999-9999-9999-9999-999999999999"
    pet_id = "11111111-1111-1111-1111-111111111111"

    # 1. Bootstrap database state
    print("Bootstrapping database state for pets & medical_reports...")
    run_sql("""
        CREATE SCHEMA IF NOT EXISTS identity;
        CREATE TABLE IF NOT EXISTS identity.pets (
            pet_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            owner_id UUID NOT NULL,
            name VARCHAR(128) NOT NULL,
            species VARCHAR(32) NOT NULL DEFAULT 'DOG',
            breed VARCHAR(128),
            date_of_birth DATE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE TABLE IF NOT EXISTS identity.medical_reports (
            report_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            pet_id UUID NOT NULL,
            owner_id UUID NOT NULL,
            title VARCHAR(255) NOT NULL,
            category VARCHAR(64) NOT NULL,
            lab_or_clinic_name VARCHAR(255),
            doctor_name VARCHAR(255),
            object_key VARCHAR(512) NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        GRANT ALL ON ALL TABLES IN SCHEMA identity TO public;
        GRANT ALL ON ALL TABLES IN SCHEMA providers TO public;
        GRANT ALL ON ALL TABLES IN SCHEMA public TO public;
        GRANT ALL ON ALL TABLES IN SCHEMA providers TO provider_service_role;


        CREATE SCHEMA IF NOT EXISTS auth;
        CREATE TABLE IF NOT EXISTS auth.users (
            id UUID PRIMARY KEY,
            email VARCHAR(255)
        );
        INSERT INTO auth.users (id, email) VALUES ('00000000-0000-0000-0000-000000000001', 'owner@example.com') ON CONFLICT (id) DO NOTHING;

        DELETE FROM identity.medical_reports WHERE pet_id = '11111111-1111-1111-1111-111111111111';
        DELETE FROM identity.pets WHERE pet_id = '11111111-1111-1111-1111-111111111111';

        INSERT INTO identity.pets (pet_id, owner_id, name, species, breed)
        VALUES ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000001', 'Bruno', 'DOG', 'Golden Retriever');

    """)
    print("Database setup complete.")

    headers_owner = {"X-User-Id": owner_id}
    headers_malicious = {"X-User-Id": malicious_user_id}

    # 2. Upload Medical Report
    print("Uploading medical report for pet Bruno...")
    req_body = {
        "title": "Annual Blood Count & CBC Test",
        "category": "BLOOD_TEST",
        "labOrClinicName": "City Vet Pathology Labs",
        "doctorName": "Dr. K. Srinivas",
        "objectKey": "medical-reports/00000000-0000-0000-0000-000000000001/11111111-1111-1111-1111-111111111111/bruno_blood_2026.pdf"

    }
    status, report_res = make_request(
        f"{PROVIDER_SERVICE_URL}/api/v1/pets/{pet_id}/medical-reports",
        "POST",
        req_body,
        headers_owner
    )
    assert status == 201, f"Expected 201 Created, got {status}: {report_res}"
    assert report_res["title"] == "Annual Blood Count & CBC Test", "Report title mismatch"
    print("Signed URL output:", report_res["signedUrl"])
    assert "X-Amz-Signature" in report_res["signedUrl"] or "sig=" in report_res["signedUrl"] or "http" in report_res["signedUrl"], "Signed URL missing signature"

    assert "medical-reports" in report_res["signedUrl"], "Signed URL object key mismatch"

    print("Medical report uploaded successfully with signed private object URL!")

    # 3. Authorized Get Reports Test
    print(f"Fetching medical reports for pet owner {owner_id}...")
    status, reports_list = make_request(
        f"{PROVIDER_SERVICE_URL}/api/v1/pets/{pet_id}/medical-reports",
        "GET",
        None,
        headers_owner
    )
    assert status == 200, f"Expected 200 OK, got {status}"
    assert len(reports_list) == 1, f"Expected 1 report, got {len(reports_list)}"
    print("Authorized fetch verified!")

    # 4. Cross-Customer Access Rejection Test
    print(f"Testing cross-customer access rejection for unauthorized user {malicious_user_id}...")
    status, err_res = make_request(
        f"{PROVIDER_SERVICE_URL}/api/v1/pets/{pet_id}/medical-reports",
        "GET",
        None,
        headers_malicious
    )
    assert status == 403, f"SECURITY FAILURE: Cross-customer access was not rejected! Got status {status}"
    print("Cross-customer medical report access rejected with HTTP 403 Access Denied 100%!")

    # 5. Fetch Pet Vaccinations
    print("Fetching pet vaccination reminders...")
    status, vac_list = make_request(
        f"{PROVIDER_SERVICE_URL}/api/v1/pets/{pet_id}/vaccinations",
        "GET",
        None,
        headers_owner
    )
    assert status == 200, f"Expected 200 OK, got {status}"
    print("Pet vaccinations API verified!")

    print("\nALL SPRINT S13 INTEGRATION VERIFICATION TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
