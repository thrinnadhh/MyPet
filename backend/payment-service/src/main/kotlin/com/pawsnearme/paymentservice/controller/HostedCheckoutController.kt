package com.pawsnearme.paymentservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val CHECKOUT_TTL_MINUTES = 10L

data class HostedCheckoutRequest(val transactionId: UUID)

data class HostedCheckoutSession(
    val checkoutPath: String,
    val expiresAt: Instant
)

data class HostedCheckoutView(
    val transaction: Transaction,
    val razorpayOrderId: String,
    val keyId: String
)

@Service
class HostedCheckoutService(
    private val transactionRepository: TransactionRepository,
    @Value("\${PAYMENT_CHECKOUT_TOKEN_SECRET:}") private val checkoutTokenSecret: String,
    @Value("\${RAZORPAY_WEBHOOK_SECRET:}") private val webhookSecret: String,
    @Value("\${RAZORPAY_KEY_ID:}") private val razorpayKeyId: String
) {
    private fun signingSecret(): String = checkoutTokenSecret.ifBlank { webhookSecret }
        .ifBlank { throw IllegalStateException("Payment checkout signing secret is not configured") }

    private fun sign(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingSecret().toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun verify(value: String, token: String): Boolean = MessageDigest.isEqual(
        sign(value).toByteArray(StandardCharsets.UTF_8),
        token.lowercase().toByteArray(StandardCharsets.UTF_8)
    )

    @Transactional(readOnly = true)
    fun createSession(transactionId: UUID, requesterId: String?, requesterRole: String?): HostedCheckoutSession {
        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { NoSuchElementException("Payment transaction not found") }
        if (requesterRole != "ADMIN" && requesterId != transaction.userId.toString()) {
            throw PaymentAccessDeniedException("Access denied for checkout session")
        }
        if (transaction.status != "PENDING") {
            throw IllegalStateException("Only pending payments can open checkout")
        }
        if (transaction.gatewayTransactionId.isNullOrBlank()) {
            throw IllegalStateException("Razorpay order has not been initialized")
        }

        val expiresAt = Instant.now().plus(CHECKOUT_TTL_MINUTES, ChronoUnit.MINUTES)
        val signedValue = "$transactionId:${expiresAt.epochSecond}"
        val token = sign(signedValue)
        return HostedCheckoutSession(
            checkoutPath = "/api/v1/payments/checkout/$transactionId?expires=${expiresAt.epochSecond}&token=$token",
            expiresAt = expiresAt
        )
    }

    @Transactional(readOnly = true)
    fun resolve(transactionId: UUID, expires: Long, token: String): HostedCheckoutView {
        if (expires < Instant.now().epochSecond || !verify("$transactionId:$expires", token)) {
            throw PaymentAccessDeniedException("Checkout session is invalid or expired")
        }
        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { NoSuchElementException("Payment transaction not found") }
        if (transaction.status != "PENDING") {
            throw IllegalStateException("Payment is no longer pending")
        }
        val orderId = transaction.gatewayTransactionId
            ?: throw IllegalStateException("Razorpay order has not been initialized")
        val keyId = razorpayKeyId.ifBlank { "rzp_test_mockkey" }
        return HostedCheckoutView(transaction, orderId, keyId)
    }
}

@RestController
@RequestMapping("/api/v1/payments")
class HostedCheckoutController(
    private val hostedCheckoutService: HostedCheckoutService,
    private val objectMapper: ObjectMapper
) {
    @PostMapping("/checkout-sessions")
    fun createSession(
        @RequestBody request: HostedCheckoutRequest,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?
    ): ResponseEntity<HostedCheckoutSession> = ResponseEntity.ok(
        hostedCheckoutService.createSession(request.transactionId, xUserId, xUserRole)
    )

    @GetMapping("/checkout/{transactionId}", produces = [MediaType.TEXT_HTML_VALUE])
    fun checkout(
        @PathVariable transactionId: UUID,
        @RequestParam expires: Long,
        @RequestParam token: String
    ): ResponseEntity<String> {
        val view = hostedCheckoutService.resolve(transactionId, expires, token)
        val transaction = view.transaction
        val callback = "customerapp://payments/result?referenceId=${transaction.referenceId}"
        val keyJson = objectMapper.writeValueAsString(view.keyId)
        val orderJson = objectMapper.writeValueAsString(view.razorpayOrderId)
        val callbackJson = objectMapper.writeValueAsString(callback)
        val amountInPaise = transaction.amount.movePointRight(2).setScale(0).toLong()
        val html = """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1" />
              <title>MyPet secure payment</title>
              <style>
                body{font-family:system-ui,-apple-system,sans-serif;background:#f7f9fc;color:#15213a;margin:0;display:grid;min-height:100vh;place-items:center}
                main{max-width:420px;padding:28px;text-align:center;background:#fff;border-radius:20px;box-shadow:0 14px 40px rgba(20,38,70,.12)}
                button{border:0;border-radius:12px;background:#1565d8;color:#fff;font-weight:700;padding:14px 22px;font-size:16px}
                p{line-height:1.5;color:#536078}
              </style>
              <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
            </head>
            <body>
              <main>
                <h1>MyPet secure checkout</h1>
                <p>Amount: ₹${transaction.amount.toPlainString()}</p>
                <button id="pay">Continue to Razorpay</button>
                <p id="status">Payment success is confirmed only by the MyPet server after Razorpay verification.</p>
              </main>
              <script>
                const callbackUrl = $callbackJson;
                const options = {
                  key: $keyJson,
                  order_id: $orderJson,
                  amount: $amountInPaise,
                  currency: ${objectMapper.writeValueAsString(transaction.currency)},
                  name: 'MyPet',
                  description: 'Pet care marketplace order',
                  handler: function () {
                    document.getElementById('status').textContent = 'Payment received. Confirming securely with MyPet…';
                    window.location.href = callbackUrl + '&checkout=completed';
                  },
                  modal: {
                    ondismiss: function () { window.location.href = callbackUrl + '&checkout=cancelled'; }
                  },
                  theme: { color: '#1565D8' }
                };
                const checkout = new Razorpay(options);
                document.getElementById('pay').addEventListener('click', function () { checkout.open(); });
                window.addEventListener('load', function () { checkout.open(); });
              </script>
            </body>
            </html>
        """.trimIndent()

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .cacheControl(CacheControl.noStore())
            .body(html)
    }
}
