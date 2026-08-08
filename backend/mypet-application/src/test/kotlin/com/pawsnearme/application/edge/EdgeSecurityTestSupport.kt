package com.pawsnearme.application.edge

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

@RestController
class EdgeSecurityTestController {

    @GetMapping("/api/v1/discovery/test")
    fun publicRoute(request: HttpServletRequest): Map<String, String?> = requestIdentity(request)

    @GetMapping("/api/v1/orders/test")
    fun authenticatedRoute(request: HttpServletRequest): Map<String, String?> = requestIdentity(request)

    @PostMapping("/api/v1/providers")
    fun merchantRoute(request: HttpServletRequest): Map<String, String?> = requestIdentity(request)

    @PostMapping("/api/v1/orders/idempotency-test")
    fun idempotentRoute(@RequestBody body: String): ResponseEntity<Map<String, Any>> {
        val execution = idempotentExecutions.incrementAndGet()
        return ResponseEntity.ok(mapOf("body" to body, "execution" to execution))
    }

    private fun requestIdentity(request: HttpServletRequest): Map<String, String?> = mapOf(
        "userId" to request.getHeader("X-User-Id"),
        "role" to request.getHeader("X-User-Role"),
        "email" to request.getHeader("X-User-Email"),
        "fullName" to request.getHeader("X-User-Full-Name"),
        "phone" to request.getHeader("X-User-Phone"),
        "requestId" to request.getHeader(EdgeRequestInfrastructureFilter.REQUEST_ID_HEADER),
        "internalSecret" to request.getHeader("X-Internal-Gateway-Secret")
    )

    companion object {
        val idempotentExecutions = AtomicInteger()
    }
}

fun unsignedJwt(
    subject: String = "user-123",
    role: String = "CUSTOMER",
    email: String = "user@example.com"
): String {
    val header = """{"alg":"none","typ":"JWT"}"""
    val payload = """{
        "sub":"$subject",
        "exp":${Instant.now().plusSeconds(3_600).epochSecond},
        "email":"$email",
        "phone":"9999999999",
        "app_metadata":{"role":"$role"},
        "user_metadata":{"full_name":"Test User","phone":"8111111111"}
    }""".trimIndent()

    return "${base64Url(header)}.${base64Url(payload)}."
}

private fun base64Url(value: String): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(StandardCharsets.UTF_8))