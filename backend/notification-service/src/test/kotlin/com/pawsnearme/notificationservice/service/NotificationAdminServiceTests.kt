package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.model.EmailDelivery
import com.pawsnearme.notificationservice.model.NotificationAdminAudit
import com.pawsnearme.notificationservice.repository.EmailDeliveryRepository
import com.pawsnearme.notificationservice.repository.NotificationAdminAuditRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.UUID

class NotificationAdminServiceTests {
    private val deliveryRepository: EmailDeliveryRepository = mock()
    private val auditRepository: NotificationAdminAuditRepository = mock()
    private val service = NotificationAdminService(deliveryRepository, auditRepository)

    @Test
    fun `list is bounded and masks recipient email`() {
        val delivery = delivery("FAILED")
        whenever(deliveryRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 25)))
            .thenReturn(PageImpl(listOf(delivery), PageRequest.of(0, 25), 1))
        val page = service.list(0, 25, null)
        assertEquals("cu***@example.com", page.content.single().recipientEmailMasked)
    }

    @Test
    fun `failed delivery gets one audited retry under row lock`() {
        val delivery = delivery("FAILED").apply { attemptCount = 5; lastError = "Provider rejected request" }
        val actor = UUID.randomUUID()
        whenever(deliveryRepository.findByEmailDeliveryId(delivery.emailDeliveryId)).thenReturn(delivery)
        whenever(deliveryRepository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(auditRepository.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.retryFailed(delivery.emailDeliveryId, actor, "Provider failure reviewed", "req-1")

        assertEquals("RETRY", result.status)
        assertEquals(4, result.attemptCount)
        assertTrue(result.lastError!!.contains("Manual admin retry scheduled"))
        verify(auditRepository).save(argThat<NotificationAdminAudit> {
            actorUserId == actor && targetId == delivery.emailDeliveryId && previousState == "FAILED" && newState == "RETRY" && reason == "Provider failure reviewed"
        })
    }

    @Test
    fun `ambiguous delivery cannot be retried or audited`() {
        val delivery = delivery("UNKNOWN")
        whenever(deliveryRepository.findByEmailDeliveryId(delivery.emailDeliveryId)).thenReturn(delivery)
        assertThrows<IllegalArgumentException> { service.retryFailed(delivery.emailDeliveryId, UUID.randomUUID(), "Retry requested", null) }
        verify(deliveryRepository, never()).save(any())
        verify(auditRepository, never()).save(any())
    }

    @Test
    fun `retry reason is validated before loading row`() {
        val id = UUID.randomUUID()
        assertThrows<IllegalArgumentException> { service.retryFailed(id, UUID.randomUUID(), "x", null) }
        verify(deliveryRepository, never()).findByEmailDeliveryId(id)
    }

    private fun delivery(status: String) = EmailDelivery(
        emailDeliveryId = UUID.randomUUID(),
        idempotencyKey = "order:${UUID.randomUUID()}:delivered",
        userId = UUID.randomUUID(),
        recipientEmail = "customer@example.com",
        recipientName = "Customer",
        templateCode = "ORDER_DELIVERED",
        variablesJson = "{}",
        provider = "MSG91",
        status = status,
        attemptCount = 1,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
