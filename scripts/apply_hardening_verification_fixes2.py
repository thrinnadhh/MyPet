#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    target = ROOT / path
    target.write_text(text.rstrip() + "\n", encoding="utf-8")
    print(f"fixed {path}")


# TestingAuthenticationToken's two-argument constructor is unauthenticated.
gateway_test = "backend/api-gateway/src/test/kotlin/com/pawsnearme/apigateway/filter/AuthenticationHeaderFilterTests.kt"
text = read(gateway_test)
text = text.replace(
    "TestingAuthenticationToken(jwt, null)",
    "TestingAuthenticationToken(jwt, null, emptyList())",
)
write(gateway_test, text)

# Make the MVC slice independent of application-profile defaults.
catalog_test = "backend/catalog-service/src/test/kotlin/com/pawsnearme/catalogservice/controller/CatalogAuthorizationWebMvcTest.kt"
text = read(catalog_test)
if "org.springframework.test.context.TestPropertySource" not in text:
    text = text.replace(
        "import org.springframework.test.web.servlet.MockMvc\n",
        "import org.springframework.test.context.TestPropertySource\n"
        "import org.springframework.test.web.servlet.MockMvc\n",
    )
if "@TestPropertySource(properties = [\"internal.api.secret=dev-internal-secret\"])" not in text:
    text = text.replace(
        "@WebMvcTest(controllers = [CatalogController::class, InternalCatalogController::class])\n",
        "@WebMvcTest(controllers = [CatalogController::class, InternalCatalogController::class])\n"
        "@TestPropertySource(properties = [\"internal.api.secret=dev-internal-secret\"])\n",
    )
write(catalog_test, text)

# Payment idempotency is mandatory in production, so unit tests provide an explicit mock.
payment_test = "backend/payment-service/src/test/kotlin/com/pawsnearme/paymentservice/service/PaymentServiceTests.kt"
text = read(payment_test)
if "com.pawsnearme.common.idempotency.IdempotencyService" not in text:
    text = text.replace(
        "import com.fasterxml.jackson.databind.ObjectMapper\n",
        "import com.fasterxml.jackson.databind.ObjectMapper\n"
        "import com.pawsnearme.common.idempotency.IdempotencyService\n",
    )
if "private lateinit var idempotencyService: IdempotencyService" not in text:
    text = text.replace(
        "    private lateinit var service: PaymentService\n",
        "    private lateinit var service: PaymentService\n"
        "    private lateinit var idempotencyService: IdempotencyService\n",
    )
if "idempotencyService = mock()" not in text:
    text = text.replace(
        "        codConfigRepository = mock()\n",
        "        codConfigRepository = mock()\n"
        "        idempotencyService = mock()\n"
        "        whenever(idempotencyService.checkAndRecord(any())).thenReturn(true)\n",
    )
text = text.replace(
    "            objectMapper = ObjectMapper()\n",
    "            objectMapper = ObjectMapper(),\n"
    "            idempotencyService = idempotencyService\n",
)
text = text.replace(
    "        objectMapper = ObjectMapper()\n",
    "        objectMapper = ObjectMapper(),\n"
    "        idempotencyService = idempotencyService\n",
)
write(payment_test, text)

print("Second verification-fix pass applied.")
