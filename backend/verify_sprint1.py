from verify_sprint_common import check, exists, has_text, finish

print("Sprint 1: Identity, Auth, Provider Onboarding")

check("gateway strips spoofed identity headers", has_text("backend/api-gateway/src/main/kotlin/com/pawsnearme/apigateway/filter/AuthenticationHeaderFilter.kt", 'it.remove("X-User-Id")', 'it.remove("X-User-Role")'))
check("gateway prefers Supabase app_metadata role for app authorization", has_text("backend/api-gateway/src/main/kotlin/com/pawsnearme/apigateway/filter/AuthenticationHeaderFilter.kt", "app_metadata", "AUTHENTICATED"))
check("gateway accepts Supabase ES256 JWTs", has_text("backend/api-gateway/src/main/kotlin/com/pawsnearme/apigateway/config/SecurityConfig.kt", "SignatureAlgorithm.ES256"))
check("role guard exists", exists("backend/api-gateway/src/main/kotlin/com/pawsnearme/apigateway/filter/RoleGuardGatewayFilterFactory.kt"))
check("provider state machine exists", has_text("backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/model/Enums.kt", "PENDING_APPROVAL", "ACTIVE"))
check("authenticated default address API exists", has_text("backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/controller/Controllers.kt", "/default", "X-User-Id", "No default delivery address found"))
check("provider service can write identity address rows", has_text("backend/provider-service/src/main/resources/db/migration/V2__grant_identity_tables.sql", "identity.addresses", "provider_service_role"))
check(
    "document upload endpoints exist",
    has_text("backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/controller/Controllers.kt", "uploadDocument")
    and has_text("backend/provider-service/src/main/kotlin/com/pawsnearme/providerservice/controller/MediaController.kt", "upload-url", "upload-file")
)
check("merchant onboarding uploads document and submits provider", has_text("apps/merchant-captain-app/src/app/onboarding.tsx", "upload-url", "/submit"))

finish("Sprint 1", [
    "Run customer and merchant mobile signup/onboarding screens against real Supabase config with demo mode disabled.",
    "Replace the local auth.users mirror used in live proof with a production auth sync strategy.",
])
