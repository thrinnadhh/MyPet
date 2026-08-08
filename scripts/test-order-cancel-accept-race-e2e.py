#!/usr/bin/env python3
"""Release-critical Customer CANCEL vs Merchant ACCEPT concurrency certification.

This uses the public gateway against the already-running clean stack. It proves
both deterministic serialization orders and then fires a barrier-synchronized
pair of competing requests. The existing business rule permits a customer to
cancel while an order is PLACED or ACCEPTED, so the only valid settled state
once the customer cancellation request succeeds is CANCELLED.
"""

from __future__ import annotations

import importlib.util
import json
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
REPORT = Path(os.environ.get("MYPET_SMOKE_REPORT", ROOT / "build/reports/full-stack-smoke.md"))
MATRIX_PATH = ROOT / "scripts/test-m8-feature-matrix.py"

spec = importlib.util.spec_from_file_location("mypet_order_race_matrix", MATRIX_PATH)
if spec is None or spec.loader is None:
    raise SystemExit(f"Unable to load test helpers from {MATRIX_PATH}")
matrix = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = matrix
spec.loader.exec_module(matrix)

compose_files_raw = os.environ.get("MYPET_COMPOSE_FILES", "")
if compose_files_raw:
    matrix.COMPOSE = [
        "docker", "compose", "-p", matrix.PROJECT_NAME, "--env-file", matrix.ENV_FILE,
    ]
    for compose_file in compose_files_raw.split(","):
        if compose_file:
            matrix.COMPOSE.extend(("-f", compose_file))


def require(condition: bool, message: str, details: Any = None) -> None:
    matrix.require(condition, message, details)


def stock(offering_id: str) -> int:
    return int(matrix.sql(
        "SELECT stock_quantity FROM catalog.offerings "
        f"WHERE offering_id='{offering_id}'::uuid;"
    ))


def db_status(order_id: str) -> str:
    return matrix.sql(
        "SELECT status::text FROM orders.orders "
        f"WHERE order_id='{order_id}'::uuid;"
    )


def history(order_id: str) -> list[str]:
    raw = matrix.sql(
        "SELECT coalesce(string_agg(to_status::text, ',' ORDER BY changed_at, history_id),'') "
        "FROM orders.order_status_history "
        f"WHERE order_id='{order_id}'::uuid;"
    )
    return [value for value in raw.split(",") if value]


def payment_count(order_id: str) -> int:
    return int(matrix.sql(
        "SELECT count(*) FROM payments.transactions "
        f"WHERE reference_id='{order_id}'::uuid;"
    ) or "0")


def notification_counts(order_id: str, customer_id: str, merchant_id: str) -> tuple[int, int, int]:
    row = matrix.sql(
        "SELECT "
        f"count(*) FILTER (WHERE user_id='{customer_id}'::uuid AND notification_type='ORDER_CANCELLED') || '|' || "
        f"count(*) FILTER (WHERE user_id='{merchant_id}'::uuid AND notification_type='MERCHANT_ORDER_ALERT') || '|' || "
        f"count(*) FILTER (WHERE user_id='{merchant_id}'::uuid AND notification_type='MERCHANT_ORDER_CANCELLED') "
        "FROM notifications.in_app_notifications "
        f"WHERE reference_id='{order_id}'::uuid;"
    )
    return tuple(int(value) for value in row.split("|", 2))  # type: ignore[return-value]


def assert_settled(
    order_id: str,
    provider_id: str,
    offering_id: str,
    expected_stock: int,
    customer: Any,
    merchant: Any,
    *,
    accepted_may_exist: bool,
) -> None:
    require(db_status(order_id) == "CANCELLED", "DB order did not settle to CANCELLED", history(order_id))

    customer_view = matrix.request("GET", f"/api/v1/orders/{order_id}", customer)
    require(customer_view["status"] == "CANCELLED", "Customer projection disagrees with DB", customer_view)

    merchant_orders = matrix.request("GET", f"/api/v1/orders/provider/{provider_id}", merchant)
    merchant_view = next((row for row in merchant_orders if row.get("orderId") == order_id), None)
    require(merchant_view is not None, "Merchant projection lost the raced order", merchant_orders)
    require(merchant_view["status"] == "CANCELLED", "Merchant projection disagrees with DB", merchant_view)

    require(stock(offering_id) == expected_stock, "Inventory was not restored exactly once")
    require(customer_view.get("couponCode") is None, "Race fixture unexpectedly carried a coupon", customer_view)
    require(payment_count(order_id) == 0, "COD race created an unintended payment transaction")

    states = history(order_id)
    require(states.count("CANCELLED") == 1, "Cancellation history was duplicated", states)
    require(states.count("ACCEPTED") <= (1 if accepted_may_exist else 0), "Acceptance history is invalid", states)
    require(states[-1] == "CANCELLED", "Final history entry is not CANCELLED", states)

    counts = matrix.poll(
        "race cancellation notifications",
        lambda: notification_counts(order_id, customer.user_id, merchant.user_id),
        lambda value: value[0] == 1 and value[1] == 1 and value[2] == 1,
        timeout=30,
    )
    time.sleep(2)
    require(
        notification_counts(order_id, customer.user_id, merchant.user_id) == counts,
        "Event retry produced duplicate race notifications",
        notification_counts(order_id, customer.user_id, merchant.user_id),
    )

    retry = matrix.request(
        "POST",
        f"/api/v1/orders/{order_id}/cancel?reason=duplicate-race-retry",
        customer,
        {},
        expected=(409,),
    )
    require(retry.get("code") == "STATE_CONFLICT", "Repeated cancellation did not fail as state conflict", retry)
    require(stock(offering_id) == expected_stock, "Cancellation retry changed restored inventory")


def main() -> int:
    matrix.append_report("\n## Customer cancel vs merchant accept concurrency certification\n\n")

    customer = matrix.make_actor("CUSTOMER", "race-customer")
    merchant = matrix.make_actor("MERCHANT", "race-merchant")
    admin = matrix.make_actor("ADMIN", "race-admin")
    for actor in (customer, merchant, admin):
        matrix.sync_profile(actor)

    address = matrix.request(
        "POST", "/api/v1/addresses", customer,
        {
            "label": "Race Home",
            "line1": "Concurrency Test Street",
            "line2": None,
            "city": "Tirupati",
            "state": "Andhra Pradesh",
            "pincode": "517501",
            "geoLat": 13.6288,
            "geoLng": 79.4192,
            "isDefault": True,
        },
        expected=(201,),
    )
    matrix.request(
        "PUT", f"/api/v1/addresses/{address['addressId']}/contact", customer,
        {"phoneNumber": "+919876543210"}, expected=(200,),
    )

    provider = matrix.activate_provider(
        matrix.create_provider(merchant, "PET_STORE", "DELIVERY", "Race Certified Pet Store")["providerId"],
        merchant,
        admin,
    )
    offering = matrix.create_offering(
        merchant,
        provider["providerId"],
        "Race Certified Dog Food",
        "FOOD",
        199.0,
        8,
        None,
    )
    provider_id = provider["providerId"]
    offering_id = offering["offeringId"]

    def create_order(label: str) -> tuple[dict[str, Any], int]:
        before = stock(offering_id)
        payload = {
            "providerId": provider_id,
            "deliveryAddressId": address["addressId"],
            "items": [{"offeringId": offering_id, "quantity": 1}],
            "paymentMethod": "COD",
            "city": "Tirupati",
            "latitude": 13.6288,
            "longitude": 79.4192,
        }
        quote = matrix.request("POST", "/api/v1/checkout/quote", customer, payload)
        order = matrix.request(
            "POST", "/api/v1/orders", customer,
            {**payload, "quoteToken": quote["quoteToken"]}, expected=(201,),
        )
        require(order["status"] == "PLACED", f"{label}: order did not start PLACED", order)
        require(order["paymentMethod"] == "COD", f"{label}: fixture stopped being COD", order)
        require(order["paymentStatus"] == "COD_PENDING", f"{label}: COD state is invalid", order)
        require(stock(offering_id) == before - 1, f"{label}: stock reservation missing")
        return order, before

    # Scenario A: cancellation owns the serialized state first.
    order_a, stock_a = create_order("cancel-first")
    cancelled_a = matrix.request(
        "POST", f"/api/v1/orders/{order_a['orderId']}/cancel?reason=cancel-first", customer, {}, expected=(200,),
    )
    require(cancelled_a["status"] == "CANCELLED", "Cancel-first did not cancel", cancelled_a)
    rejected_accept = matrix.request(
        "PUT", f"/api/v1/orders/{order_a['orderId']}/status?status=ACCEPTED", merchant, expected=(409,),
    )
    require(rejected_accept.get("code") == "STATE_CONFLICT", "Accept after cancellation was not rejected", rejected_accept)
    assert_settled(order_a["orderId"], provider_id, offering_id, stock_a, customer, merchant, accepted_may_exist=False)
    matrix.passed("order", "cancel-first serialization rejected stale merchant acceptance and restored stock once")

    # Scenario B: merchant acceptance owns state first. Existing product policy
    # permits the customer to cancel an ACCEPTED order, so the follow-up cancel
    # is authoritative and must restore stock exactly once.
    order_b, stock_b = create_order("accept-first")
    accepted_b = matrix.request(
        "PUT", f"/api/v1/orders/{order_b['orderId']}/status?status=ACCEPTED", merchant, expected=(200,),
    )
    require(accepted_b["status"] == "ACCEPTED", "Accept-first merchant transition failed", accepted_b)
    cancelled_b = matrix.request(
        "POST", f"/api/v1/orders/{order_b['orderId']}/cancel?reason=accepted-then-cancelled", customer, {}, expected=(200,),
    )
    require(cancelled_b["status"] == "CANCELLED", "Accepted-order cancellation policy regressed", cancelled_b)
    assert_settled(order_b["orderId"], provider_id, offering_id, stock_b, customer, merchant, accepted_may_exist=True)
    matrix.passed("order", "accept-first serialization followed the defined accepted-order cancellation policy")

    # True concurrent pair: both threads are released from the same barrier.
    order_c, stock_c = create_order("concurrent")
    barrier = threading.Barrier(2)

    def cancel_concurrently() -> Any:
        barrier.wait(timeout=10)
        return matrix.request(
            "POST", f"/api/v1/orders/{order_c['orderId']}/cancel?reason=concurrent-race", customer, {}, expected=(200, 409),
        )

    def accept_concurrently() -> Any:
        barrier.wait(timeout=10)
        return matrix.request(
            "PUT", f"/api/v1/orders/{order_c['orderId']}/status?status=ACCEPTED", merchant, expected=(200, 409),
        )

    with ThreadPoolExecutor(max_workers=2) as executor:
        cancel_future = executor.submit(cancel_concurrently)
        accept_future = executor.submit(accept_concurrently)
        cancel_result = cancel_future.result(timeout=35)
        accept_result = accept_future.result(timeout=35)

    require(
        cancel_result.get("status") == "CANCELLED",
        "Concurrent customer cancellation did not become authoritative",
        {"cancel": cancel_result, "accept": accept_result},
    )
    require(
        accept_result.get("status") == "ACCEPTED" or accept_result.get("code") == "STATE_CONFLICT",
        "Concurrent merchant response was neither serialized acceptance nor state conflict",
        accept_result,
    )
    assert_settled(order_c["orderId"], provider_id, offering_id, stock_c, customer, merchant, accepted_may_exist=True)
    matrix.passed("order", "barrier-synchronized cancel/accept requests settled to one authoritative Customer and Merchant state")

    matrix.append_report(
        "- ✅ COD race fixtures created no payment transaction and carried no coupon entitlement.\n"
        "- ✅ Customer and Merchant projections matched the authoritative DB state after every race.\n"
        "- ✅ Cancellation/merchant alert notifications were emitted exactly once despite retry exposure.\n"
    )
    print(json.dumps({
        "status": "PASS",
        "cancelFirst": order_a["orderId"],
        "acceptFirst": order_b["orderId"],
        "concurrent": order_c["orderId"],
        "concurrentAcceptResponse": accept_result,
        "concurrentCancelResponse": cancel_result,
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
