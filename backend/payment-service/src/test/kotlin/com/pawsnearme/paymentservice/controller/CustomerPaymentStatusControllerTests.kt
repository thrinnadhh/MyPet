package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.repository.TransactionRepository
import com.pawsnearme.paymentservice.service.CashfreeGatewayService
import com.pawsnearme.paymentservice.service.PaymentResultEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class CustomerPaymentStatusControllerTests {
    private val transactionRepository: TransactionRepository = mock()
    private val cashfreeGatewayService: CashfreeGatewayService = mock()
    private val service = CustomerPaymentStatusService(transactionRepository, cashfreeGatewayService)
    private val controller = CustomerPaymentStatusController(service)

    @Test
    fun `owner can read safe latest payment status`() {
        val userId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        val transaction = transaction(userId, referenceId, transactionId, "PENDING")
        whenever(transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId))
            .thenReturn(transaction)

        val response = controller.getLatestForReference(referenceId, userId.toString(), "CUSTOMER")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(transactionId, response.body?.transactionId)
        assertEquals("PENDING", response.body?.status)
        assertEquals(BigDecimal("799.00"), response.body?.amount)
        assertEquals(referenceId, response.body?.referenceId)
        assertEquals(false, response.body.toString().contains("order_private_gateway_id"))
    }

    @Test
    fun `owner can request server side Cashfree reconciliation`() {
        val userId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        val pending = transaction(userId, referenceId, transactionId, "PENDING")
        val success = transaction(userId, referenceId, transactionId, "SUCCESS")
        whenever(transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId))
            .thenReturn(pending, success)
        whenever(cashfreeGatewayService.reconcile(referenceId)).thenReturn(
            PaymentResultEvent(
                eventType = "PaymentCaptured",
                transactionId = transactionId,
                referenceId = referenceId,
                actorId = userId,
                amount = BigDecimal("799.00"),
                gateway = "CASHFREE",
                gatewayTransactionId = "mypet_order_123",
            ),
        )

        val response = controller.reconcileLatestForReference(referenceId, userId.toString(), "CUSTOMER")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("SUCCESS", response.body?.status)
    }

    @Test
    fun `different customer cannot read payment status`() {
        val ownerId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()
        whenever(transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId))
            .thenReturn(transaction(ownerId, referenceId, UUID.randomUUID(), "PENDING"))

        assertThrows<PaymentAccessDeniedException> {
            controller.getLatestForReference(referenceId, UUID.randomUUID().toString(), "CUSTOMER")
        }
    }

    @Test
    fun `different customer cannot trigger Cashfree reconciliation`() {
        val ownerId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()
        whenever(transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId))
            .thenReturn(transaction(ownerId, referenceId, UUID.randomUUID(), "PENDING"))

        assertThrows<PaymentAccessDeniedException> {
            controller.reconcileLatestForReference(referenceId, UUID.randomUUID().toString(), "CUSTOMER")
        }
    }

    @Test
    fun `administrator can read payment status`() {
        val referenceId = UUID.randomUUID()
        whenever(transactionRepository.findFirstByReferenceIdOrderByCreatedAtDesc(referenceId))
            .thenReturn(transaction(UUID.randomUUID(), referenceId, UUID.randomUUID(), "SUCCESS"))

        assertEquals(
            HttpStatus.OK,
            controller.getLatestForReference(referenceId, UUID.randomUUID().toString(), "ADMIN").statusCode,
        )
    }

    private fun transaction(
        userId: UUID,
        referenceId: UUID,
        transactionId: UUID,
        status: String,
    ) = Transaction(
        transactionId = transactionId,
        userId = userId,
        transactionType = "ORDER_PAYMENT",
        referenceId = referenceId,
        amount = BigDecimal("799.00"),
        status = status,
        gateway = "CASHFREE",
        gatewayTransactionId = "order_private_gateway_id",
        createdAt = Instant.parse("2026-08-01T18:00:00Z"),
        updatedAt = Instant.parse("2026-08-01T18:01:00Z"),
    )
}
