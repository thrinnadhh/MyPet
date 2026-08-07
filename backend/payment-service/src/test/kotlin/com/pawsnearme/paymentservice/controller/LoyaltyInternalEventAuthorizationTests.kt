package com.pawsnearme.paymentservice.controller

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.paymentservice.service.LoyaltyReconciliationService
import com.pawsnearme.paymentservice.service.LoyaltyService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class LoyaltyInternalEventAuthorizationTests {
    private val loyaltyService: LoyaltyService = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val reconciliationService: LoyaltyReconciliationService = mock()
    private val secret = "0123456789abcdef0123456789abcdef"
    private val controller = LoyaltyController(loyaltyService, providerModule, reconciliationService, secret)

    @Test
    fun `delivered loyalty event rejects requests without internal authorization`() {
        val payload = OrderDeliveredEventPayload(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            providerId = UUID.randomUUID(),
            netAmount = BigDecimal("500.00"),
        )

        val error = assertThrows<PaymentAccessDeniedException> {
            controller.handleOrderDelivered(payload, null)
        }

        assertTrue(error.message!!.contains("authorization"))
    }

    @Test
    fun `delivered loyalty event accepts the internal service secret`() {
        val payload = OrderDeliveredEventPayload(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            providerId = UUID.randomUUID(),
            netAmount = BigDecimal("500.00"),
        )
        whenever(
            loyaltyService.processOrderDeliveredEvent(
                payload.orderId,
                payload.customerId,
                payload.providerId,
                payload.netAmount,
            )
        ).thenReturn(true)

        val response = controller.handleOrderDelivered(payload, secret)

        assertTrue(response.body?.get("processed") == true)
        verify(loyaltyService).processOrderDeliveredEvent(
            payload.orderId,
            payload.customerId,
            payload.providerId,
            payload.netAmount,
        )
    }

    @Test
    fun `refund loyalty event rejects an incorrect internal secret`() {
        val payload = OrderRefundedEventPayload(
            orderId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            providerId = UUID.randomUUID(),
        )

        assertThrows<PaymentAccessDeniedException> {
            controller.handleOrderRefunded(payload, "wrong-secret")
        }
    }
}
