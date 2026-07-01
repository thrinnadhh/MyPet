from verify_sprint_common import check, exists, has_text, finish

print("Sprint 5: Appointments And Slot Locking")

check("appointment verifier exists", exists("backend/verify_appointments.py"))
check("appointment hold endpoint exists", has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/controller/AppointmentController.kt", "/hold"))
check("appointment confirm endpoint exists", has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/controller/AppointmentController.kt", "/confirm"))
check("customer Vet/Groom hold real slots without hidden production demo success", has_text("apps/customer-app/src/app/vet.tsx", "Booking Unavailable") and has_text("apps/customer-app/src/app/groom.tsx", "Booking Unavailable"))

finish("Sprint 5", [
    "Run concurrent booking for the same slot and confirm exactly one winner.",
    "Confirm expired holds release the slot.",
])
