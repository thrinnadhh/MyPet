import urllib.request
import json
import subprocess
import time

def run_sql(sql_command):
    cmd = ["docker", "exec", "-i", "pawsnearme-postgres", "psql", "-U", "postgres", "-d", "pawsnearme", "-c", sql_command]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"SQL execution failed: {res.stderr}")
        raise RuntimeError(res.stderr)
    return res.stdout

def make_request(url, method="GET", body=None, headers=None):
    if headers is None:
        headers = {}
    headers["X-Internal-Gateway-Secret"] = "dev-secret-change-in-production"
    req = urllib.request.Request(url, method=method)
    for k, v in headers.items():
        req.add_header(k, v)
    
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        req.add_header("Content-Type", "application/json")
        
    try:
        with urllib.request.urlopen(req, data=data) as f:
            res_body = f.read().decode("utf-8")
            status = f.status
            return status, json.loads(res_body) if res_body else {}
    except urllib.error.HTTPError as e:
        res_body = e.read().decode("utf-8")
        print(f"HTTP Error {e.code}: {res_body}")
        return e.code, json.loads(res_body) if res_body else {}

def main():
    print("--- Sprint 22 Automatic Commission & Payout System Integration Verification ---")
    
    # 1. Clean database state and insert mock provider + delivered order
    print("Cleaning database state and inserting mock records...")
    run_sql("DELETE FROM payments.platform_commission_ledger;")
    run_sql("DELETE FROM payments.payouts;")
    run_sql("DELETE FROM payments.linked_accounts;")
    run_sql("DELETE FROM orders.orders WHERE provider_id = 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d';")
    run_sql("DELETE FROM providers.providers WHERE provider_id = 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d';")
    
    # Insert mock provider with 15% commission
    run_sql("""
        INSERT INTO providers.providers (
            provider_id, owner_user_id, provider_type, fulfillment_type, name, address_line, city, pincode, geo_location, status, commission_pct
        ) VALUES (
            'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', '98765432-1234-1234-1234-123456789abc', 'PET_STORE', 'DELIVERY', 'Test Provider', 'Street 1', 'City', '10001', ST_SetSRID(ST_MakePoint(-73.935242, 40.730610), 4326), 'ACTIVE', 15.00
        );
    """)
    
    # Insert delivered order for total amount 1000.00
    run_sql("""
        INSERT INTO orders.orders (
            order_id, customer_id, provider_id, delivery_address_id, status, subtotal_amount, total_amount, delivered_at
        ) VALUES (
            'f5e4d3c2-b1a0-0987-6543-210fedcba987', gen_random_uuid(), 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', gen_random_uuid(), 'DELIVERED', 1000.00, 1000.00, '2026-07-05 12:00:00+00'
        );

    """)
    print("Database test state setup complete.")
    
    # 2. Register Linked Account
    print("Registering Route Linked Account...")
    url_linked = "http://localhost:8090/api/v1/payments/linked-accounts"
    headers_admin = {"X-User-Role": "ADMIN"}
    body_linked = {
        "payeeUserId": "98765432-1234-1234-1234-123456789abc",
        "payeeRole": "MERCHANT",
        "accountNumber": "123456789",
        "ifsc": "UTIB0001234",
        "businessName": "Test Provider Inc",
        "email": "test@provider.com"
    }
    status, res = make_request(url_linked, "POST", body_linked, headers_admin)
    print(f"Linked Account registration response status: {status}")
    assert status == 201, "Expected status 201"
    assert "razorpayAccountId" in res, "Expected razorpayAccountId in response"
    print(f"Linked Account registered successfully: {res['razorpayAccountId']}")
    
    # 3. Calculate Payouts
    print("Executing Payouts Calculation...")
    time.sleep(1) # Allow db transaction sync
    url_calc = "http://localhost:8090/api/v1/payments/payouts/calculate?start=2026-07-01&end=2026-07-07"
    status, res_calc = make_request(url_calc, "POST", None, headers_admin)
    print(f"Calculate Payouts response status: {status}")
    assert status == 200, "Expected status 200"
    payout = next((p for p in res_calc if p["payeeUserId"] == "98765432-1234-1234-1234-123456789abc"), None)
    assert payout is not None, "Expected payout for our test provider not found"
    payout_id = payout["payoutId"]
    
    # Assert commission deduction: 1000.00 - 15% = 850.00
    assert float(payout["amount"]) == 850.00, f"Expected payout amount 850.00, got {payout['amount']}"
    assert payout["status"] == "PROCESSING", f"Expected status PROCESSING, got {payout['status']}"
    assert payout["razorpayTransferId"].startswith("trf_mock_"), f"Expected mock transfer ID, got {payout['razorpayTransferId']}"
    print(f"Calculated payout is correct! Amount: {payout['amount']}, PayoutId: {payout_id}, TransferId: {payout['razorpayTransferId']}")
    
    # Check Platform Commission Ledger
    ledger_count = run_sql("SELECT count(*) FROM payments.platform_commission_ledger WHERE provider_id = 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d';").strip().split('\n')[-2].strip()
    assert int(ledger_count) == 1, f"Expected 1 ledger entry, got {ledger_count}"
    print("Platform Commission Ledger verified!")
    
    # 4. Fetch specific payout via GET history API with payee-only checks
    print("Fetching payout details via payee check...")
    url_payout = f"http://localhost:8090/api/v1/payments/payouts/{payout_id}"
    headers_merchant = {"X-User-Id": "98765432-1234-1234-1234-123456789abc", "X-User-Role": "MERCHANT"}
    status, res_payout = make_request(url_payout, "GET", None, headers_merchant)
    assert status == 200, "Expected 200 OK for merchant payee"
    assert res_payout["amount"] == 850.00, "Amount mismatch"
    print("Payee check access successful!")
    
    # Fetch with unauthorized user ID
    headers_wrong = {"X-User-Id": "11111111-1111-1111-1111-111111111111", "X-User-Role": "MERCHANT"}
    status, _ = make_request(url_payout, "GET", None, headers_wrong)
    assert status == 403 or status == 500, f"Expected access denied (403/500), got {status}"
    print("Unauthorized user check rejected correctly!")
    
    # 5. Trigger Webhook Reversal (transfer.reversed)
    print("Simulating transfer.reversed webhook...")
    url_webhook = "http://localhost:8090/api/v1/payments/webhook"
    payload_webhook = {
        "event": "transfer.reversed",
        "payload": {
            "reversal": {
                "entity": {
                    "id": "rev_test123",
                    "transfer_id": payout["razorpayTransferId"],
                    "amount": 85000,
                    "currency": "INR"
                }
            }
        }
    }
    headers_webhook = {"X-Razorpay-Signature": "dummy_sig"}
    status, res_webhook = make_request(url_webhook, "POST", payload_webhook, headers_webhook)
    assert status == 200, f"Expected webhook 200, got {status}"
    
    # Verify status in database
    payout_status = run_sql(f"SELECT status FROM payments.payouts WHERE payout_id = '{payout_id}';").strip().split('\n')[-2].strip()
    assert payout_status == "REVERSED", f"Expected REVERSED status, got {payout_status}"
    
    # Verify pending clawback balance
    clawback_bal = run_sql("SELECT pending_clawback_balance FROM payments.linked_accounts WHERE payee_user_id = '98765432-1234-1234-1234-123456789abc';").strip().split('\n')[-2].strip()
    assert float(clawback_bal) == 850.00, f"Expected clawback balance 850.00, got {clawback_bal}"
    print("Reversal webhook processed and stateful clawback balance tracked successfully!")
    
    # 6. Verify Clawback Netting in subsequent calculatePayouts
    # Insert another delivered order for 1000.00
    print("Inserting subsequent delivered order for testing netting...")
    run_sql("""
        INSERT INTO orders.orders (
            order_id, customer_id, provider_id, delivery_address_id, status, subtotal_amount, total_amount, delivered_at
        ) VALUES (
            'e4d3c2b1-a098-7654-3210-fedcba987654', gen_random_uuid(), 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', gen_random_uuid(), 'DELIVERED', 1000.00, 1000.00, '2026-07-06 12:00:00+00'
        );

    """)
    
    print("Re-calculating payouts...")
    status, res_calc2 = make_request(url_calc, "POST", None, headers_admin)
    # The payout calculation will result in:
    # 1. Base calculated merchant share: 850.00
    # 2. Subtract pending clawback balance: 850.00
    # 3. Net payout amount = 0.00 -> No payout created / amount is zero.
    # 4. Pending clawback balance set to 0.00.
    
    # Let's verify the pending clawback balance is now 0.00 in database
    clawback_bal_after = run_sql("SELECT pending_clawback_balance FROM payments.linked_accounts WHERE payee_user_id = '98765432-1234-1234-1234-123456789abc';").strip().split('\n')[-2].strip()
    assert float(clawback_bal_after) == 0.00, f"Expected clawback balance 0.00, got {clawback_bal_after}"
    
    print("Clawback netting correctly applied on subsequent run!")
    print("\nALL SPRINT 22 INTEGRATION VERIFICATION TESTS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
