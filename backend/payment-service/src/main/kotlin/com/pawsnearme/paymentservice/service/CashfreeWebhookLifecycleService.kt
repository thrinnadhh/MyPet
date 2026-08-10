package com.pawsnearme.paymentservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.common.idempotency.IdempotencyService
import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class CashfreeWebhookLifecycleService(
    private val cashfreeGatewayService: CashfreeGatewayService,
    private val transactionRepository: TransactionRepository,
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper,
    private val orderPaymentLifecycleService: OrderPaymentLifecycleService,
) {
    fun process(
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
        val data = event["data"] as? Map<*, *>
            ?: throw IllegalArgumentException("Cashfree webhook data is missing")
        val order = data["order"] as? Map<*, *>
            ?: throw IllegalArgumentException("Cashfree webhook order is missing")
        val payment = data["payment"] as? Map<*, *>
        val orderId = order["order_id"]?.toString()
            ?: throw IllegalArgumentException("Cashfree order_id is missing")
        val paymentId = payment?.get("cf_payment_id")?.toString()
        val type = event["type"]?.toString()?.uppercase() ?: "UNKNOWN"
        val eventKey = idempotencyKey?.trim()?.takeIf { it.isNotBlank() }
            ?: "$type|$orderId|${paymentId.orEmpty()}|${event["event_time"]?.toString().orEmpty()}"
        val eventUuid = runCatching { UUID.fromString(eventKey) }
            .getOrElse { UUID.nameUUIDFromBytes(eventKey.toByteArray(StandardCharsets.UTF_8)) }

        val transaction = transactionRepository.findByGatewayTransactionId(orderId)
            ?: throw IllegalArgumentException("Cashfree order is not associated with a MyPet transaction")
        require(transaction.gateway == "CASHFREE") { "Webhook order is not a Cashfree transaction" }
        validateAmount(transaction.amount, transaction.currency, order, payment)
        if (!idempotencyService.checkAndRecord(eventUuid)) return false

        val paymentStatus = payment?.get("payment_status")?.toString()?.uppercase()
        when (type) {
            "PAYMENT_SUCCESS_WEBHOOK" -> {
                if (paymentStatus != "SUCCESS") {
                    throw IllegalArgumentException("Cashfree success webhook did not contain SUCCESS payment status")
                }
                orderPaymentLifecycleService.capture(transaction)
            }
            "PAYMENT_FAILED_WEBHOOK" -> orderPaymentLifecycleService.fail(
                transaction,
                "Cashfree reported payment failure (${paymentStatus ?: "UNKNOWN"})",
            )
            "PAYMENT_USER_DROPPED_WEBHOOK" -> orderPaymentLifecycleService.fail(
                transaction,
                "Customer abandoned the Cashfree payment flow",
            )
            else -> return true
        }
        return true
    }

    private fun validateAmount(
        expectedAmount: BigDecimal,
        expectedCurrency: String,
        order: Map<*, *>,
        payment: Map<*, *>?,
    ) {
        val orderAmount = decimal(order["order_amount"])
        val orderCurrency = order["order_currency"]?.toString()
        val paymentAmount = payment?.get("payment_amount")?.let(::decimal)
        val paymentCurrency = payment?.get("payment_currency")?.toString()
        require(orderAmount != null && orderAmount.compareTo(expectedAmount) == 0) {
            "Cashfree webhook order amount does not match"
        }
        require(orderCurrency == expectedCurrency) { "Cashfree webhook order currency does not match" }
        require(paymentAmount == null || paymentAmount.compareTo(expectedAmount) == 0) {
            "Cashfree webhook payment amount does not match"
        }
        require(paymentCurrency == null || paymentCurrency == expectedCurrency) {
            "Cashfree webhook payment currency does not match"
        }
    }

    private fun decimal(value: Any?): BigDecimal? = when (value) {
        is BigDecimal -> value.setScale(2, RoundingMode.HALF_UP)
        is Number -> value.toString().toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
        is String -> value.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)
        else -> null
    }
}
