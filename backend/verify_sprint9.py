from verify_sprint_common import ROOT, check, exists, has_text, finish

print("Sprint 9: Legal And Launch")

def text(path: str) -> str:
    file_path = ROOT / path
    if not file_path.exists():
        return ""
    return file_path.read_text(errors="ignore")


def has_any(path: str, *needles: str) -> bool:
    content = text(path)
    return any(needle in content for needle in needles)


def has_text_ci(path: str, *needles: str) -> bool:
    content = text(path).lower()
    return all(needle.lower() in content for needle in needles)


LEGAL_DOCS = [
    "docs/legal/terms-of-service.md",
    "docs/legal/privacy-policy.md",
    "docs/legal/refund-cancellation-policy.md",
    "docs/legal/support-dispute-workflow.md",
]

LAUNCH_DOCS = [
    "docs/launch/app-store-listing.md",
    "docs/launch/play-store-listing.md",
    "docs/launch/data-safety-privacy-disclosures.md",
    "docs/launch/production-secrets-checklist.md",
    "docs/launch/soft-launch-plan.md",
    "docs/launch/rollback-drill.md",
    "docs/launch/gst-invoice-proof.md",
]

check("launch sprint checklist exists", exists("docs/sprints/sprint-9-legal-launch.md"))
check("legal docs exist", all(exists(path) for path in LEGAL_DOCS))
check("legal docs cover required policies", all(has_text(path, marker) for path, marker in [
    ("docs/legal/terms-of-service.md", "Terms of Service"),
    ("docs/legal/privacy-policy.md", "Privacy Policy"),
    ("docs/legal/refund-cancellation-policy.md", "Refund"),
    ("docs/legal/support-dispute-workflow.md", "Support"),
]))
check("customer app legal screen exists", exists("apps/customer-app/src/app/legal.tsx"))
check("merchant app legal screen exists", exists("apps/merchant-captain-app/src/app/legal.tsx"))
check("customer app links legal route", has_text("apps/customer-app/src/app/profile.tsx", "router.push('/legal'"))
check("merchant app links legal route", has_text("apps/merchant-captain-app/src/app/index.tsx", "route: '/legal'"))
check("store and launch docs exist", all(exists(path) for path in LAUNCH_DOCS))
check("app store listing copy ready", has_text("docs/launch/app-store-listing.md", "App Store", "Screenshots"))
check("play store listing copy ready", has_text("docs/launch/play-store-listing.md", "Play Store", "Screenshots"))
check("data safety disclosures ready", has_text_ci("docs/launch/data-safety-privacy-disclosures.md", "Data Safety", "Location", "Payment"))
check("production secrets checklist documented", has_text("docs/launch/production-secrets-checklist.md", "Production Razorpay", "Supabase", "Kafka", "Redis", "FCM", "APNs"))
check("soft launch locality documented", has_any("docs/launch/soft-launch-plan.md", "locality", "Indiranagar", "Hyderabad", "Bangalore"))
check("rollback drill documented", has_text_ci("docs/launch/rollback-drill.md", "Rollback Drill", "Trigger", "Service rollback order"))
check("GST invoice proof document exists", has_text_ci("docs/launch/gst-invoice-proof.md", "orders", "appointments", "in-store bills"))
check("appointment invoice migration exists", exists("backend/appointment-service/src/main/resources/db/migration/V2__appointment_invoices.sql"))
check("appointment invoice model exists", has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/model/Models.kt", "AppointmentInvoice", "appointment_invoices"))
check("appointment invoice endpoint exists", has_text("backend/appointment-service/src/main/kotlin/com/pawsnearme/appointmentservice/controller/AppointmentController.kt", "/{id}/invoice"))
check("appointment invoice tests exist", has_text("backend/appointment-service/src/test/kotlin/com/pawsnearme/appointmentservice/service/AppointmentServiceTests.kt", "generates appointment GST invoice", "does not duplicate appointment invoice"))
check("sprint 9 acceptance doc references artifacts", has_text("docs/sprints/sprint-9-legal-launch.md", "docs/legal", "docs/launch", "Appointment invoices"))

finish("Sprint 9", [
    "Capture real production secret configuration outside git.",
    "Submit release candidates to store internal testing.",
    "Run rollback drill against the selected staging/production-like environment.",
])
