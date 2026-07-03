#!/usr/bin/env python3
"""Live Sprint 3 checkout proof against local services.

This script seeds throwaway local rows, then exercises the same order/payment
flow used by the customer app:

- successful checkout records PaymentCaptured and leaves stock decremented
- failed checkout records PaymentFailed, cancels the order, and restores stock

It requires local Postgres plus catalog-service, order-service, and
payment-service to be running from the current workspace.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from typing import Any

import psycopg2


DB_DSN = os.environ.get(
    "SPRINT3_DB_DSN",
    "host=localhost port=5433 dbname=pawsnearme user=postgres password=postgres",
)
ORDER_BASE_URL = os.environ.get("ORDER_BASE_URL", "http://localhost:8084")
PAYMENT_BASE_URL = os.environ.get("PAYMENT_BASE_URL", "http://localhost:8090")


def request_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int, dict[str, Any] | None]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Accept": "application/json"}
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
    if headers:
        request_headers.update(headers)

    request = urllib.request.Request(url, data=body, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            text = response.read().decode("utf-8")
            return response.status, json.loads(text) if text else None
    except urllib.error.HTTPError as exc:
        text = exc.read().decode("utf-8")
        try:
            data: Any = json.loads(text) if text else None
        except json.JSONDecodeError:
            data = text
        raise RuntimeError(f"{method} {url} failed with {exc.code}: {data}") from exc


def scalar(cursor: Any, sql: str, args: tuple[str, ...]) -> Any:
    cursor.execute(sql, args)
    row = cursor.fetchone()
    return row[0] if row else None


def seed_proof_data(cursor: Any) -> dict[str, uuid.UUID | str]:
    run_id = uuid.uuid4().hex[:8]
    data = {
        "run_id": run_id,
        "customer_id": uuid.uuid4(),
        "merchant_id": uuid.uuid4(),
        "provider_id": uuid.uuid4(),
        "address_id": uuid.uuid4(),
        "success_offering_id": uuid.uuid4(),
        "failure_offering_id": uuid.uuid4(),
    }

    try:
        cursor.execute(
            "INSERT INTO auth.users (id, email) VALUES (%s, %s) ON CONFLICT (id) DO NOTHING",
            (str(data["customer_id"]), f"sprint3-customer-{run_id}@example.com"),
        )
        cursor.execute(
            "INSERT INTO auth.users (id, email) VALUES (%s, %s) ON CONFLICT (id) DO NOTHING",
            (str(data["merchant_id"]), f"sprint3-merchant-{run_id}@example.com"),
        )
    except Exception as exc:  # noqa: BLE001 - auth.users shape differs between local setups.
        print(f"WARN auth.users seed skipped: {exc}")

    cursor.execute(
        """
        INSERT INTO identity.profiles (user_id, role, full_name, phone_number)
        VALUES (%s, 'CUSTOMER', %s, %s)
        ON CONFLICT (user_id) DO NOTHING
        """,
        (
            str(data["customer_id"]),
            f"Sprint 3 Customer {run_id}",
            f"+91003{run_id[:5]}",
        ),
    )
    cursor.execute(
        """
        INSERT INTO identity.addresses
            (address_id, user_id, label, line1, city, state, pincode, geo_lat, geo_lng, is_default)
        VALUES
            (%s, %s, 'Home', 'Sprint 3 Proof Street', 'Bangalore', 'Karnataka', '560038',
             12.971900, 77.640400, true)
        """,
        (str(data["address_id"]), str(data["customer_id"])),
    )
    cursor.execute(
        """
        INSERT INTO providers.providers (
            provider_id, owner_user_id, provider_type, fulfillment_type, name, description,
            address_line, city, pincode, geo_location, status
        ) VALUES (
            %s, %s, 'PET_STORE'::providers.provider_type, 'DELIVERY'::providers.fulfillment_type,
            %s, 'Sprint 3 live proof store', 'Proof Address', 'Bangalore', '560038',
            ST_SetSRID(ST_MakePoint(77.640400, 12.971900), 4326)::geography,
            'ACTIVE'::providers.provider_status
        )
        """,
        (
            str(data["provider_id"]),
            str(data["merchant_id"]),
            f"Sprint 3 Proof Store {run_id}",
        ),
    )

    offerings = [
        (data["success_offering_id"], "Success Proof Food"),
        (data["failure_offering_id"], "Failure Proof Food"),
    ]
    for offering_id, name in offerings:
        cursor.execute(
            """
            INSERT INTO catalog.offerings
                (offering_id, provider_id, name, description, category, price, status, stock_quantity, sku)
            VALUES
                (%s, %s, %s, 'Sprint 3 live proof item', 'FOOD', 199.00,
                 'ACTIVE'::catalog.offering_status, 10, %s)
            """,
            (
                str(offering_id),
                str(data["provider_id"]),
                name,
                f"S3-{run_id}-{str(offering_id)[:6]}",
            ),
        )

    return data


def create_order(headers: dict[str, str], data: dict[str, uuid.UUID | str], offering_key: str, quantity: int) -> dict[str, Any]:
    status, payload = request_json(
        "POST",
        f"{ORDER_BASE_URL}/api/v1/orders",
        {
            "customerId": str(uuid.uuid4()),
            "providerId": str(data["provider_id"]),
            "deliveryAddressId": str(data["address_id"]),
            "deliveryFee": 0,
            "discountAmount": 0,
            "items": [{"offeringId": str(data[offering_key]), "quantity": quantity}],
        },
        headers,
    )
    if status != 201 or not payload:
        raise AssertionError(f"Order create failed: {status} {payload}")
    return payload


def record_payment(headers: dict[str, str], customer_id: uuid.UUID, order: dict[str, Any], success: bool) -> dict[str, Any]:
    status, payload = request_json(
        "POST",
        f"{PAYMENT_BASE_URL}/api/v1/payments/transactions/result",
        {
            "userId": str(customer_id),
            "referenceId": order["orderId"],
            "transactionType": "ORDER_PAYMENT",
            "amount": order["totalAmount"],
            "gatewayTransactionId": f"sandbox_{'captured' if success else 'failed'}_{order['orderId']}",
            "success": success,
        },
        headers,
    )
    if status != 201 or not payload:
        raise AssertionError(f"Payment result failed: {status} {payload}")
    return payload


def main() -> int:
    connection = psycopg2.connect(DB_DSN)
    connection.autocommit = True
    cursor = connection.cursor()

    data = seed_proof_data(cursor)
    customer_id = data["customer_id"]
    assert isinstance(customer_id, uuid.UUID)
    headers = {"X-User-Id": str(customer_id), "X-User-Role": "CUSTOMER"}

    success_order = create_order(headers, data, "success_offering_id", 1)
    success_payment = record_payment(headers, customer_id, success_order, True)

    failure_order = create_order(headers, data, "failure_offering_id", 2)
    failure_payment = record_payment(headers, customer_id, failure_order, False)
    cancel_status, _ = request_json(
        "PUT",
        f"{ORDER_BASE_URL}/api/v1/orders/{failure_order['orderId']}/status"
        "?status=CANCELLED&note=Sandbox%20payment%20failed%3B%20reserved%20stock%20restored.",
        None,
        headers,
    )

    success_stock = scalar(
        cursor,
        "SELECT stock_quantity FROM catalog.offerings WHERE offering_id=%s",
        (str(data["success_offering_id"]),),
    )
    failure_stock = scalar(
        cursor,
        "SELECT stock_quantity FROM catalog.offerings WHERE offering_id=%s",
        (str(data["failure_offering_id"]),),
    )
    success_tx = scalar(cursor, "SELECT status FROM payments.transactions WHERE reference_id=%s", (success_order["orderId"],))
    failure_tx = scalar(cursor, "SELECT status FROM payments.transactions WHERE reference_id=%s", (failure_order["orderId"],))
    success_order_status = scalar(cursor, "SELECT status FROM orders.orders WHERE order_id=%s", (success_order["orderId"],))
    failure_order_status = scalar(cursor, "SELECT status FROM orders.orders WHERE order_id=%s", (failure_order["orderId"],))
    success_history = scalar(cursor, "SELECT count(*) FROM orders.order_status_history WHERE order_id=%s", (success_order["orderId"],))
    failure_history = scalar(cursor, "SELECT count(*) FROM orders.order_status_history WHERE order_id=%s", (failure_order["orderId"],))

    checks = {
        "success payment captured event": success_payment.get("eventType") == "PaymentCaptured" and bool(success_payment.get("eventId")),
        "success stock decremented": success_stock == 9,
        "success order remains placed": success_order_status == "PLACED",
        "success transaction stored": success_tx == "SUCCESS",
        "failure payment failed event": failure_payment.get("eventType") == "PaymentFailed" and bool(failure_payment.get("eventId")),
        "failure cancel accepted": cancel_status == 200,
        "failure stock restored": failure_stock == 10,
        "failure order cancelled": failure_order_status == "CANCELLED",
        "failure transaction stored": failure_tx == "FAILED",
        "status history written": success_history >= 1 and failure_history >= 2,
    }
    failed = [name for name, ok in checks.items() if not ok]
    if failed:
        print(json.dumps({"ok": False, "failed": failed, "checks": checks}, indent=2, default=str))
        return 1

    print(
        json.dumps(
            {
                "ok": True,
                "runId": data["run_id"],
                "customerId": str(customer_id),
                "providerId": str(data["provider_id"]),
                "successOrderId": success_order["orderId"],
                "successPaymentEvent": success_payment["eventType"],
                "successPaymentEventId": success_payment["eventId"],
                "successStockAfter": success_stock,
                "successOrderStatus": success_order_status,
                "failureOrderId": failure_order["orderId"],
                "failurePaymentEvent": failure_payment["eventType"],
                "failurePaymentEventId": failure_payment["eventId"],
                "failureStockAfter": failure_stock,
                "failureOrderStatus": failure_order_status,
                "successHistoryRows": success_history,
                "failureHistoryRows": failure_history,
            },
            indent=2,
            default=str,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
