package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.AppointmentRef
import com.pawsnearme.paymentservice.model.OrderRef
import com.pawsnearme.paymentservice.model.Payout
import com.pawsnearme.paymentservice.model.PlatformCommissionLedger
import com.pawsnearme.paymentservice.model.ProviderRef
import com.pawsnearme.paymentservice.repository.AppointmentRefRepository
import com.pawsnearme.paymentservice.repository.OrderRefRepository
import com.pawsnearme.paymentservice.repository.PayoutRepository
import com.pawsnearme.paymentservice.repository.PlatformCommissionLedgerRepository
import com.pawsnearme.paymentservice.repository.ProviderRefRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class MerchantFinanceControllerTests {
    private val providerRepository: ProviderRefRepository = mock()
    private val orderRepository: OrderRefRepository = mock()
    private val appointmentRepository: AppointmentRefRepository = mock()
    private val ledgerRepository: PlatformCommissionLedgerRepository = mock()
    private val payoutRepository: PayoutRepository = mock()
    private val service = MerchantFinanceService(
        providerRepository,
        orderRepository,
        appointmentRepository,
        ledgerRepository,
        payoutRepository
    )

    @Test
    fun `owner receives provider revenue and account payout totals`() {
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId)).thenReturn(
            Optional.of(ProviderRef(providerId, ownerId, BigDecimal("10.00")))
        )
        whenever(orderRepository.findByProviderIdAndStatus(providerId, "DELIVERED")).thenReturn(
            listOf(
                OrderRef(
                    orderId = orderId,
                    providerId = providerId,
                    customerId = UUID.randomUUID(),
                    captainId = UUID.randomUUID(),
                    status = "DELIVERED",
                    totalAmount = BigDecimal("1000.00"),
                    deliveredAt = Instant.now()
                )
            )
        )
        whenever(ledgerRepository.findByProviderId(providerId)).thenReturn(
            listOf(
                PlatformCommissionLedger(
                    providerId = providerId,
                    orderId = orderId,
                    grossAmount = BigDecimal("1000.00"),
                    commissionPct = BigDecimal("10.00"),
                    commissionAmount = BigDecimal("100.00"),
                    netMerchantAmount = BigDecimal("900.00")
                )
            )
        )
        whenever(appointmentRepository.findByProviderIdAndStatus(providerId, "COMPLETED")).thenReturn(
            listOf(
                AppointmentRef(
                    appointmentId = UUID.randomUUID(),
                    providerId = providerId,
                    status = "COMPLETED",
                    priceAmount = BigDecimal("500.00"),
                    completedAt = Instant.now()
                )
            )
        )
        whenever(payoutRepository.findByPayeeUserId(ownerId)).thenReturn(
            listOf(
                Payout(
                    payeeUserId = ownerId,
                    payeeRole = "MERCHANT",
                    amount = BigDecimal("700.00"),
                    status = "PAID",
                    periodStart = LocalDate.now().minusDays(30),
                    periodEnd = LocalDate.now().minusDays(1)
                ),
                Payout(
                    payeeUserId = ownerId,
                    payeeRole = "MERCHANT",
                    amount = BigDecimal("300.00"),
                    status = "PROCESSING",
                    periodStart = LocalDate.now(),
                    periodEnd = LocalDate.now()
                )
            )
        )

        val summary = service.summary(providerId, ownerId.toString(), "MERCHANT")

        assertEquals(BigDecimal("1500.00"), summary.totalGrossRevenue)
        assertEquals(BigDecimal("1400.00"), summary.totalNetRevenue)
        assertEquals(BigDecimal("100.00"), summary.orderCommission)
        assertEquals(BigDecimal("700.00"), summary.accountPaidOut)
        assertEquals(BigDecimal("300.00"), summary.accountPayoutInFlight)
        assertEquals(1, summary.deliveredOrderCount)
        assertEquals(1, summary.completedAppointmentCount)
    }

    @Test
    fun `different merchant cannot read provider finance`() {
        val providerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId)).thenReturn(
            Optional.of(ProviderRef(providerId, UUID.randomUUID(), BigDecimal("15.00")))
        )

        assertThrows<PaymentAccessDeniedException> {
            service.summary(providerId, UUID.randomUUID().toString(), "MERCHANT")
        }
    }

    @Test
    fun `admin can read provider finance`() {
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId)).thenReturn(
            Optional.of(ProviderRef(providerId, ownerId, BigDecimal("15.00")))
        )
        whenever(orderRepository.findByProviderIdAndStatus(providerId, "DELIVERED")).thenReturn(emptyList())
        whenever(appointmentRepository.findByProviderIdAndStatus(providerId, "COMPLETED")).thenReturn(emptyList())
        whenever(ledgerRepository.findByProviderId(providerId)).thenReturn(emptyList())
        whenever(payoutRepository.findByPayeeUserId(ownerId)).thenReturn(emptyList())

        val summary = service.summary(providerId, UUID.randomUUID().toString(), "ADMIN")
        assertEquals(BigDecimal("0.00"), summary.totalNetRevenue)
    }
}
