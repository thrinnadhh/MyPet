from verify_sprint_common import check, has_text, finish

print("Sprint 3: Orders And Payments")

check("order placed event has event id", has_text("backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderService.kt", "OrderPlacedEvent", "eventId"))
check("order cancelled event has event id", has_text("backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderService.kt", "OrderCancelled", "eventId"))
check("payment result endpoint exists", has_text("backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/controller/PaymentController.kt", "/transactions/result"))
check("payment captured/failed events exist", has_text("backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/service/PaymentService.kt", "PaymentCaptured", "PaymentFailed", "eventId"))
check("customer checkout calls order and payment APIs", has_text("apps/customer-app/src/app/shop.tsx", "/api/v1/orders", "/api/v1/payments/transactions/result", "loadDefaultAddress"))
check("production checkout requires default address", has_text("apps/customer-app/src/app/shop.tsx", "/api/v1/addresses/default", "Add a default delivery address before checkout."))

finish("Sprint 3", [
    "Run Razorpay sandbox or documented sandbox equivalent through customer checkout.",
    "Verify payment failure does not permanently decrement stock.",
])
