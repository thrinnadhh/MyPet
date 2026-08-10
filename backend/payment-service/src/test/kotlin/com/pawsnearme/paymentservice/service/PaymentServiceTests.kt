package com.pawsnearme.paymentservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.paymentservice.model.CodConfig
import com.pawsnearme.paymentservice.model.CouponReservation
import com.pawsnearme.paymentservice.model.LinkedAccount
import com.pawsnearme.paymentservice.model.Payout
import com.pawsnearme.paymentservice.model.PlatformCommissionLedger
import com.pawsnearme.paymentservice.model.Promotion
import com.pawsnearme.paymentservice.model.ProviderRef
import com.pawsnearme.paymentservice.repository.AppointmentRefRepository
import com.pawsnearme.paymentservice.repository.CaptainEarningRefRepository
import com.pawsnearme.paymentservice.repository.CodConfigRepository
import com.pawsnearme.paymentservice.repository.CouponReservationRepository
import com.pawsnearme.paymentservice.repository.LinkedAccountRepository
import com.pawsnearme.paymentservice.repository.OrderRefRepository
import com.pawsnearme.paymentservice.repository.PayoutRepository
import com.pawsnearme.paymentservice.repository.PlatformCommissionLedgerRepository
import com.pawsnearme.paymentservice.repository.PromotionRepository
import com.pawsnearme.paymentservice.repository.ProviderRefRepository
import com.pawsnearme.paymentservice.repository.TransactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class PaymentServiceTests {
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var payoutRepository: PayoutRepository
    private lateinit var promotionRepository: PromotionRepository
    private lateinit var orderRefRepository: OrderRefRepository
    private lateinit var appointmentRefRepository: AppointmentRefRepository
    private lateinit var captainEarningRefRepository: CaptainEarningRefRepository
    private lateinit var providerRefRepository: ProviderRefRepository
    private lateinit var linkedAccountRepository: LinkedAccountRepository
    private lateinit var platformCommissionLedgerRepository: PlatformCommissionLedgerRepository
    private lateinit var couponReservationRepository: CouponReservationRepository
    private lateinit var codConfigRepository: CodConfigRepository
    private lateinit var service: PaymentService

    @BeforeEach
    fun setup() {
        transactionRepository = mock()
        payoutRepository = mock()
        promotionRepository = mock()
        orderRefRepository = mock()
        appointmentRefRepository = mock()
        captainEarningRefRepository = mock()
        providerRefRepository = mock()
        linkedAccountRepository = mock()
        platformCommissionLedgerRepository = mock()
        couponReservationRepository = mock()
        codConfigRepository = mock()
        service = newService(payoutSandboxMode = true)

        whenever(promotionRepository.existsByCode(any())).thenReturn(false)
        whenever(payoutRepository.findByPayeeUserIdAndPayeeRoleAndPeriodStartAndPeriodEnd(any(), any(), any(), any()))
            .thenReturn(null)
        whenever(payoutRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<Payout>(0).also { it.payoutId = it.payoutId ?: UUID.randomUUID() }
        }
        whenever(platformCommissionLedgerRepository.save(any<PlatformCommissionLedger>())).thenAnswer { invocation ->
            invocation.getArgument<PlatformCommissionLedger>(0).also { it.ledgerId = it.ledgerId ?: UUID.randomUUID() }
        }
        whenever(linkedAccountRepository.save(any<LinkedAccount>())).thenAnswer { it.getArgument(0) }
    }

    @Test
    fun `transaction lookup returns persisted transaction only`() {
        val id = UUID.randomUUID()
        val transaction = com.pawsnearme.paymentservice.model.Transaction(
            transactionId = id,
            userId = UUID.randomUUID(),
            transactionType = "ORDER_PAYMENT",
            referenceId = UUID.randomUUID(),
            amount = BigDecimal("499.00"),
            status = "PENDING",
            gateway = "CASHFREE",
        )
        whenever(transactionRepository.findById(id)).thenReturn(Optional.of(transaction))
        assertEquals(transaction, service.getTransactionById(id))
    }

    @Test
    fun `production payout calculation refuses mock settlement provider`() {
        val production = newService(payoutSandboxMode = false)
        val error = assertThrows<IllegalStateException> {
            production.calculatePayouts(java.time.LocalDate.now(), java.time.LocalDate.now())
        }
        assertTrue(error.message!!.contains("Cashfree Easy Split"))
    }

    @Test
    fun `merchant cannot create platform wide coupon`() {
        val promo = promo(providerId = null)
        val error = assertThrows<IllegalArgumentException> {
            service.createPromotion(promo, "MERCHANT", UUID.randomUUID())
        }
        assertTrue(error.message!!.contains("ADMIN"))
        verify(promotionRepository, never()).save(any())
    }

    @Test
    fun `merchant cannot create coupon for another merchant provider`() {
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val promo = promo(providerId = providerId)
        whenever(providerRefRepository.findById(providerId)).thenReturn(Optional.of(ProviderRef(providerId, ownerId)))

        assertThrows<IllegalArgumentException> {
            service.createPromotion(promo, "MERCHANT", UUID.randomUUID())
        }
        verify(promotionRepository, never()).save(any())
    }

    @Test
    fun `valid percentage coupon is created`() {
        val providerId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val promo = promo(providerId = providerId)
        whenever(providerRefRepository.findById(providerId)).thenReturn(Optional.of(ProviderRef(providerId, ownerId)))
        whenever(promotionRepository.save(promo)).thenReturn(promo)

        assertEquals(promo, service.createPromotion(promo, "MERCHANT", ownerId))
    }

    @Test
    fun `coupon reserve is idempotent for same order`() {
        val userId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val promotionId = UUID.randomUUID()
        val promo = promo(providerId = providerId).also {
            it.promotionId = promotionId
            it.usageLimitTotal = 10
            it.usageLimitPerUser = 1
        }
        val existing = CouponReservation(
            reservationId = UUID.randomUUID(),
            promotionId = promotionId,
            code = promo.code,
            userId = userId,
            orderId = orderId,
            discountAmount = BigDecimal("30.00"),
            status = "HELD",
        )
        whenever(couponReservationRepository.findByOrderIdAndStatusIn(orderId, listOf("HELD", "REDEEMED")))
            .thenReturn(existing)

        val first = service.reserveCoupon(
            CouponReservationRequest(promo.code, BigDecimal("300.00"), providerId, userId, null, orderId)
        )
        val second = service.reserveCoupon(
            CouponReservationRequest(promo.code, BigDecimal("300.00"), providerId, userId, null, orderId)
        )

        assertEquals(existing.reservationId, first.reservationId)
        assertEquals(first.reservationId, second.reservationId)
        assertEquals(first.code, second.code)
        assertEquals(0, first.discountAmount.compareTo(second.discountAmount))
        assertEquals(first.expiresAt, second.expiresAt)
        verify(promotionRepository, never()).findByCodeForUpdate(any())
    }

    @Test
    fun `coupon release is replay safe`() {
        val userId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val reservation = CouponReservation(
            reservationId = UUID.randomUUID(),
            promotionId = UUID.randomUUID(),
            code = "SAVE10",
            userId = userId,
            orderId = orderId,
            discountAmount = BigDecimal("10.00"),
            status = "HELD",
        )
        whenever(couponReservationRepository.findByCodeAndUserIdAndOrderIdAndStatus("SAVE10", userId, orderId, "HELD"))
            .thenReturn(reservation, null)
        whenever(couponReservationRepository.save(reservation)).thenReturn(reservation)

        service.releaseCouponReservation("save10", userId, orderId)
        service.releaseCouponReservation("save10", userId, orderId)

        assertEquals("RELEASED", reservation.status)
        verify(couponReservationRepository).save(reservation)
    }

    @Test
    fun `COD city override is authoritative`() {
        whenever(codConfigRepository.findById("global_max_amount"))
            .thenReturn(Optional.of(CodConfig("global_max_amount", "1000.00")))
        whenever(codConfigRepository.findById("city_overrides_json"))
            .thenReturn(Optional.of(CodConfig("city_overrides_json", "{\"Tirupati\":500.00}")))
        whenever(codConfigRepository.findById("disabled_cities_json"))
            .thenReturn(Optional.of(CodConfig("disabled_cities_json", "[]")))

        val allowed = service.checkCodEligibility(CodCheckRequest(BigDecimal("499.00"), "Tirupati", UUID.randomUUID()))
        val denied = service.checkCodEligibility(CodCheckRequest(BigDecimal("501.00"), "Tirupati", UUID.randomUUID()))

        assertTrue(allowed.isEligible)
        assertFalse(denied.isEligible)
        assertEquals(BigDecimal("500.00"), denied.maxAllowedAmount)
    }

    private fun newService(payoutSandboxMode: Boolean) = PaymentService(
        transactionRepository = transactionRepository,
        payoutRepository = payoutRepository,
        promotionRepository = promotionRepository,
        orderRefRepository = orderRefRepository,
        appointmentRefRepository = appointmentRefRepository,
        captainEarningRefRepository = captainEarningRefRepository,
        providerRefRepository = providerRefRepository,
        linkedAccountRepository = linkedAccountRepository,
        platformCommissionLedgerRepository = platformCommissionLedgerRepository,
        couponReservationRepository = couponReservationRepository,
        codConfigRepository = codConfigRepository,
        objectMapper = ObjectMapper(),
        payoutSandboxMode = payoutSandboxMode,
    )

    private fun promo(providerId: UUID?) = Promotion(
        code = "SAVE10",
        discountType = "PERCENTAGE",
        discountValue = BigDecimal("10.00"),
        minOrderValue = BigDecimal("200.00"),
        maxDiscountAmount = BigDecimal("100.00"),
        providerId = providerId,
        isActive = true,
        validFrom = Instant.now().minusSeconds(60),
        validUntil = Instant.now().plusSeconds(3600),
    )
}
