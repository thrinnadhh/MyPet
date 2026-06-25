import urllib.request
import json
import sys
import time
import base64
import psycopg2

def generate_mock_jwt(user_id, role):
    header = {"alg": "none", "typ": "JWT"}
    payload = {
        "sub": user_id,
        "role": role,
        "exp": int(time.time()) + 3600
    }
    header_b64 = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip("=")
    payload_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    return f"{header_b64}.{payload_b64}."

def post_json(url, data, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode("utf-8") if data is not None else b"{}",
        headers=headers,
        method="POST"
    )
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {"error": e.reason}
    except Exception as e:
        return 500, {"error": str(e)}

def put_json(url, data, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode("utf-8") if data is not None else None,
        headers=headers,
        method="PUT"
    )
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {"error": e.reason}
    except Exception as e:
        return 500, {"error": str(e)}

def get_json(url, token=None):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {"error": e.reason}
    except Exception as e:
        return 500, {"error": str(e)}

def setup_db_mock_data():
    conn_str = "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres"
    print("Connecting to DB to set up clean test state for appointments...")
    conn = psycopg2.connect(conn_str)
    conn.autocommit = True
    cur = conn.cursor()

    customer_id = "11111111-1111-1111-1111-111111111111"
    customer_id_2 = "22222222-1111-1111-1111-111111111111"
    merchant_id = "d3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e"
    provider_id = "22222222-2222-2222-2222-222222222222"
    offering_id = "33333333-3333-3333-3333-333333333333"
    slot_id_1 = "44444444-4444-4444-4444-444444444444"
    slot_id_2 = "44444444-4444-4444-4444-555555555555"
    pet_id = "77777777-7777-7777-7777-777777777777"

    print("Cleaning up old test data...")
    cur.execute("DELETE FROM appointments.appointment_status_history WHERE appointment_id IN (SELECT appointment_id FROM appointments.appointments WHERE customer_id IN (%s, %s));", (customer_id, customer_id_2))
    cur.execute("DELETE FROM appointments.appointments WHERE customer_id IN (%s, %s);", (customer_id, customer_id_2))
    cur.execute("DELETE FROM catalog.slots WHERE slot_id IN (%s, %s);", (slot_id_1, slot_id_2))
    cur.execute("DELETE FROM catalog.offerings WHERE offering_id = %s;", (offering_id,))
    cur.execute("DELETE FROM providers.providers WHERE provider_id = %s;", (provider_id,))
    cur.execute("DELETE FROM identity.user_roles WHERE user_id IN (%s, %s, %s);", (customer_id, customer_id_2, merchant_id))
    cur.execute("DELETE FROM identity.profiles WHERE user_id IN (%s, %s, %s);", (customer_id, customer_id_2, merchant_id))
    cur.execute("DELETE FROM auth.users WHERE id IN (%s, %s, %s);", (customer_id, customer_id_2, merchant_id))

    print("Inserting fresh test users...")
    cur.execute("INSERT INTO auth.users (id, email) VALUES (%s, %s);", (customer_id, "customer1@pawsnearme.com"))
    cur.execute("INSERT INTO auth.users (id, email) VALUES (%s, %s);", (customer_id_2, "customer2@pawsnearme.com"))
    cur.execute("INSERT INTO auth.users (id, email) VALUES (%s, %s);", (merchant_id, "merchant@pawsnearme.com"))

    cur.execute("DELETE FROM identity.user_roles WHERE user_id IN (%s, %s, %s);", (customer_id, customer_id_2, merchant_id))
    cur.execute("DELETE FROM identity.profiles WHERE user_id IN (%s, %s, %s);", (customer_id, customer_id_2, merchant_id))

    cur.execute("INSERT INTO identity.profiles (user_id, role, full_name, phone_number) VALUES (%s, 'CUSTOMER', 'Customer One', '+911111111111');", (customer_id,))
    cur.execute("INSERT INTO identity.profiles (user_id, role, full_name, phone_number) VALUES (%s, 'CUSTOMER', 'Customer Two', '+912222222222');", (customer_id_2,))
    cur.execute("INSERT INTO identity.profiles (user_id, role, full_name, phone_number) VALUES (%s, 'MERCHANT', 'Merchant One', '+918888888888');", (merchant_id,))

    cur.execute("INSERT INTO identity.user_roles (user_id, role) VALUES (%s, 'CUSTOMER');", (customer_id,))
    cur.execute("INSERT INTO identity.user_roles (user_id, role) VALUES (%s, 'CUSTOMER');", (customer_id_2,))
    cur.execute("INSERT INTO identity.user_roles (user_id, role) VALUES (%s, 'MERCHANT');", (merchant_id,))

    print("Inserting Vet Clinic Provider, Offering, and Slots...")
    cur.execute("""
        INSERT INTO providers.providers (provider_id, owner_user_id, provider_type, fulfillment_type, name, address_line, city, pincode, geo_location, status) 
        VALUES (%s, %s, 'VET_HOSPITAL', 'APPOINTMENT', 'Tails Vet Clinic', '56 Clinic Street', 'Bangalore', '560008', ST_GeographyFromText('SRID=4326;POINT(77.6404 12.9719)'), 'ACTIVE');
    """, (provider_id, merchant_id))

    cur.execute("""
        INSERT INTO catalog.offerings (offering_id, provider_id, name, price, duration_minutes) 
        VALUES (%s, %s, 'General Checkup', 500.00, 30);
    """, (offering_id, provider_id))

    cur.execute("""
        INSERT INTO catalog.slots (slot_id, offering_id, slot_start, slot_end, status) 
        VALUES (%s, %s, now() + interval '1 hour', now() + interval '1.5 hours', 'AVAILABLE');
    """, (slot_id_1, offering_id))

    cur.execute("""
        INSERT INTO catalog.slots (slot_id, offering_id, slot_start, slot_end, status) 
        VALUES (%s, %s, now() + interval '2 hours', now() + interval '2.5 hours', 'AVAILABLE');
    """, (slot_id_2, offering_id))

    cur.close()
    conn.close()
    print("Database setup complete.")

def run_integration_test():
    gateway_url = "http://localhost:8080"
    customer_id = "11111111-1111-1111-1111-111111111111"
    customer_id_2 = "22222222-1111-1111-1111-111111111111"
    provider_id = "22222222-2222-2222-2222-222222222222"
    offering_id = "33333333-3333-3333-3333-333333333333"
    slot_id_1 = "44444444-4444-4444-4444-444444444444"
    slot_id_2 = "44444444-4444-4444-4444-555555555555"
    pet_id = "77777777-7777-7777-7777-777777777777"

    token_1 = generate_mock_jwt(customer_id, "CUSTOMER")
    token_2 = generate_mock_jwt(customer_id_2, "CUSTOMER")

    print("\n--- Test Step 1: Hold Slot via Appointment Service ---")
    hold_data = {
        "customerId": customer_id,
        "providerId": provider_id,
        "offeringId": offering_id,
        "slotId": slot_id_1,
        "petId": pet_id,
        "priceAmount": 500.00,
        "payAtClinic": True
    }
    status, hold_res = post_json(
        f"{gateway_url}/api/v1/appointments/hold",
        hold_data,
        token=token_1
    )
    print(f"Hold Response: {status} -> {hold_res}")
    if status != 201 or hold_res.get("status") != "SLOT_HELD":
        print("Failed to hold slot!")
        sys.exit(1)
    appointment_id = hold_res["appointmentId"]

    print("\n--- Test Step 1.1: Verify Slot status is HELD in Catalog Service ---")
    status, slots = get_json(f"{gateway_url}/api/v1/catalog/slots?offeringId={offering_id}", token=token_1)
    print(f"Slots from Catalog: {slots}")
    target_slot = next((s for s in slots if s["slotId"] == slot_id_1), None)
    if not target_slot or target_slot["status"] != "HELD":
        print("Catalog slot was not marked as HELD!")
        sys.exit(1)

    print("\n--- Test Step 2: Concurrency Check (Attempt Hold on same slot by another customer) ---")
    hold_data_2 = hold_data.copy()
    hold_data_2["customerId"] = customer_id_2
    status, res_2 = post_json(
        f"{gateway_url}/api/v1/appointments/hold",
        hold_data_2,
        token=token_2
    )
    print(f"Concurrency Hold Response (Expected 400): {status} -> {res_2}")
    if status != 400:
        print("Failed concurrency check! Allow double hold on the same slot.")
        sys.exit(1)

    print("\n--- Test Step 3: Confirm Appointment ---")
    status, confirm_res = post_json(
        f"{gateway_url}/api/v1/appointments/{appointment_id}/confirm",
        None,
        token=token_1
    )
    print(f"Confirm Response: {status} -> {confirm_res}")
    if status != 200 or confirm_res.get("status") != "CONFIRMED":
        print("Failed to confirm appointment!")
        sys.exit(1)

    print("\n--- Test Step 3.1: Verify Slot status is BOOKED in Catalog Service ---")
    status, slots = get_json(f"{gateway_url}/api/v1/catalog/slots?offeringId={offering_id}", token=token_1)
    target_slot = next((s for s in slots if s["slotId"] == slot_id_1), None)
    if not target_slot or target_slot["status"] != "BOOKED":
        print("Catalog slot was not marked as BOOKED!")
        sys.exit(1)

    print("\n--- Test Step 4: Verify Hold Timeout Expiration Loop ---")
    print("Holding Slot 2...")
    hold_data_timeout = hold_data.copy()
    hold_data_timeout["slotId"] = slot_id_2
    status, hold_timeout_res = post_json(
        f"{gateway_url}/api/v1/appointments/hold",
        hold_data_timeout,
        token=token_1
    )
    print(f"Hold Slot 2 Response: {status} -> {hold_timeout_res}")
    if status != 201:
        print("Failed to hold slot 2!")
        sys.exit(1)

    # Force expiration in DB by backdating booked_at
    conn_str = "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres"
    conn = psycopg2.connect(conn_str)
    conn.autocommit = True
    cur = conn.cursor()
    cur.execute("UPDATE appointments.appointments SET booked_at = now() - interval '6 minutes' WHERE slot_id = %s;", (slot_id_2,))
    cur.close()
    conn.close()
    print("Backdated slot 2 hold in DB to force expiration. Waiting 7 seconds for scheduler run...")
    time.sleep(7)

    print("Checking if slot 2 returned to AVAILABLE in Catalog Service...")
    status, slots = get_json(f"{gateway_url}/api/v1/catalog/slots?offeringId={offering_id}", token=token_1)
    target_slot_2 = next((s for s in slots if s["slotId"] == slot_id_2), None)
    print(f"Slot 2 Status: {target_slot_2.get('status')}")
    if not target_slot_2 or target_slot_2["status"] != "AVAILABLE":
        print("Failed hold timeout expiration! Slot did not return to AVAILABLE.")
        sys.exit(1)

    print("\nSUCCESS: All appointment booking integration checks passed successfully!")

if __name__ == "__main__":
    setup_db_mock_data()
    run_integration_test()
