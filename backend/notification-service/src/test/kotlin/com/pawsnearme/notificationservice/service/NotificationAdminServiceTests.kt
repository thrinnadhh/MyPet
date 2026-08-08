package com.pawsnearme.notificationservice.service

import com.pawsnearme.notificationservice.model.EmailDelivery
import com.pawsnearme.notificationservice.repository.EmailDeliveryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
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
    private val service = NotificationAdminService(deliveryRepository)

    @Test
    fun `list is bounded and masks recipient email`() {
        val delivery = delivery(status = "FAILED")
        whenever(deliveryRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 25)))
            .thenReturn(PageImpl(listOf(delivery), PageRequest.of(0, 25), 1))

        val page = service.list(0, 25, null)

        assertEquals(1, page.totalElements)
        assertEquals("cu***@example.com", page.content.single().recipientEmailMasked)
        assertEquals("FAILED", page.content.single().status)
    }

    @Test
    fun `status filter is server bounded`() {
        val delivery = delivery(status = "UNKNOWN")
        whenever(deliveryRepository.findByStatusOrderByCreatedAtDesc("UNKNOWN", PageRequest.of(0, 10)))
            .thenReturn(PageImpl(listOf(delivery), PageRequest.of(0, 10), 1))

        val page = service.list(0, 10, "unknown")

        assertEquals("UNKNOWN", page.content.single().status)
        assertThrows<IllegalArgumentException> { service.list(0, 10, "BOGUS") }
        assertThrows<IllegalArgumentException> { service.list(0, 101, null) }
    }

    @Test
    fun `failed delivery gets exactly one newly scheduled attempt under row lock`() {
        val delivery = delivery(status = "FAILED").apply {
            attemptCount = 5
            lastError = "Provider rejected request"
        }
        whenever(deliveryRepository.findByEmailDeliveryId(delivery.emailDeliveryId)).thenReturn(delivery)
        whenever(deliveryRepository.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.retryFailed(delivery.emailDeliveryId)

        assertEquals("RETRY", result.status)
        assertEquals(4, result.attemptCount)
        assertTrue(result.lastError!!.contains("Manual admin retry scheduled"))
        verify(deliveryRepository).findByEmailDeliveryId(delivery.emailDeliveryId)
        verify(deliveryRepository).save(delivery)
    }

    @Test
    fun `unknown delivery cannot be retried because provider outcome is ambiguous`() {
        val delivery = delivery(status = "UNKNOWN")
        whenever(deliveryRepository.findByEmailDeliveryId(delivery.emailDeliveryId)).thenReturn(delivery)

        assertThrows<IllegalArgumentException> {
            service.retryFailed(delivery.emailDeliveryId)
        }

        verify(deliveryRepository, never()).save(any())
    }

    @Test
    fun `sent delivery cannot be retried`() {
        val delivery = delivery(status = "SENT")
        whenever(deliveryRepository.findByEmailDeliveryId(delivery.emailDeliveryId)).thenReturn(delivery)

        assertThrows<IllegalArgumentException> {
            service.retryFailed(delivery.emailDeliveryId)
        }
        verify(deliveryRepository, never()).save(any())
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
