package com.pawsnearme.paymentservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.OrderRefRepository
import com.pawsnearme.paymentservice.repository.TransactionRepository
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestOperations
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

data class CreateCashfreeOrderRequest(
    val userId: UUID,
    val referenceId: UUID,
    @field:DecimalMin("0.01")
    @field:DecimalMax("10000000.00")
    val amount: BigDecimal,
    @field:Pattern(regexp = "ORDER_PAYMENT")
    val transactionType: String,
    @field:Pattern(regexp = "(?:\\+?91)?[6-9][0-9]{9}")
    val customerPhone: String,
    @field:Email
    @field:Size(max = 254)
    val customerEmail: String? = null,
    @field:Size(max = 120)
    val customerName: String? = null,
)

data class CashfreeOrderResponse(
    val orderId: String,
    val paymentSessionId: String,
    val amount: BigDecimal,
    val currency: String,
    val transactionId: UUID,
    val environment: String,
)

@Service
class CashfreeGatewayService(
    private val transactionRepository: TransactionRepository,
    private val orderRefRepository: OrderRefRepository,
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper,
    @Value("\${CASHFREE_CLIENT_ID:}") private val clientId: String = "",
    @Value("\${CASHFREE_CLIENT_SECRET:}") private val clientSecret: String = "",
    @Value("\${CASHFREE_WEBHOOK_SECRET:}") private val configuredWebhookSecret: String = "",
    @Value("\${CASHFREE_API_VERSION:2025-01-01}") private val apiVersion: String = "2025-01-01",
    @Value("\${CASHFREE_SANDBOX_MODE:false}") private val sandboxMode: Boolean = false,
    private val restTemplate: RestOperations = RestTemplate(),
) {
    private val logger = LoggerFactory.getLogger(CashfreeGatewayService::class.java)

    private val baseUrl: String
        get() = if (sandboxMode) "https://sandbox.cashfree.com/pg" else "https://api.cashfree.com/pg"

    private val environment: String
        get() = if (sandboxMode) "SANDBOX" else "PRODUCTION"

    private fun credentialsConfigured(): Boolean = clientId.isNotBlank() && clientSecret.isNotBlank()

    private fun webhookSecret(): String = configuredWebhookSecret.ifBlank { clientSecret }
        .ifBlank { throw IllegalStateException("Cashfree webhook/client secret is not configured") }

    private fun authenticatedHeaders(requestId: UUID = UUID.randomUUID()): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        accept = listOf(MediaType.APPLICATION_JSON)
        set("x-api-version", apiVersion)
        set("x-client-id", clientId)
        set("x-client-secret", clientSecret)
        set("x-request-id", requestId.toString())
        set("x-idempotency-key", requestId.toString())
    }

    private fun normalizePhone(value: String): String {
        val digits = value.filter(Char::isDigit)
        return when {
            digits.length == 12 && digits.startsWith("91") -> digits.takeLast(10)
            digits.length == 10 -> digits
            else -> throw IllegalArgumentException("A valid Indian customer mobile number is required for Cashfree")
        }
    }

    private fun validateReference(request: CreateCashfreeOrderRequest) {
        require(request.transactionType == "ORDER_PAYMENT") {
            "Cashfree order initiation currently supports order payments only"
        }
        require(request.amount > BigDecimal.ZERO) { "Payment amount must be greater than zero" }

        val order = orderRefRepository.findById(request.referenceId)
            .orElseThrow { IllegalArgumentException("Order not found for payment reference") }
        if (order.customerId != request.userId) {
            throw IllegalArgumentException("Payment reference does not belong to this user")
        }
        if (order.status != "PLACED") {
            throw IllegalStateException("Order is not awaiting online payment")
        }
        if (order.totalAmount.compareTo(request.amount) != 0) {
            throw IllegalArgumentException("Payment amount does not match the server-authoritative order total")
        }
    }

    @Transactional
    fun createOrder(request: CreateCashfreeOrderRequest): CashfreeOrderResponse {
        validateReference(request)

        val completed = transactionRepository.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
            request.referenceId,
            listOf("SUCCESS", "REFUNDED", "REFUND_PENDING"),
        )
        if (completed != null) {
            throw IllegalStateException("Payment is already completed for reference ID ${request.referenceId}")
        }

        val pending = transactionRepository.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
            request.referenceId,
            listOf("PENDING"),
        )
        if (pending != null && pending.gateway == "CASHFREE" && !pending.gatewayTransactionId.isNullOrBlank()) {
            return responseForExisting(pending)
        }
        if (pending != null) {
            pending.status = "FAILED"
            transactionRepository.save(pending)
        }

        val transaction = transactionRepository.save(
            Transaction(
                userId = request.userId,
                transactionType = request.transactionType,
                referenceId = request.referenceId,
                amount = request.amount.setScale(2, RoundingMode.HALF_UP),
                status = "PENDING",
                gateway = "CASHFREE",
            ),
        )
        val transactionId = transaction.transactionId
            ?: throw IllegalStateException("Payment transaction ID was not generated")
        val cashfreeOrderId = "mypet_${transactionId.toString().replace("-", "")}"

        val sessionId = if (sandboxMode && !credentialsConfigured()) {
            "session_mock_${transactionId.toString().replace("-", "")}"
        } else {
            createRemoteOrder(transaction, request, cashfreeOrderId, transactionId)
        }

        transaction.gatewayTransactionId = cashfreeOrderId
        transactionRepository.save(transaction)
        return CashfreeOrderResponse(
            orderId = cashfreeOrderId,
            paymentSessionId = sessionId,
            amount = transaction.amount,
            currency = transaction.currency,
            transactionId = transactionId,
            environment = environment,
        )
    }

    private fun createRemoteOrder(
        transaction: Transaction,
        request: CreateCashfreeOrderRequest,
        cashfreeOrderId: String,
        transactionId: UUID,
    ): String {
        if (!credentialsConfigured()) {
            throw IllegalStateException("Cashfree credentials are not configured")
        }
        val customerDetails = linkedMapOf<String, Any>(
            "customer_id" to request.userId.toString(),
            "customer_phone" to normalizePhone(request.customerPhone),
        ).apply {
            request.customerEmail?.trim()?.takeIf { it.isNotBlank() }?.let { put("customer_email", it) }
            request.customerName?.trim()?.takeIf { it.isNotBlank() }?.let { put("customer_name", it) }
        }
        val body = linkedMapOf<String, Any>(
            "order_id" to cashfreeOrderId,
            "order_amount" to transaction.amount,
            "order_currency" to transaction.currency,
            "customer_details" to customerDetails,
            "order_note" to "MyPet order payment for ${request.referenceId}",
            "order_tags" to mapOf(
                "mypet_reference_id" to request.referenceId.toString(),
                "mypet_transaction_id" to transactionId.toString(),
            ),
        )

        try {
            val response = restTemplate.exchange(
                "$baseUrl/orders",
                HttpMethod.POST,
                HttpEntity(body, authenticatedHeaders(transactionId)),
                Map::class.java,
            )
            val responseBody = response.body
                ?: throw IllegalStateException("Cashfree returned an empty order response")
            val returnedOrderId = responseBody["order_id"] as? String
                ?: throw IllegalStateException("Cashfree response did not contain order_id")
            if (returnedOrderId != cashfreeOrderId) {
                throw IllegalStateException("Cashfree returned an unexpected order ID")
            }
            return responseBody["payment_session_id"] as? String
                ?: throw IllegalStateException("Cashfree response did not contain payment_session_id")
        } catch (error: Exception) {
            transaction.status = "FAILED"
            transactionRepository.save(transaction)
            throw IllegalStateException("Failed to create Cashfree order: ${error.message}", error)
        }
    }

    private fun responseForExisting(transaction: Transaction): CashfreeOrderResponse {
        val transactionId = transaction.transactionId
            ?: throw IllegalStateException("Existing transaction did not have an ID")
        val orderId = transaction.gatewayTransactionId
            ?: throw IllegalStateException("Existing Cashfree transaction did not have an order ID")
        return CashfreeOrderResponse(
            orderId = orderId,
            paymentSessionId = fetchPaymentSession(orderId, transactionId),
            amount = transaction.amount,
            currency = transaction.currency,
            transactionId = transactionId,
            environment = environment,
        )
    }

    fun fetchPaymentSession(orderId: String, requestId: UUID = UUID.randomUUID()): String {
        if (sandboxMode && !credentialsConfigured() && orderId.startsWith("mypet_")) {
            return "session_mock_${orderId.removePrefix("mypet_")}"
        }
        val body = fetchOrder(orderId, requestId)
        return body["payment_session_id"] as? String
            ?: throw IllegalStateException("Cashfree order does not contain payment_session_id")
    }

    private fun fetchOrder(orderId: String, requestId: UUID = UUID.randomUUID()): Map<*, *> {
        if (!credentialsConfigured()) {
            throw IllegalStateException("Cashfree credentials are not configured")
        }
        val response = restTemplate.exchange(
            "$baseUrl/orders/$orderId",
            HttpMethod.GET,
            HttpEntity<Any>(authenticatedHeaders(requestId)),
            Map::class.java,
        )
        return response.body ?: throw IllegalStateException("Cashfree returned an empty order response")
    }

    @Transactional
    fun reconcile(referenceId: UUID): PaymentResultEvent {
        val transaction = transactionRepository.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
            referenceId,
            listOf("PENDING", "SUCCESS"),
        ) ?: transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId)
            ?: throw IllegalArgumentException("Transaction not found for reference ID $referenceId")

        if (transaction.status == "SUCCESS") return event(transaction, "PaymentCaptured")
        if (transaction.gateway != "CASHFREE") {
            throw IllegalStateException("The pending transaction is not a Cashfree transaction")
        }
        val orderId = transaction.gatewayTransactionId
            ?: throw IllegalStateException("Cashfree order ID is missing")

        if (sandboxMode && !credentialsConfigured()) return event(transaction, "PaymentPending")

        val order = fetchOrder(orderId, transaction.transactionId ?: UUID.randomUUID())
        validateCashfreeOrder(transaction, order)
        if ((order["order_status"] as? String)?.uppercase() == "PAID") {
            transaction.status = "SUCCESS"
            return event(transactionRepository.save(transaction), "PaymentCaptured")
        }
        return event(transaction, "PaymentPending")
    }

    private fun validateCashfreeOrder(transaction: Transaction, order: Map<*, *>) {
        val returnedOrderId = order["order_id"] as? String
        val amount = decimal(order["order_amount"])
        val currency = order["order_currency"] as? String
        if (returnedOrderId != transaction.gatewayTransactionId) {
            throw IllegalArgumentException("Cashfree order ID does not match the initiated transaction")
        }
        if (amount == null || amount.compareTo(transaction.amount) != 0) {
            throw IllegalArgumentException("Cashfree order amount does not match the initiated transaction")
        }
        if (currency != transaction.currency) {
            throw IllegalArgumentException("Cashfree order currency does not match the initiated transaction")
        }
    }

    fun verifyWebhookSignature(rawBody: String, timestamp: String, signature: String): Boolean {
        val timestampMillis = timestamp.toLongOrNull() ?: return false
        if (abs(Instant.now().toEpochMilli() - timestampMillis) > 5 * 60 * 1000L) return false
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(webhookSecret().toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
            val expected = Base64.getEncoder().encodeToString(
                mac.doFinal((timestamp + rawBody).toByteArray(StandardCharsets.UTF_8)),
            )
            MessageDigest.isEqual(
                expected.toByteArray(StandardCharsets.UTF_8),
                signature.toByteArray(StandardCharsets.UTF_8),
            )
        } catch (error: Exception) {
            logger.warn("Cashfree webhook signature verification failed", error)
            false
        }
    }

    @Transactional
    fun processWebhook(
        rawBody: String,
        signature: String,
        timestamp: String,
        idempotencyKey: String?,
    ): Boolean {
        if (!verifyWebhookSignature(rawBody, timestamp, signature)) {
            throw IllegalArgumentException("Invalid or expired Cashfree webhook signature")
        }
        val event: Map<String, Any> = try {
            objectMapper.readValue(rawBody, object : TypeReference<Map<String, Any>>() {})
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid Cashfree webhook payload", error)
        }
        val data = event["data"] as? Map<*, *>
            ?: throw IllegalArgumentException("Cashfree webhook data is missing")
        val order = data["order"] as? Map<*, *>
            ?: throw IllegalArgumentException("Cashfree webhook order is missing")
        val payment = data["payment"] as? Map<*, *>
        val orderId = order["order_id"] as? String
            ?: throw IllegalArgumentException("Cashfree order_id is missing")
        val paymentId = payment?.get("cf_payment_id")?.toString()
        val type = event["type"]?.toString()?.uppercase() ?: "UNKNOWN"
        val eventKey = idempotencyKey?.trim()?.takeIf { it.isNotBlank() }
            ?: "$type|$orderId|${paymentId.orEmpty()}|${event["event_time"]?.toString().orEmpty()}"
        val eventUuid = runCatching { UUID.fromString(eventKey) }
            .getOrElse { UUID.nameUUIDFromBytes(eventKey.toByteArray(StandardCharsets.UTF_8)) }

        val transaction = transactionRepository.findByGatewayTransactionId(orderId)
            ?: throw IllegalArgumentException("Cashfree order is not associated with a MyPet transaction")
        if (transaction.gateway != "CASHFREE") {
            throw IllegalArgumentException("Webhook order is not a Cashfree transaction")
        }
        validateWebhookAmount(transaction, order, payment)
        if (!idempotencyService.checkAndRecord(eventUuid)) return false

        val paymentStatus = payment?.get("payment_status")?.toString()?.uppercase()
        if (type == "PAYMENT_SUCCESS_WEBHOOK" && paymentStatus == "SUCCESS") {
            transaction.status = "SUCCESS"
            transactionRepository.save(transaction)
        } else if (type == "PAYMENT_FAILED_WEBHOOK" || type == "PAYMENT_USER_DROPPED_WEBHOOK") {
            logger.info("Cashfree payment attempt {} for order {} ended with {}", paymentId, orderId, paymentStatus)
        }
        return true
    }

    private fun validateWebhookAmount(transaction: Transaction, order: Map<*, *>, payment: Map<*, *>?) {
        val orderAmount = decimal(order["order_amount"])
        val orderCurrency = order["order_currency"]?.toString()
        val paymentAmount = payment?.get("payment_amount")?.let(::decimal)
        val paymentCurrency = payment?.get("payment_currency")?.toString()
        if (orderAmount == null || orderAmount.compareTo(transaction.amount) != 0) {
            throw IllegalArgumentException("Cashfree webhook order amount does not match")
        }
        if (orderCurrency != transaction.currency) {
            throw IllegalArgumentException("Cashfree webhook order currency does not match")
        }
        if (paymentAmount != null && paymentAmount.compareTo(transaction.amount) != 0) {
            throw IllegalArgumentException("Cashfree webhook payment amount does not match")
        }
        if (paymentCurrency != null && paymentCurrency != transaction.currency) {
            throw IllegalArgumentException("Cashfree webhook payment currency does not match")
        }
    }

    @Transactional
    fun refundOrder(referenceId: UUID): Transaction {
        val transaction = transactionRepository.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
            referenceId,
            listOf("SUCCESS", "REFUND_PENDING"),
        ) ?: throw IllegalArgumentException("Successful Cashfree transaction not found for order $referenceId")
        if (transaction.gateway != "CASHFREE") {
            throw IllegalStateException("The transaction was not paid through Cashfree")
        }
        if (transaction.status == "REFUND_PENDING") return transaction
        val orderId = transaction.gatewayTransactionId
            ?: throw IllegalStateException("Cashfree order ID is missing")

        if (sandboxMode && !credentialsConfigured()) {
            transaction.status = "REFUNDED"
            return transactionRepository.save(transaction)
        }
        val transactionId = transaction.transactionId ?: UUID.randomUUID()
        val refundId = "refund_${transactionId.toString().replace("-", "")}"
        val body = mapOf(
            "refund_amount" to transaction.amount,
            "refund_id" to refundId,
            "refund_note" to "MyPet order refund for $referenceId",
            "refund_speed" to "STANDARD",
        )
        val response = restTemplate.exchange(
            "$baseUrl/orders/$orderId/refunds",
            HttpMethod.POST,
            HttpEntity(body, authenticatedHeaders(transactionId)),
            Map::class.java,
        )
        val refundStatus = response.body?.get("refund_status")?.toString()?.uppercase()
            ?: throw IllegalStateException("Cashfree refund response did not contain refund_status")
        transaction.status = if (refundStatus == "SUCCESS") "REFUNDED" else "REFUND_PENDING"
        return transactionRepository.save(transaction)
    }

    private fun event(transaction: Transaction, type: String): PaymentResultEvent = PaymentResultEvent(
        eventType = type,
        transactionId = transaction.transactionId
            ?: throw IllegalStateException("Transaction ID is missing"),
        referenceId = transaction.referenceId,
        actorId = transaction.userId,
        amount = transaction.amount,
        gateway = transaction.gateway,
        gatewayTransactionId = transaction.gatewayTransactionId,
    )

    private fun decimal(value: Any?): BigDecimal? = when (value) {
        is BigDecimal -> value.setScale(2, RoundingMode.HALF_UP)
        is Number -> value.toString().toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
        is String -> value.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
        else -> null
    }
}
