from verify_sprint_common import check, exists, has_text, finish

print("Sprint 4: Dispatch And Captain Loop")

check("captain location/status service exists", has_text("backend/captain-service/src/main/kotlin/com/pawsnearme/captainservice/service/CaptainService.kt", "updateLocation", "online"))
check("dispatch offer response service exists", has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt", "respondToOffer"))
check("dispatch validates authenticated offer ownership", has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt", "Offer does not belong to authenticated captain"))
check("dispatch pickup and delivery endpoints exist", has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/controller/DispatchController.kt", "/pickup", "/deliver", "DeliveryProofRequest"))
check("dispatch pickup and delivery update order status", has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt", "markPickedUp", "PICKED_UP", "markDelivered", "DELIVERED"))
check("dispatch completion marks job completed", has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt", "JobStatus.COMPLETED", "resolvedAt"))
check("max attempt failure is visible without silent cancellation", has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt", "DispatchJobFailed", "MAX_ATTEMPTS_EXHAUSTED") and not has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt", "status=CANCELLED&note=No Captains available"))
check("dispatch events carry event_id", has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/service/DispatchService.kt", "\"event_id\"", "\"occurred_at\"", "\"order_id\""))
check("ops can list failed dispatch jobs", has_text("backend/dispatch-service/src/main/kotlin/com/pawsnearme/dispatchservice/controller/DispatchController.kt", "@GetMapping(\"/jobs\")", "status: JobStatus?"))
check("captain earnings are recorded on delivered order event", has_text("backend/captain-service/src/main/kotlin/com/pawsnearme/captainservice/service/CaptainService.kt", "toStatus == \"DELIVERED\"", "CaptainEarning", "totalDeliveries"))
check("merchant captain delivery screen uses dispatch offers", has_text("apps/merchant-captain-app/src/app/delivery.tsx", "/api/v1/dispatch/offers"))
check("delivery screen drives pickup and delivery through dispatch APIs", has_text("apps/merchant-captain-app/src/app/delivery.tsx", "/pickup", "/deliver", "proofCode"))
check("delivery screen sends auth token to backend", has_text("apps/merchant-captain-app/src/app/delivery.tsx", "Authorization", "Bearer"))
check("dispatch service tests cover sprint lifecycle", has_text("backend/dispatch-service/src/test/kotlin/com/pawsnearme/dispatchservice/service/DispatchServiceTests.kt", "markPickedUp", "markDelivered", "DispatchJobFailed"))
check("sprint 4 live verifier exists", exists("backend/verify_sprint4_live.py"))

finish("Sprint 4")
