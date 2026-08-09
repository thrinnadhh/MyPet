package com.pawsnearme.paymentservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
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
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class CashfreeRefundLifecycleService(
    private val transactionRepository: TransactionRepository,
    private val cashfreeGatewayService: CashfreeGatewayService,
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper,
    @Value("\${CASHFREE_CLIENT_ID:}") private val clientId: String = "",
    @Value("\${CASHFREE_CLIENT_SECRET:}") private val clientSecret: String = "",
    @Value("\${CASHFREE_API_VERSION:2025-01-01}") private val apiVersion: String = "2025-01-01",
    @Value("\${CASHFREE_SANDBOX_MODE:false}") private val sandboxMode: Boolean = false,
    private val restTemplate: RestOperations = RestTemplate(),
) {
    private val baseUrl: String
        get() = if (sandboxMode) "https://sandbox.cashfree.com/pg" else "https://api.cashfree.com/pg"

    @Transactional
    fun processWebhook(
        rawBody: String,
        signature: String,
        timestamp: String,
        idempotencyKey: String?,
    ): Boolean {
        if (!cashfreeGatewayService.verifyWebhookSignature(rawBody, timestamp, signature)) {
            throw IllegalArgumentException("Invalid or expired Cashfree webhook signature")
        }
        val event: Map<String, Any> = try {
            objectMapper.readValue(rawBody, object : TypeReference<Map<String, Any>>() {})
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid Cashfree webhook payload", error)
        }
        val type = event["type"]?.toString()?.uppercase()
            ?: throw IllegalArgumentException("Cashfree webhook type is missing")
        if (type != "REFUND_STATUS_WEBHOOK") {
            return cashfreeGatewayService.processWebhook(rawBody, signature, timestamp, idempotencyKey)
        }

        val refund = (event["data"] as? Map<*, *>)?.get("refund") as? Map<*, *>
            ?: throw IllegalArgumentException("Cashfree refund webhook data is missing")
        val gatewayOrderId = refund["order_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Cashfree refund webhook order_id is missing")
        val refundId = refund["refund_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Cashfree refund webhook refund_id is missing")
        val eventKey = idempotencyKey?.trim()?.takeIf { it.isNotBlank() }
            ?: "$type|$gatewayOrderId|$refundId|${event["event_time"]?.toString().orEmpty()}"
        val eventUuid = runCatching { UUID.fromString(eventKey) }
            .getOrElse { UUID.nameUUIDFromBytes(eventKey.toByteArray(StandardCharsets.UTF_8)) }

        // Validate that the refund belongs to a MyPet transaction before consuming
        // the idempotency key. A transient lookup failure must remain retryable.
        val transaction = transactionRepository.findRefundByGatewayTransactionId(gatewayOrderId)
            ?: throw IllegalArgumentException("Cashfree refund is not associated with a MyPet transaction")
        validateRefundIdentity(transaction, refundId)
        if (!idempotencyService.checkAndRecord(eventUuid)) return false

        reconcileLocked(transaction, gatewayOrderId, refundId)
        return true
    }

    @Transactional
    fun reconcileReference(referenceId: UUID): Transaction {
        val transaction = transactionRepository.findFirstByReferenceIdAndStatusInOrderByCreatedAtDesc(
            referenceId,
            listOf("REFUND_PENDING", "REFUNDED", "REFUND_FAILED"),
        ) ?: throw IllegalArgumentException("Refund transaction not found for reference ID $referenceId")
        if (transaction.status == "REFUNDED") return transaction
        val gatewayOrderId = transaction.gatewayTransactionId
            ?: throw IllegalStateException("Cashfree order ID is missing")
        val refundId = expectedRefundId(transaction)
        return reconcileLocked(transaction, gatewayOrderId, refundId)
    }

    private fun reconcileLocked(
        transaction: Transaction,
        gatewayOrderId: String,
        refundId: String,
    ): Transaction {
        if (transaction.gateway != "CASHFREE") {
            throw IllegalStateException("Refund reconciliation is only supported for Cashfree transactions")
        }
        validateRefundIdentity(transaction, refundId)
        if (transaction.status == "REFUNDED") return transaction
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw IllegalStateException("Cashfree credentials are not configured for refund reconciliation")
        }

        val response = restTemplate.exchange(
            "$baseUrl/orders/$gatewayOrderId/refunds/$refundId",
            HttpMethod.GET,
            HttpEntity<Any>(authenticatedHeaders()),
            Map::class.java,
        ).body ?: throw IllegalStateException("Cashfree returned an empty refund response")

        validateRemoteRefund(transaction, gatewayOrderId, refundId, response)
        return when (response["refund_status"]?.toString()?.uppercase()) {
            "SUCCESS" -> {
                transaction.status = "REFUNDED"
                transactionRepository.save(transaction)
            }
            "FAILED", "CANCELLED" -> {
                transaction.status = "REFUND_FAILED"
                transactionRepository.save(transaction)
            }
            "PENDING", "ONHOLD" -> {
                if (transaction.status != "REFUND_PENDING") {
                    transaction.status = "REFUND_PENDING"
                    transactionRepository.save(transaction)
                } else transaction
            }
            else -> throw IllegalStateException("Cashfree returned an unsupported refund status")
        }
    }

    private fun authenticatedHeaders(): HttpHeaders = HttpHeaders().apply {
        accept = listOf(MediaType.APPLICATION_JSON)
        set("x-api-version", apiVersion)
        set("x-client-id", clientId)
        set("x-client-secret", clientSecret)
    }

    private fun validateRefundIdentity(transaction: Transaction, refundId: String) {
        if (refundId != expectedRefundId(transaction)) {
            throw IllegalArgumentException("Cashfree refund ID does not match the MyPet refund attempt")
        }
    }

    private fun validateRemoteRefund(
        transaction: Transaction,
        gatewayOrderId: String,
        refundId: String,
        response: Map<*, *>,
    ) {
        if (response["order_id"]?.toString() != gatewayOrderId) {
            throw IllegalArgumentException("Cashfree refund order ID does not match")
        }
        if (response["refund_id"]?.toString() != refundId) {
            throw IllegalArgumentException("Cashfree refund ID does not match")
        }
        val amount = decimal(response["refund_amount"])
            ?: throw IllegalArgumentException("Cashfree refund amount is missing")
        if (amount.compareTo(transaction.amount) != 0) {
            throw IllegalArgumentException("Cashfree refund amount does not match the transaction")
        }
        val currency = response["refund_currency"]?.toString()
            ?: throw IllegalArgumentException("Cashfree refund currency is missing")
        if (currency != transaction.currency) {
            throw IllegalArgumentException("Cashfree refund currency does not match the transaction")
        }
    }

    private fun expectedRefundId(transaction: Transaction): String {
        val transactionId = transaction.transactionId
            ?: throw IllegalStateException("Payment transaction ID is missing")
        return "refund_${transactionId.toString().replace("-", "")}"
    }

    private fun decimal(value: Any?): BigDecimal? = when (value) {
        is BigDecimal -> value
        is Number -> value.toString().toBigDecimalOrNull()
        is String -> value.toBigDecimalOrNull()
        else -> null
    }
}
