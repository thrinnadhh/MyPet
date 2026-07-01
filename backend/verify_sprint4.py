from verify_sprint_common import check, has_text, finish

print("Sprint 4: Dispatch And Captain Loop")

check("captain location/status service exists", has_text("backend/captain-service/src/main/kotlin/com/pawsnearme/captainservice/service/CaptainService.kt", "updateLocation", "online"))
check("dispatch offer response service exists", has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt", "respondToOffer"))
check("merchant captain delivery screen uses dispatch offers", has_text("apps/merchant-captain-app/src/app/delivery.tsx", "/api/v1/dispatch/offers"))
check("delivery status updates call order service", has_text("apps/merchant-captain-app/src/app/delivery.tsx", "PICKED_UP", "DELIVERED"))

finish("Sprint 4", [
    "Simulate ready-for-pickup order and multiple captains.",
    "Verify offer expiry/retry, accept, pickup, delivery, and earnings.",
])
