package com.pawsnearme.paymentservice.module

import com.pawsnearme.paymentservice.model.Transaction
import com.pawsnearme.paymentservice.service.CashfreeGatewayService
import com.pawsnearme.paymentservice.service.CouponReservationLifecycleService
import com.pawsnearme.paymentservice.service.LoyaltyLifecycleService
import com.pawsnearme.paymentservice.service.PaymentService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class PaymentModuleFacadeTests {
    private val paymentService: PaymentService = mock()
    private val cashfreeGatewayService: CashfreeGatewayService = mock()
    private val loyaltyLifecycleService: LoyaltyLifecycleService = mock()
    private val couponReservationLifecycleService: CouponReservationLifecycleService = mock()
    private val facade = PaymentModuleFacade(
        paymentService = paymentService,
        cashfreeGatewayService = cashfreeGatewayService,
        loyaltyLifecycleService = loyaltyLifecycleService,
        couponReservationLifecycleService = couponReservationLifecycleService,
    )

    @Test
    fun `admin module refund uses the production Cashfree refund path`() {
        val orderId = UUID.randomUUID()
        whenever(cashfreeGatewayService.refundOrder(orderId)).thenReturn(mock<Transaction>())

        facade.refundOrder(orderId)

        verify(cashfreeGatewayService).refundOrder(orderId)
        verify(paymentService, never()).refundPayment(any())
    }
}
