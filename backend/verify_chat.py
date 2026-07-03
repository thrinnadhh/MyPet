#!/usr/bin/env python3
"""Static verification for merchant-customer chat service."""

from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
CHAT_ROOT = ROOT / "backend" / "chat-service"

REQUIRED = [
    CHAT_ROOT / "build.gradle.kts",
    CHAT_ROOT / "src/main/resources/db/migration/V1__init_chat.sql",
    CHAT_ROOT / "src/main/kotlin/com/pawsnearme/chatservice/service/ChatService.kt",
    CHAT_ROOT / "src/main/kotlin/com/pawsnearme/chatservice/controller/ChatController.kt",
    ROOT / "backend" / "notification-service" / "src/main/kotlin/com/pawsnearme/notificationservice/service/ChatEventListener.kt",
    ROOT / "apps" / "customer-app" / "src/app/chat.tsx",
    ROOT / "apps" / "merchant-captain-app" / "src/app/chat.tsx",
]


def main() -> int:
    missing = [str(path.relative_to(ROOT)) for path in REQUIRED if not path.exists()]
    if missing:
        print("FAILED: missing chat artifacts:")
        for item in missing:
            print(f"  - {item}")
        return 1

    gateway = (ROOT / "backend" / "api-gateway" / "src/main/resources/application.yml").read_text()
    if "chat-service" not in gateway:
        print("FAILED: api-gateway is missing chat-service route")
        return 1

    sql = (CHAT_ROOT / "src/main/resources/db/migration/V1__init_chat.sql").read_text()
    for token in ("customer_phone_visible", "doctor_phone_visible", "chat_message_type"):
        if token not in sql:
            print(f"FAILED: migration missing {token}")
            return 1

    print("PASSED: chat service artifacts present")
    return 0


if __name__ == "__main__":
    sys.exit(main())
