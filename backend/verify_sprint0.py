from verify_sprint_common import check, exists, has_text, finish

print("Sprint 0: Foundations")

check("docker-compose infra exists", exists("infra/docker-compose.yml"))
check("migration runner exists", exists("infra/run_migrations.py"))
check("CI workflow exists", exists(".github/workflows/ci.yml"))
check("CI runs backend from backend directory", has_text(".github/workflows/ci.yml", "working-directory: backend", "./gradlew test"))
check("artifact scan is wired", has_text("scripts/verify-all.sh", "check-no-generated-artifacts.sh"))
check("generated artifact ignore rules exist", has_text(".gitignore", "**/bin/", "**/build/", "**/.expo/"))

finish("Sprint 0", [
    "Run local infra and migrations against a clean database.",
    "Create Kafka topics and verify Redis connectivity.",
])
