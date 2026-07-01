from verify_sprint_common import check, exists, has_text, finish

print("Sprint 8: Hardening, Admin, Billing")

check("billing endpoint requires merchant/admin role", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/Controllers.kt", "Access denied: role not authorized"))
check("billing staff identity comes from auth header", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/Controllers.kt", "request.copy(staffId = UUID.fromString(xUserId))"))
check("billing uses atomic stock guard", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/repository/Repositories.kt", "decrementStockIfAvailable", "stockQuantity >= :quantity"))
check("provider commission admin endpoint exists", has_text("backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/controller/Controllers.kt", '@PatchMapping("/{id}/commission")', "Only administrators can update provider commission"))
check("provider commission update publishes event contract", has_text("backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/service/ProviderService.kt", "ProviderCommissionUpdated", "event_id", "actor_id", "provider_id"))
check("gateway protects provider commission endpoint", has_text("backend/api-gateway/src/main/resources/application.yml", "provider-commission-admin", "/api/v1/providers/*/commission", "roles: ADMIN"))
check("support case table migration exists", has_text("backend/catalog-service/src/main/resources/db/migration/V4__support_cases.sql", "orders.support_cases", "action_type", "created_by_user_id"))
check("order admin table grants exist", has_text("backend/catalog-service/src/main/resources/db/migration/V5__grant_order_admin_tables.sql", "orders.support_cases", "order_service_role"))
check("provider identity grants exist", has_text("backend/provider-service/src/main/resources/db/migration/V2__grant_identity_tables.sql", "identity.profiles", "provider_service_role"))
check("support case admin API exists", has_text("backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/controller/AdminController.kt", "/admin/support-cases", "createSupportCase", "resolveSupportCase"))
check("support case event contract exists", has_text("backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderService.kt", "SupportCaseEvent", "eventId", "actorId", "supportCaseId"))
check("Sprint 8 load smoke exists", has_text("scripts/load-smoke-sprint8.py", "discovery", "orders", "appointments", "billing"))
check("Sprint 8 operations runbook exists", exists("docs/operations/sprint-8-hardening-runbook.md"))
check("Sprint 8 Grafana dashboard exists", has_text("docs/operations/grafana-sprint8-dashboard.json", "Request Rate By Service", "Kafka Consumer Lag", "Billing Requests And Failures", "Redis Command Latency"))
check("observability runbook covers request rate errors latency Kafka Redis billing", has_text("docs/operations/sprint-8-hardening-runbook.md", "Request rate", "Error rate", "Latency", "Kafka", "Redis", "Billing"))
check("backup and rollback runbook covers Supabase Kafka Redis rollback order", has_text("docs/operations/sprint-8-hardening-runbook.md", "Supabase/Postgres", "Kafka", "Redis", "Service rollback order"))
check("CI artifact scan exists", has_text("scripts/check-no-generated-artifacts.sh", "Generated artifacts are tracked"))
check("security action list exists", has_text("docs/architecture/architecture-code-quality-actions.md", "Identity And Authorization Boundary", "Billing Add-on Hardening"))

finish("Sprint 8", [
    "Run load smoke against all live services and archive p95 results.",
])
