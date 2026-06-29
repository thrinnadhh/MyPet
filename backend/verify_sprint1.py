from verify_sprint_common import check, exists, has_text, finish

print("Sprint 1: Identity, Auth, Provider Onboarding")

check("gateway strips spoofed identity headers", has_text("backend/api-gateway/src/main/kotlin/com/pawsnearme/apigateway/filter/AuthenticationHeaderFilter.kt", 'it.remove("X-User-Id")', 'it.remove("X-User-Role")'))
check("role guard exists", exists("backend/api-gateway/src/main/kotlin/com/pawsnearme/apigateway/filter/RoleGuardGatewayFilterFactory.kt"))
check("provider state machine exists", has_text("backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/model/Enums.kt", "PENDING_APPROVAL", "ACTIVE"))
check(
    "document upload endpoints exist",
    has_text("backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/controller/Controllers.kt", "uploadDocument")
    and has_text("backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/controller/MediaController.kt", "upload-url", "upload-file")
)
check("merchant onboarding uploads document and submits provider", has_text("apps/merchant-captain-app/src/app/onboarding.tsx", "upload-url", "/submit"))

finish("Sprint 1", [
    "Create customer and merchant users against real Supabase config.",
    "Upload provider document, submit provider, approve as ADMIN, and confirm non-admin approval is rejected.",
])
