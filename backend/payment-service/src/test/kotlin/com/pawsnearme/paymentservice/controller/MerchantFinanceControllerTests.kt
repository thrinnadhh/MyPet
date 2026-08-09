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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
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
    fun `owner receives exact provider revenue and account payout totals`() {
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
                    customerId = UUID.randomUUID(),
                    providerId = providerId,
                    status = "COMPLETED",
                    priceAmount = BigDecimal("500.00"),
                    completedAt = Instant.now()
                )
            )
        )
        val paid = Payout(
            payeeUserId = ownerId,
            payeeRole = "MERCHANT",
            amount = BigDecimal("700.00"),
            status = "PAID",
            periodStart = LocalDate.now().minusDays(30),
            periodEnd = LocalDate.now().minusDays(1)
        )
        val processing = Payout(
            payeeUserId = ownerId,
            payeeRole = "MERCHANT",
            amount = BigDecimal("300.00"),
            status = "PROCESSING",
            periodStart = LocalDate.now(),
            periodEnd = LocalDate.now()
        )
        whenever(payoutRepository.findByPayeeUserIdOrderByCreatedAtDesc(eq(ownerId), any<Pageable>())).thenReturn(
            PageImpl(listOf(processing, paid))
        )
        whenever(payoutRepository.sumPaidAmountByPayeeUserId(ownerId)).thenReturn(BigDecimal("700.00"))
        whenever(payoutRepository.sumInFlightAmountByPayeeUserId(ownerId)).thenReturn(BigDecimal("300.00"))

        val summary = service.summary(providerId, ownerId.toString(), "MERCHANT")

        assertEquals(BigDecimal("1500.00"), summary.totalGrossRevenue)
        assertEquals(BigDecimal("1400.00"), summary.totalNetRevenue)
        assertEquals(BigDecimal("100.00"), summary.orderCommission)
        assertEquals(BigDecimal("700.00"), summary.accountPaidOut)
        assertEquals(BigDecimal("300.00"), summary.accountPayoutInFlight)
        assertEquals(1, summary.deliveredOrderCount)
        assertEquals(1, summary.completedAppointmentCount)
        assertEquals(2, summary.payoutTotalRecords)
        assertEquals(50, summary.payoutPageSize)
    }

    @Test
    fun `payout history size is capped to one hundred records per request`() {
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        whenever(providerRepository.findById(providerId)).thenReturn(
            Optional.of(ProviderRef(providerId, ownerId, BigDecimal("15.00")))
        )
        whenever(orderRepository.findByProviderIdAndStatus(providerId, "DELIVERED")).thenReturn(emptyList())
        whenever(appointmentRepository.findByProviderIdAndStatus(providerId, "COMPLETED")).thenReturn(emptyList())
        whenever(ledgerRepository.findByProviderId(providerId)).thenReturn(emptyList())
        whenever(payoutRepository.findByPayeeUserIdOrderByCreatedAtDesc(eq(ownerId), any<Pageable>())).thenAnswer { invocation ->
            val pageable = invocation.arguments[1] as Pageable
            assertEquals(100, pageable.pageSize)
            assertEquals(0, pageable.pageNumber)
            PageImpl<Payout>(emptyList(), pageable, 0)
        }
        whenever(payoutRepository.sumPaidAmountByPayeeUserId(ownerId)).thenReturn(BigDecimal.ZERO)
        whenever(payoutRepository.sumInFlightAmountByPayeeUserId(ownerId)).thenReturn(BigDecimal.ZERO)

        val summary = service.summary(providerId, ownerId.toString(), "MERCHANT", payoutPage = -2, payoutSize = 5000)

        assertEquals(0, summary.payoutPage)
        assertEquals(100, summary.payoutPageSize)
        verify(payoutRepository).findByPayeeUserIdOrderByCreatedAtDesc(eq(ownerId), any<Pageable>())
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
        whenever(payoutRepository.findByPayeeUserIdOrderByCreatedAtDesc(eq(ownerId), any<Pageable>())).thenReturn(
            PageImpl<Payout>(emptyList())
        )
        whenever(payoutRepository.sumPaidAmountByPayeeUserId(ownerId)).thenReturn(BigDecimal.ZERO)
        whenever(payoutRepository.sumInFlightAmountByPayeeUserId(ownerId)).thenReturn(BigDecimal.ZERO)

        val summary = service.summary(providerId, UUID.randomUUID().toString(), "ADMIN")
        assertEquals(BigDecimal("0.00"), summary.totalNetRevenue)
    }
}
