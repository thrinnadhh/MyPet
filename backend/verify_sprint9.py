from verify_sprint_common import check, exists, has_text, finish

print("Sprint 9: Legal And Launch")

check("launch sprint checklist exists", exists("docs/sprints/sprint-9-legal-launch.md"))
check("legal content requirements documented", has_text("docs/sprints/sprint-9-legal-launch.md", "Terms of Service", "Privacy Policy", "Refund/Cancellation Policy"))
check("production secrets checklist documented", has_text("docs/sprints/sprint-9-legal-launch.md", "Production Razorpay", "Supabase", "Kafka", "Redis"))
check("rollback requirement documented", has_text("docs/sprints/sprint-9-legal-launch.md", "Rollback plan"))

finish("Sprint 9", [
    "Publish legal pages and link them in both apps.",
    "Prepare store listings, data safety disclosures, production secrets, and rollback drill evidence.",
])
