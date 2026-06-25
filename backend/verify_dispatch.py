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
    print("Connecting to DB to set up clean test state...")
    conn = psycopg2.connect(conn_str)
    conn.autocommit = True
    cur = conn.cursor()

    captain_id = "99999999-9999-9999-9999-999999999999"
    customer_id = "11111111-1111-1111-1111-111111111111"
    merchant_id = "d3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e"
    provider_id = "22222222-2222-2222-2222-222222222222"
    offering_id = "33333333-3333-3333-3333-333333333333"
    address_id = "55555555-5555-5555-5555-555555555555"

    print("Cleaning up old test data...")
    # Delete from dependent tables first
    cur.execute("DELETE FROM captains.captain_earnings WHERE captain_id = %s;", (captain_id,))
    cur.execute("DELETE FROM captains.captain_profiles WHERE captain_id = %s;", (captain_id,))
    cur.execute("DELETE FROM dispatch.dispatch_offers WHERE captain_id = %s;", (captain_id,))
    cur.execute("DELETE FROM dispatch.dispatch_jobs WHERE order_id IN (SELECT order_id FROM orders.orders WHERE customer_id = %s);", (customer_id,))
    cur.execute("DELETE FROM orders.order_items WHERE offering_id = %s;", (offering_id,))
    cur.execute("DELETE FROM orders.order_status_history WHERE changed_by_user_id IN (%s, %s, %s);", (customer_id, merchant_id, captain_id))
    cur.execute("DELETE FROM orders.orders WHERE customer_id = %s;", (customer_id,))
    cur.execute("DELETE FROM catalog.offerings WHERE offering_id = %s;", (offering_id,))
    cur.execute("DELETE FROM providers.providers WHERE provider_id = %s;", (provider_id,))
    cur.execute("DELETE FROM identity.addresses WHERE address_id = %s;", (address_id,))
    cur.execute("DELETE FROM identity.user_roles WHERE user_id IN (%s, %s, %s);", (captain_id, customer_id, merchant_id))
    cur.execute("DELETE FROM identity.profiles WHERE user_id IN (%s, %s, %s);", (captain_id, customer_id, merchant_id))
    cur.execute("DELETE FROM auth.users WHERE id IN (%s, %s, %s);", (captain_id, customer_id, merchant_id))

    print("Inserting fresh test users...")
    cur.execute("INSERT INTO auth.users (id, email) VALUES (%s, %s);", (captain_id, "captain@pawsnearme.com"))
    cur.execute("INSERT INTO auth.users (id, email) VALUES (%s, %s);", (customer_id, "customer@pawsnearme.com"))
    cur.execute("INSERT INTO auth.users (id, email) VALUES (%s, %s);", (merchant_id, "merchant@pawsnearme.com"))

    # Cleanup roles and profiles auto-created by triggers to avoid duplicate conflicts
    cur.execute("DELETE FROM identity.user_roles WHERE user_id IN (%s, %s, %s);", (captain_id, customer_id, merchant_id))
    cur.execute("DELETE FROM identity.profiles WHERE user_id IN (%s, %s, %s);", (captain_id, customer_id, merchant_id))

    # Explicit inserts to match testing expectation
    cur.execute("INSERT INTO identity.profiles (user_id, role, full_name, phone_number) VALUES (%s, 'CAPTAIN', 'Test Captain', '+919999999999');", (captain_id,))
    cur.execute("INSERT INTO identity.profiles (user_id, role, full_name, phone_number) VALUES (%s, 'CUSTOMER', 'Test Customer', '+911111111111');", (customer_id,))
    cur.execute("INSERT INTO identity.profiles (user_id, role, full_name, phone_number) VALUES (%s, 'MERCHANT', 'Test Merchant', '+918888888888');", (merchant_id,))

    cur.execute("INSERT INTO identity.user_roles (user_id, role) VALUES (%s, 'CAPTAIN');", (captain_id,))
    cur.execute("INSERT INTO identity.user_roles (user_id, role) VALUES (%s, 'CUSTOMER');", (customer_id,))
    cur.execute("INSERT INTO identity.user_roles (user_id, role) VALUES (%s, 'MERCHANT');", (merchant_id,))

    print("Inserting Captain Profile, Address, Provider, and Offering...")
    cur.execute("INSERT INTO captains.captain_profiles (captain_id, status, vehicle_type, vehicle_number) VALUES (%s, 'ACTIVE', 'BIKE', 'DL3S-1234');", (captain_id,))
    
    cur.execute("""
        INSERT INTO identity.addresses (address_id, user_id, label, line1, city, state, pincode, geo_lat, geo_lng) 
        VALUES (%s, %s, 'Home', '12B Sector 4 R.K. Puram', 'Delhi', 'Delhi', '110022', 28.5684, 77.1703);
    """, (address_id, customer_id))

    # Location in Delhi near CP
    cur.execute("""
        INSERT INTO providers.providers (provider_id, owner_user_id, provider_type, fulfillment_type, name, address_line, city, pincode, geo_location, status) 
        VALUES (%s, %s, 'PET_STORE', 'DELIVERY', 'Paws Palace CP', 'A-54 Connaught Place', 'Delhi', '110001', ST_GeographyFromText('SRID=4326;POINT(77.2177 28.6304)'), 'ACTIVE');
    """, (provider_id, merchant_id))

    cur.execute("""
        INSERT INTO catalog.offerings (offering_id, provider_id, name, price, stock_quantity) 
        VALUES (%s, %s, 'Super Dog Chow', 499.00, 100);
    """, (offering_id, provider_id))

    cur.close()
    conn.close()
    print("Database mock data setup successfully!")

def run_integration_test():
    gateway_url = "http://localhost:8080"
    captain_id = "99999999-9999-9999-9999-999999999999"
    customer_id = "11111111-1111-1111-1111-111111111111"
    merchant_id = "d3b07384-d113-4e4e-9c8e-3d8e3d8e3d8e"
    provider_id = "22222222-2222-2222-2222-222222222222"
    offering_id = "33333333-3333-3333-3333-333333333333"
    address_id = "55555555-5555-5555-5555-555555555555"
    conn_str = "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres"

    # Generate mock JWTs
    captain_token = generate_mock_jwt(captain_id, "CAPTAIN")
    customer_token = generate_mock_jwt(customer_id, "CUSTOMER")
    merchant_token = generate_mock_jwt(merchant_id, "MERCHANT")

    print("\n--- Test Step 1: Make Captain Online via API Gateway ---")
    status, res = put_json(
        f"{gateway_url}/api/v1/captains/status",
        {"online": True, "longitude": 77.2177, "latitude": 28.6304},
        token=captain_token
    )
    print(f"Online Status Response: {status} -> {res}")
    if status != 200:
        print("Failed to make Captain online!")
        sys.exit(1)

    print("\n--- Test Step 2: Customer Places Order ---")
    order_data = {
        "customerId": customer_id,
        "providerId": provider_id,
        "deliveryAddressId": address_id,
        "items": [
            {"offeringId": offering_id, "quantity": 1}
        ],
        "deliveryFee": 150.00,
        "discountAmount": 0.00
    }
    status, order = post_json(
        f"{gateway_url}/api/v1/orders",
        order_data,
        token=customer_token
    )
    print(f"Order Placed Response: {status} -> {order}")
    if status != 201:
        print("Failed to place order!")
        sys.exit(1)
    order_id = order["orderId"]

    print("\n--- Test Step 3: Merchant Accepts Order ---")
    status, res = put_json(
        f"{gateway_url}/api/v1/orders/{order_id}/status?status=ACCEPTED",
        None,
        token=merchant_token
    )
    print(f"Accept Order Response: {status} -> {res}")
    if status != 200:
        print("Failed to accept order!")
        sys.exit(1)

    print("\n--- Test Step 4: Merchant Marks Order Ready for Pickup ---")
    status, res = put_json(
        f"{gateway_url}/api/v1/orders/{order_id}/status?status=READY_FOR_PICKUP",
        None,
        token=merchant_token
    )
    print(f"Ready for Pickup Response: {status} -> {res}")
    if status != 200:
        print("Failed to mark order ready for pickup!")
        sys.exit(1)

    print("\n--- Test Step 5: Poll Dispatch Offers for Captain ---")
    offer = None
    for i in range(10):
        print(f"Polling active offers (Attempt {i+1}/10)...")
        status, offers = get_json(
            f"{gateway_url}/api/v1/dispatch/offers",
            token=captain_token
        )
        if status == 200 and len(offers) > 0:
            offer = offers[0]
            print(f"Found Active Offer: {offer}")
            break
        time.sleep(1)

    if not offer:
        print("Failed to receive dispatch offer. Kafka event routing or matching might be delayed/failing.")
        sys.exit(1)

    offer_id = offer["offerId"]

    print("\n--- Test Step 6: Captain Accepts Offer ---")
    status, res = post_json(
        f"{gateway_url}/api/v1/dispatch/offers/{offer_id}/respond?response=ACCEPTED",
        {},
        token=captain_token
    )
    print(f"Accept Offer Response: {status} -> {res}")
    if status != 200:
        print("Failed to accept offer!")
        sys.exit(1)

    print("\n--- Test Step 7: Verify Order Status updated to ASSIGNED and Captain ID set ---")
    status, order_check = get_json(f"{gateway_url}/api/v1/orders/{order_id}", token=customer_token)
    print(f"Order Check Status: {status}")
    print(f"Order Status: {order_check.get('status')}, Captain ID: {order_check.get('captainId')}")
    if order_check.get('status') != "ASSIGNED" or order_check.get('captainId') != captain_id:
        print("Order service not updated to ASSIGNED with captainId!")
        sys.exit(1)

    print("\n--- Test Step 8: Captain Marks Order as Picked Up ---")
    status, res = put_json(
        f"{gateway_url}/api/v1/orders/{order_id}/status?status=PICKED_UP",
        None,
        token=captain_token
    )
    print(f"Picked Up Status: {status} -> {res}")
    if status != 200:
        print("Failed to update status to PICKED_UP!")
        sys.exit(1)

    print("\n--- Test Step 9: Captain Marks Order as Delivered ---")
    status, res = put_json(
        f"{gateway_url}/api/v1/orders/{order_id}/status?status=DELIVERED",
        None,
        token=captain_token
    )
    print(f"Delivered Status: {status} -> {res}")
    if status != 200:
        print("Failed to update status to DELIVERED!")
        sys.exit(1)

    print("\n--- Test Step 10: Verify Captain Earnings Logged ---")
    earnings = []
    for i in range(5):
        print(f"Polling earnings (Attempt {i+1}/5)...")
        status, earnings = get_json(
            f"{gateway_url}/api/v1/captains/{captain_id}/earnings",
            token=captain_token
        )
        if status == 200 and len(earnings) > 0:
            print(f"Earning Record Logged: {earnings}")
            break
        time.sleep(1)

    if not earnings:
        print("Failed to log Captain earnings! Kafka OrderDelivered event consumer might be failing.")
        sys.exit(1)

    print(f"Earning amount: {earnings[0].get('amount')}")
    print("\nSUCCESS: All integration checks passed successfully! Dispatch offer accepted, pickup and delivery completed, earnings logged!")

    # Test Step 11: Timeout Retry Loop verification
    print("\n--- Test Step 11: Verification of Offer Timeout & Timeout Retry Loop ---")
    print("Placing another order...")
    status, order_t = post_json(
        f"{gateway_url}/api/v1/orders",
        order_data,
        token=customer_token
    )
    order_id_t = order_t["orderId"]
    
    put_json(f"{gateway_url}/api/v1/orders/{order_id_t}/status?status=ACCEPTED", None, token=merchant_token)
    put_json(f"{gateway_url}/api/v1/orders/{order_id_t}/status?status=READY_FOR_PICKUP", None, token=merchant_token)
    
    print("Waiting for dispatch offer to be generated...")
    offer_t = None
    for i in range(10):
        status, offers = get_json(
            f"{gateway_url}/api/v1/dispatch/offers",
            token=captain_token
        )
        if status == 200 and len(offers) > 0:
            offer_t = offers[0]
            print(f"Offer for timeout check received: {offer_t}")
            break
        time.sleep(1)
        
    if not offer_t:
        print("Failed to receive second dispatch offer.")
        sys.exit(1)

    print("We will wait 35 seconds to let the offer time out automatically via Scheduled task...")
    time.sleep(35)
    
    # Query database directly to check if offer response became 'TIMED_OUT'
    print("Connecting to DB to check offer status...")
    conn = psycopg2.connect(conn_str)
    cur = conn.cursor()
    cur.execute("SELECT response, responded_at FROM dispatch.dispatch_offers WHERE offer_id = %s;", (offer_t["offerId"],))
    row = cur.fetchone()
    cur.close()
    conn.close()
    
    print(f"DB Offer status: response = '{row[0]}', responded_at = {row[1]}")
    if row[0] == 'TIMED_OUT':
        print("SUCCESS: Timeout retry loop verified! Offer automatically timed out after 30 seconds.")
    else:
        print(f"FAILED: Offer did not time out. Current response: {row[0]}")
        sys.exit(1)

if __name__ == "__main__":
    setup_db_mock_data()
    run_integration_test()
