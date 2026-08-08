#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(text: str, pattern: str, message: str) -> None:
    if re.search(pattern, text, re.MULTILINE | re.DOTALL) is None:
        raise AssertionError(message)


def forbid(text: str, pattern: str, message: str) -> None:
    # Secret exposure rules are line-oriented. Do not use DOTALL here: a public
    # Expo variable followed by a server-only provider key many lines later is
    # not itself a leak.
    if re.search(pattern, text, re.MULTILINE) is not None:
        raise AssertionError(message)


def main() -> int:
    sms = read("supabase/functions/send-sms-hook/index.ts")
    email_provider = read(
        "backend/notification-service/src/main/kotlin/com/pawsnearme/notificationservice/service/TransactionalEmailProviders.kt"
    )
    email_router = read(
        "backend/notification-service/src/main/kotlin/com/pawsnearme/notificationservice/service/TransactionalEmailService.kt"
    )
    notification_config = read("backend/notification-service/src/main/resources/application.yml")
    env_example = read(".env.example")

    # SMS hook: trust boundary, provider endpoint, bounded latency and OTP contract.
    require(sms, r"new Webhook\(hookSecret\)", "Send SMS Hook must verify the Supabase webhook signature")
    require(sms, r"webhook\.verify\(", "Send SMS Hook must reject unsigned/tampered requests")
    require(sms, r"SEND_SMS_HOOK_SECRET", "Send SMS Hook secret must remain server-side configuration")
    require(sms, r"MSG91_AUTH_KEY", "MSG91 SMS auth key must come from Edge Function secrets")
    require(sms, r"MSG91_SMS_TEMPLATE_ID", "MSG91 SMS template must be configured externally")
    require(sms, r"https://control\.msg91\.com/api/v5/flow", "MSG91 SMS must use the configured Flow API")
    require(sms, r"\^\\d\{6\}\$", "Supabase SMS hook must enforce the six-digit OTP contract")
    require(sms, r"AbortSignal\.timeout\(4_000\)", "MSG91 request must have a bounded timeout")
    require(sms, r"req\.method !== \"POST\"", "SMS hook must reject unsupported HTTP methods")

    # Transactional email provider boundary.
    require(email_provider, r"https://control\.msg91\.com", "MSG91 email provider endpoint missing")
    require(email_provider, r"/api/v5/email/send", "MSG91 email send path missing")
    require(email_provider, r"https://api\.brevo\.com", "Brevo provider endpoint missing")
    require(email_provider, r"/v3/smtp/email", "Brevo transactional send path missing")
    require(email_provider, r"Idempotency-Key", "Brevo send must carry application idempotency key")
    require(email_provider, r"ambiguous = true", "Network-ambiguous email outcomes must be represented explicitly")
    require(email_provider, r"status\.value\(\) == 429", "Provider rate limits must be classified")
    require(email_provider, r"status\.is5xxServerError", "Provider 5xx failures must be classified")

    # Router: quota failover, duplicate protection, retry and distributed scheduler locking.
    require(email_router, r"findByIdempotencyKey", "Email router must deduplicate logical deliveries")
    require(email_router, r"msg91RemainingThisMonth", "MSG91 monthly quota routing is missing")
    require(email_router, r"safeToFailover", "Definitive primary failures must support backup routing")
    require(email_router, r"if \(result\.ambiguous\)", "Ambiguous primary outcomes must block immediate failover")
    require(email_router, r'background|UNKNOWN|status = "UNKNOWN"', "Ambiguous email outcome state is missing")
    require(email_router, r"@Scheduled", "Transactional email retry scheduler is missing")
    require(email_router, r"@SchedulerLock", "Transactional email retry scheduler must be distributed-lock protected")

    # Sending must remain opt-in until real provider identities/templates are supplied.
    require(
        notification_config,
        r"enabled:\s*\$\{TRANSACTIONAL_EMAIL_ENABLED:false\}",
        "Transactional email must default to disabled",
    )
    require(env_example, r"TRANSACTIONAL_EMAIL_ENABLED=false", "Example environment must keep email disabled")
    require(env_example, r"MSG91_EMAIL_TEMPLATE_ORDER_PLACED", "MSG91 order template contract missing")
    require(env_example, r"BREVO_EMAIL_TEMPLATE_ORDER_PLACED", "Brevo order template contract missing")

    # Provider secrets must never be exposed through Expo public variables.
    forbid(env_example, r"^EXPO_PUBLIC_[^\r\n]*MSG91", "MSG91 secrets/config must never be exposed to the mobile bundle")
    forbid(env_example, r"^EXPO_PUBLIC_[^\r\n]*BREVO", "Brevo secrets/config must never be exposed to the mobile bundle")
    forbid(env_example, r"^EXPO_PUBLIC_[^\r\n]*SERVICE_ROLE", "Supabase service-role key must never be exposed to Expo")

    print("Production communications contract passed: SMS hook trust, email failover/idempotency, retries, and secret boundaries are enforced.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
