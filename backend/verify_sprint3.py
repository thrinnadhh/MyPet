from verify_sprint_common import check, has_text, finish

print("Sprint 3: Orders And Payments")

check("order placed event has event id", has_text("backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderService.kt", "OrderPlacedEvent", "eventId"))
check("order cancelled event has event id", has_text("backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderService.kt", "OrderCancelled", "eventId"))
check("payment result endpoint exists", has_text("backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/controller/PaymentController.kt", "/transactions/result"))
check("payment captured/failed events exist", has_text("backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/service/PaymentService.kt", "PaymentCaptured", "PaymentFailed", "eventId"))
check("customer checkout calls order and payment APIs", has_text("apps/customer-app/src/app/shop.tsx", "/api/v1/orders", "/api/v1/payments/transactions/result", "loadDefaultAddress"))
check("production checkout requires default address", has_text("apps/customer-app/src/app/shop.tsx", "/api/v1/addresses/default", "Add a default delivery address before checkout."))
check("catalog stock decrement is atomic", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/service/CatalogService.kt", "decrementStockIfAvailable", "updatedRows != 1"))
check("catalog stock restore endpoint exists", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/Controllers.kt", "/restore-stock", "restoreStock"))
check("order cancellation restores reserved stock", has_text("backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderService.kt", "restoreOrderCatalogStock", "OrderStatus.CANCELLED", "OrderStatus.REJECTED"))
check("checkout payment failure cancels order", has_text("apps/customer-app/src/app/shop.tsx", "cancelOrderAfterPaymentFailure", "Payment failed", "stock restored"))
check("stock rollback tests exist", has_text("backend/catalog-service/src/test/kotlin/com/pawsnearme/catalogservice/service/CatalogServiceTests.kt", "atomic guard prevents oversell", "restoreStock - increments tracked stock"))
check("customer app UI proof captured", has_text("docs/sprints/sprint-3-orders-payments.md", "Customer app UI proof captured", "EXPO_PUBLIC_ALLOW_DEMO_MODE=false", "sandbox_captured_", "sandbox_failed_"))

finish("Sprint 3")

print("\nLaunch follow-up:")
print("  - Before production launch, replace local sandbox-equivalent proof with Razorpay sandbox capture/failure evidence.")
