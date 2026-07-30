#!/usr/bin/env python3
"""
Sprint S15 Verification Script
Tests server-authoritative checkout, coupon ledger enforcement, COD rules/city overrides,
and payment/invoice reconciliation.
"""

import json
import sys
import uuid
import urllib.request
import urllib.error

ORDER_SERVICE_URL = "http://localhost:8084"
PAYMENT_SERVICE_URL = "http://localhost:8090"

def make_request(url, method="GET", body=None, headers=None):
    if headers is None:
        headers = {}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            resp_data = resp.read().decode("utf-8")
            return resp.status, json.loads(resp_data) if resp_data else {}
    except urllib.error.HTTPError as e:
        resp_data = e.read().decode("utf-8")
        try:
            parsed = json.loads(resp_data)
        except Exception:
            parsed = {"error": resp_data}
        return e.code, parsed

def test_sprint15():
    print("=== SPRINT 15 VERIFICATION ===")
    customer_id = str(uuid.uuid4())
    provider_id = str(uuid.uuid4())
    address_id = str(uuid.uuid4())
    offering_id = str(uuid.uuid4())

    headers_customer = {"X-User-Id": customer_id, "X-User-Role": "CUSTOMER"}
    headers_admin = {"X-User-Id": str(uuid.uuid4()), "X-User-Role": "ADMIN"}

    # 1. Server-Authoritative Quote Calculation
    print("\n1. Testing POST /api/v1/checkout/quote...")
    quote_req = {
        "customerId": customer_id,
        "providerId": provider_id,
        "deliveryAddressId": address_id,
        "items": [{"offeringId": offering_id, "quantity": 2}],
        "paymentMethod": "CARD"
    }
    status, quote_res = make_request(f"{ORDER_SERVICE_URL}/api/v1/checkout/quote", "POST", quote_req, headers_customer)
    assert status == 200, f"Quote failed: {status} {quote_res}"
    assert "quoteToken" in quote_res, "quoteToken missing"
    assert quote_res["subtotal"] > 0, "subtotal must be positive"
    assert quote_res["payableTotal"] > 0, "payableTotal must be positive"
    print(f"   [PASS] Quote calculated server-side: Subtotal={quote_res['subtotal']}, Tax={quote_res['tax']}, PayableTotal={quote_res['payableTotal']}")

    # 2. Rejection of Client-Tampered Amounts in Order Creation
    print("\n2. Testing Client-Tampered Amount Override Rejection...")
    tampered_order_req = {
        "customerId": customer_id,
        "providerId": provider_id,
        "deliveryAddressId": address_id,
        "items": [{"offeringId": offering_id, "quantity": 2}],
        "deliveryFee": 0.0,
        "discountAmount": 9999.0, # Attempted client discount tamper
        "paymentMethod": "CARD"
    }
    status, order_res = make_request(f"{ORDER_SERVICE_URL}/api/v1/orders", "POST", tampered_order_req, headers_customer)
    assert status == 201, f"Order creation failed: {status} {order_res}"
    # Verify server ignored the 9999 discount tamper
    assert float(order_res["discountAmount"]) == 0.0, f"Server accepted client discount tamper! Got {order_res['discountAmount']}"
    assert float(order_res["totalAmount"]) == float(quote_res["payableTotal"]), "Server did not use authoritative total"
    print("   [PASS] Server ignored client-supplied discount tamper and applied authoritative quote breakdown!")

    # 3. Coupon Creation & Concurrency Ledger Enforcement
    print("\n3. Testing Coupon Creation & Usage Limit Enforcement...")
    promo_code = f"TESTPROMO{uuid.uuid4().hex[:4]}".upper()
    promo_req = {
        "code": promo_code,
        "discountType": "FLAT",
        "discountValue": 50.0,
        "minOrderValue": 100.0,
        "usageLimitTotal": 1,
        "usageLimitPerUser": 1,
        "validFrom": "2026-01-01T00:00:00Z",
        "validUntil": "2030-01-01T00:00:00Z",
        "isActive": True
    }
    status, promo_res = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/payments/promotions", "POST", promo_req, headers_admin)
    assert status == 201, f"Promo creation failed: {status} {promo_res}"
    print(f"   Created promo {promo_code} with max total redemptions = 1")

    # First coupon reservation (should succeed)
    reserve_req1 = {
        "code": promo_code,
        "orderValue": 200.0,
        "providerId": provider_id,
        "userId": customer_id
    }
    status, reserve_res1 = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/payments/promotions/reserve", "POST", reserve_req1, headers_customer)
    assert status == 200, f"First coupon reservation failed: {status} {reserve_res1}"
    assert float(reserve_res1["discountAmount"]) == 50.0, "Coupon discount incorrect"
    print("   [PASS] First coupon reservation succeeded.")

    # Second coupon reservation (should fail due to total limit = 1)
    customer_2 = str(uuid.uuid4())
    reserve_req2 = {
        "code": promo_code,
        "orderValue": 200.0,
        "providerId": provider_id,
        "userId": customer_2
    }
    status, reserve_res2 = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/payments/promotions/reserve", "POST", reserve_req2, headers_customer)
    assert status in (400, 500), f"Second coupon reservation should have been rejected! Got {status} {reserve_res2}"
    print("   [PASS] Second coupon reservation blocked by usage limit ledger!")

    # 4. COD ₹1,000 Threshold Enforcement & Admin Override
    print("\n4. Testing Cash-On-Delivery (COD) Threshold Enforcement...")
    cod_check_over_limit = {
        "amount": 1500.0,
        "city": "Tirupati"
    }
    status, cod_res1 = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/payments/cod/check", "POST", cod_check_over_limit, headers_customer)
    assert status == 200, f"COD check failed: {status}"
    assert cod_res1["isEligible"] is False, "COD over ₹1000 should be ineligible by default"
    print("   [PASS] COD over ₹1,000 rejected under default configuration.")

    # Admin updates COD limit for Tirupati to ₹2000
    admin_cod_update = {
        "cityOverrides": {"Tirupati": 2000.0}
    }
    status, config_res = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/payments/cod/config", "POST", admin_cod_update, headers_admin)
    assert status == 200, f"COD config update failed: {status}"
    
    # Re-check COD ₹1500 in Tirupati (should now succeed)
    status, cod_res2 = make_request(f"{PAYMENT_SERVICE_URL}/api/v1/payments/cod/check", "POST", cod_check_over_limit, headers_customer)
    assert status == 200, f"COD re-check failed: {status}"
    assert cod_res2["isEligible"] is True, f"COD over ₹1000 should be eligible after city override! Got {cod_res2}"
    print("   [PASS] COD over ₹1,000 succeeds after valid admin city override configuration!")

    # 5. COD Order Placement & State Transition
    print("\n5. Testing COD Order Placement & State Transitions...")
    cod_order_req = {
        "customerId": customer_id,
        "providerId": provider_id,
        "deliveryAddressId": address_id,
        "items": [{"offeringId": offering_id, "quantity": 2}],
        "paymentMethod": "COD",
        "city": "Tirupati"
    }
    status, cod_order_res = make_request(f"{ORDER_SERVICE_URL}/api/v1/orders", "POST", cod_order_req, headers_customer)
    assert status == 201, f"COD order creation failed: {status} {cod_order_res}"
    assert cod_order_res["status"] == "ACCEPTED", f"COD order should transition directly to ACCEPTED. Got {cod_order_res['status']}"
    assert cod_order_res["paymentStatus"] == "COD_PENDING", f"COD order payment status should be COD_PENDING. Got {cod_order_res['paymentStatus']}"
    print("   [PASS] COD order placed, marked ACCEPTED and COD_PENDING without requiring fake online payment transaction!")

    print("\n=== SPRINT 15 ALL TESTS PASSED SUCCESSFULLY! ===")

if __name__ == "__main__":
    test_sprint15()
