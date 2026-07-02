from verify_sprint_common import check, exists, has_text, finish

print("Sprint 5: Appointments And Slot Locking")

check("appointment verifier exists", exists("backend/verify_appointments.py"))
check("appointment hold endpoint exists", has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/controller/AppointmentController.kt", "/hold"))
check("appointment confirm endpoint exists", has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/controller/AppointmentController.kt", "/confirm"))
check("appointment events include stable contract fields", has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt", "event_id") and has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt", "occurred_at") and has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt", "actor_id"))
check("redis slot hold key is separate from write lock", has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt", "hold:slots") and has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt", "Duration.ofSeconds(holdDurationSeconds)"))
check("database uniqueness prevents active double booking", has_text("backend/appointment-service/src/main/resources/db/migration/V1__init_appointments.sql", "ux_appointments_active_slot") and has_text("backend/appointment-service/src/main/resources/db/migration/V1__init_appointments.sql", "SLOT_HELD', 'CONFIRMED"))
check("customer Vet/Groom use shared live booking service", has_text("apps/customer-app/src/services/appointment-booking.ts", "holdAppointmentSlot") and has_text("apps/customer-app/src/app/vet.tsx", "fetchAvailableAppointmentSlots") and has_text("apps/customer-app/src/app/groom.tsx", "fetchAvailableAppointmentSlots"))
check("customer Vet/Groom production failures remain visible", has_text("apps/customer-app/src/app/vet.tsx", "Booking Unavailable") and has_text("apps/customer-app/src/app/groom.tsx", "Booking Unavailable"))
check("dispatch and captain code paths are not imported by appointments", not has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/service/AppointmentService.kt", "dispatch") and not has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/controller/AppointmentController.kt", "captain"))
check("appointment tests cover hold ttl and event contracts", has_text("backend/appointment-service/src/test/kotlin/com/pawsnearme/appointmentservice/service/AppointmentServiceTests.kt", "stores redis hold key with ttl") and has_text("backend/appointment-service/src/test/kotlin/com/pawsnearme/appointmentservice/service/AppointmentServiceTests.kt", "publishes event contracts"))

finish("Sprint 5")
