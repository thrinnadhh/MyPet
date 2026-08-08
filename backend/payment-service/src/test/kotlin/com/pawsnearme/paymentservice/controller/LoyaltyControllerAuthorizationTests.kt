package com.pawsnearme.paymentservice.controller

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.paymentservice.model.LoyaltyProgram
import com.pawsnearme.paymentservice.service.LoyaltyLifecycleService
import com.pawsnearme.paymentservice.service.LoyaltyReconciliationService
import com.pawsnearme.paymentservice.service.LoyaltyService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class LoyaltyControllerAuthorizationTests {
    private val loyaltyService: LoyaltyService = mock()
    private val providerModule: ProviderModuleApi = mock()
    private val loyaltyLifecycleService: LoyaltyLifecycleService = mock()
    private val loyaltyReconciliationService: LoyaltyReconciliationService = mock()
    private val controller = LoyaltyController(
        loyaltyService,
        providerModule,
        loyaltyLifecycleService,
        loyaltyReconciliationService,
        "test-internal-api-secret",
    )

    @Test
    fun `provider may update only an owned store program`() {
        val actorId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val program = program(providerId, BigDecimal("150"))
        whenever(providerModule.ownerUserId(providerId)).thenReturn(actorId)
        whenever(loyaltyService.updateProgram(any(), any())).thenAnswer { it.getArgument(0) }

        val response = controller.updateProgram(program, "PROVIDER", actorId.toString())

        assertEquals(BigDecimal("150"), response.body!!.rewardAmount)
        assertEquals(true, response.body!!.isStackable)
        verify(loyaltyService).updateProgram(program, actorId)
    }

    @Test
    fun `provider cannot update another merchants program`() {
        val actorId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        whenever(providerModule.ownerUserId(providerId)).thenReturn(UUID.randomUUID())

        assertThrows<PaymentAccessDeniedException> {
            controller.updateProgram(program(providerId, BigDecimal("50")), "PROVIDER", actorId.toString())
        }
    }

    @Test
    fun `program rejects unsupported reward values and missing identity`() {
        val actorId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        whenever(providerModule.ownerUserId(providerId)).thenReturn(actorId)

        assertThrows<IllegalArgumentException> {
            controller.updateProgram(program(providerId, BigDecimal("75")), "PROVIDER", actorId.toString())
        }
        assertThrows<PaymentAccessDeniedException> {
            controller.updateProgram(program(providerId, BigDecimal("50")), "PROVIDER", null)
        }
    }

    private fun program(providerId: UUID, reward: BigDecimal) = LoyaltyProgram(
        providerId = providerId,
        targetStars = 10,
        rewardAmount = reward,
        minOrderValue = BigDecimal("199"),
        welcomeStarPolicy = true,
        isActive = true,
        isStackable = false,
        expiryDays = 60
    )
}
