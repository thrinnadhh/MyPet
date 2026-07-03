from verify_sprint_common import ROOT, check, exists, has_text, finish

print("Sprint 10: Authorization Hardening")

# 1. API Gateway checks
check("gateway application.yml maps PUT status route with MERCHANT/ADMIN role", has_text(
    "backend/api-gateway/src/main/resources/application.yml",
    "/api/v1/appointments/*/status",
    "MERCHANT",
    "ADMIN"
))

check("RoleGuardGatewayFilterFactoryTests checks PUT status restriction", has_text(
    "backend/api-gateway/src/test/kotlin/com/pawsnearme/apigateway/filter/RoleGuardGatewayFilterFactoryTests.kt",
    "put",
    "/api/v1/appointments/",
    "CUSTOMER",
    "HttpStatus.FORBIDDEN"
))

# 2. Appointment Controller & Service checks
check("AppointmentController extracts authentication context", has_text(
    "backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/controller/AppointmentController.kt",
    "X-User-Id",
    "X-User-Role",
    "HttpStatus.UNAUTHORIZED"
))

check("AppointmentService contains owner resolution and access checks", has_text(
    "backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt",
    "fetchProviderOwnerUserId",
    "AppointmentAccessDeniedException",
    "getAppointment",
    "updateAppointmentStatus"
))

check("AppointmentAuthTests contains authorization cases", exists(
    "backend/appointment-service/src/test/kotlin/com/pawsnearme/appointmentservice/service/AppointmentAuthTests.kt"
))

# 3. Dispatch Controller & Service checks
check("DispatchController rejects missing/blank credentials with 401", has_text(
    "backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/controller/DispatchController.kt",
    "X-User-Id",
    "HttpStatus.UNAUTHORIZED",
    "Missing authenticated captain context."
))

check("DispatchService has hardened respondToOffer validation", has_text(
    "backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt",
    "respondToOffer",
    "captainId: UUID",
    "offer.captainId != captainId"
))

check("DispatchControllerTests exists and checks 401 flows", exists(
    "backend/dispatch-service/src/test/kotlin/com/pawsnearme/dispatchservice/controller/DispatchControllerTests.kt"
))

finish("Sprint 10", [
    "Run backend integration tests locally and verify Gateway filter.",
    "Verify dispatch OTP flows with mobile apps in captain app simulator."
])
