from verify_sprint_common import check, has_text, finish

print("Sprint 8: Hardening, Admin, Billing")

check("billing endpoint requires merchant/admin role", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/Controllers.kt", "Access denied: role not authorized"))
check("billing staff identity comes from auth header", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/Controllers.kt", "request.copy(staffId = UUID.fromString(xUserId))"))
check("billing uses atomic stock guard", has_text("backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/repository/Repositories.kt", "decrementStockIfAvailable", "stockQuantity >= :quantity"))
check("CI artifact scan exists", has_text("scripts/check-no-generated-artifacts.sh", "Generated artifacts are tracked"))
check("security action list exists", has_text("docs/architecture/architecture-code-quality-actions.md", "Identity And Authorization Boundary", "Billing Add-on Hardening"))

finish("Sprint 8", [
    "Build minimal Super Admin web/API for approvals, disputes, commission config, and support actions.",
    "Run load tests and publish observability dashboards/runbook.",
])
