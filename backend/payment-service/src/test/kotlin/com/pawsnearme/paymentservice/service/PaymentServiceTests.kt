package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.model.*
import com.pawsnearme.paymentservice.repository.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PaymentServiceTests {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var payoutRepository: PayoutRepository
    private lateinit var promotionRepository: PromotionRepository
    private lateinit var orderRefRepository: OrderRefRepository
    private lateinit var appointmentRefRepository: AppointmentRefRepository
    private lateinit var captainEarningRefRepository: CaptainEarningRefRepository
    private lateinit var providerRefRepository: ProviderRefRepository
    private lateinit var service: PaymentService

    @BeforeEach
    fun setup() {
        // Fresh mocks for every test to avoid stub bleed
        transactionRepository = mock()
        payoutRepository = mock()
        promotionRepository = mock()
        orderRefRepository = mock()
        appointmentRefRepository = mock()
        captainEarningRefRepository = mock()
        providerRefRepository = mock()
        service = PaymentService(
            transactionRepository, payoutRepository, promotionRepository,
            orderRefRepository, appointmentRefRepository,
            captainEarningRefRepository, providerRefRepository
        )
        // Default: code does not exist
        whenever(promotionRepository.existsByCode(any())).thenReturn(false)
        whenever(transactionRepository.save(any())).thenAnswer { invocation ->
            val transaction = invocation.getArgument<Transaction>(0)
            transaction.also { it.transactionId = it.transactionId ?: UUID.randomUUID() }
        }
    }

    private fun promoOf(
        discountType: String = "PERCENTAGE",
        discountValue: BigDecimal = BigDecimal("10"),
        minOrderValue: BigDecimal? = BigDecimal("200"),
        maxDiscountAmount: BigDecimal? = null,
        providerId: UUID? = UUID.randomUUID(),
        isActive: Boolean = true,
        validFrom: Instant = Instant.now().minusSeconds(3600),
        validUntil: Instant = Instant.now().plusSeconds(3600),
        code: String = "SAVE10"
    ) = Promotion(
        code = code,
        discountType = discountType,
        discountValue = discountValue,
        minOrderValue = minOrderValue,
        maxDiscountAmount = maxDiscountAmount,
        providerId = providerId,
        isActive = isActive,
        validFrom = validFrom,
        validUntil = validUntil
    )

    // ── createPromotion validations ───────────────────────────────────────────

    @Test
    fun `recordPaymentResult - success returns PaymentCaptured event with event id`() {
        val request = PaymentResultRequest(
            userId = UUID.randomUUID(),
            referenceId = UUID.randomUUID(),
            transactionType = "ORDER_PAYMENT",
            amount = BigDecimal("499.00"),
            gatewayTransactionId = "pay_test_123",
            success = true
        )

        val event = service.recordPaymentResult(request)

        assertNotNull(event.eventId)
        assertEquals("PaymentCaptured", event.eventType)
        assertEquals(request.userId, event.actorId)
        assertEquals(request.referenceId, event.referenceId)
    }

    @Test
    fun `recordPaymentResult - failure returns PaymentFailed event with event id`() {
        val request = PaymentResultRequest(
            userId = UUID.randomUUID(),
            referenceId = UUID.randomUUID(),
            transactionType = "ORDER_PAYMENT",
            amount = BigDecimal("499.00"),
            gatewayTransactionId = "pay_test_failed",
            success = false
        )

        val event = service.recordPaymentResult(request)

        assertNotNull(event.eventId)
        assertEquals("PaymentFailed", event.eventType)
    }

    @Test
    fun `createPromotion - platform-wide by non-admin - throws`() {
        val promo = promoOf(providerId = null)
        val ex = assertThrows<IllegalArgumentException> { service.createPromotion(promo, "MERCHANT") }
        assertTrue(ex.message!!.contains("ADMIN"), "Expected message to mention ADMIN but was: ${ex.message}")
    }

    @Test
    fun `createPromotion - duplicate code - throws`() {
        val promo = promoOf()
        whenever(promotionRepository.existsByCode(promo.code)).thenReturn(true)
        val ex = assertThrows<IllegalArgumentException> { service.createPromotion(promo, "MERCHANT") }
        assertTrue(ex.message!!.contains("already exists"), "Expected 'already exists' in: ${ex.message}")
    }

    @Test
    fun `createPromotion - percentage over 30 - throws`() {
        val promo = promoOf(discountType = "PERCENTAGE", discountValue = BigDecimal("35"))
        val ex = assertThrows<IllegalArgumentException> { service.createPromotion(promo, "MERCHANT") }
        assertTrue(ex.message!!.contains("30%"), "Expected '30%' in: ${ex.message}")
    }

    @Test
    fun `createPromotion - flat without minOrderValue - throws`() {
        val promo = promoOf(discountType = "FLAT", discountValue = BigDecimal("50"), minOrderValue = null)
        val ex = assertThrows<IllegalArgumentException> { service.createPromotion(promo, "MERCHANT") }
        assertTrue(ex.message!!.contains("Minimum order value is required"), "Expected min order msg, got: ${ex.message}")
    }

    @Test
    fun `createPromotion - flat exceeds 30 pct of minOrder - throws`() {
        // discount=60, minOrder=100 → 60 > 100 * 0.30 = 30 → exceeds
        val promo = promoOf(discountType = "FLAT", discountValue = BigDecimal("60"), minOrderValue = BigDecimal("100"))
        val ex = assertThrows<IllegalArgumentException> { service.createPromotion(promo, "MERCHANT") }
        assertTrue(ex.message!!.contains("30%"), "Expected '30%' in: ${ex.message}")
    }

    @Test
    fun `createPromotion - valid percentage - saves and returns`() {
        val promo = promoOf() // 10% with minOrder=200, valid
        whenever(promotionRepository.save(any())).thenReturn(promo)
        val result = service.createPromotion(promo, "MERCHANT")
        assertEquals(promo.code, result.code)
        verify(promotionRepository).save(promo)
    }

    // ── validateCoupon ────────────────────────────────────────────────────────

    @Test
    fun `validateCoupon - code not found - throws`() {
        whenever(promotionRepository.findByCode("INVALID")).thenReturn(null)
        val ex = assertThrows<IllegalArgumentException> {
            service.validateCoupon("INVALID", BigDecimal("500"), UUID.randomUUID(), null)
        }
        assertTrue(ex.message!!.contains("Invalid coupon"))
    }

    @Test
    fun `validateCoupon - inactive - throws`() {
        val promo = promoOf(isActive = false)
        whenever(promotionRepository.findByCode(promo.code)).thenReturn(promo)
        val ex = assertThrows<IllegalArgumentException> {
            service.validateCoupon(promo.code, BigDecimal("500"), UUID.randomUUID(), null)
        }
        assertTrue(ex.message!!.contains("inactive"))
    }

    @Test
    fun `validateCoupon - expired - throws`() {
        val promo = promoOf(validUntil = Instant.now().minusSeconds(1))
        whenever(promotionRepository.findByCode(promo.code)).thenReturn(promo)
        val ex = assertThrows<IllegalArgumentException> {
            service.validateCoupon(promo.code, BigDecimal("500"), UUID.randomUUID(), null)
        }
        assertTrue(ex.message!!.contains("expired"))
    }

    @Test
    fun `validateCoupon - order below minimum - throws`() {
        val promo = promoOf(minOrderValue = BigDecimal("500"), providerId = null)
        whenever(promotionRepository.findByCode(promo.code)).thenReturn(promo)
        val ex = assertThrows<IllegalArgumentException> {
            service.validateCoupon(promo.code, BigDecimal("100"), UUID.randomUUID(), null)
        }
        assertTrue(ex.message!!.contains("not met") || ex.message!!.contains("Minimum"), "Got: ${ex.message}")
    }

    @Test
    fun `validateCoupon - valid - returns promo`() {
        val providerId = UUID.randomUUID()
        // Promo scoped to this specific provider
        val promo = promoOf(providerId = providerId)
        whenever(promotionRepository.findByCode(promo.code)).thenReturn(promo)
        val result = service.validateCoupon(promo.code, BigDecimal("300"), providerId, null)
        assertEquals(promo.code, result.code)
    }
}
