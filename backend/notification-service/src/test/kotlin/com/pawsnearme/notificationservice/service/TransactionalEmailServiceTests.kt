package com.pawsnearme.notificationservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.notificationservice.model.EmailDelivery
import com.pawsnearme.notificationservice.model.NotificationContact
import com.pawsnearme.notificationservice.repository.EmailDeliveryRepository
import com.pawsnearme.notificationservice.repository.NotificationContactRepository
import com.pawsnearme.notificationservice.repository.NotificationReferenceOwnerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.env.MockEnvironment
import java.util.Optional
import java.util.UUID

class TransactionalEmailServiceTests {
    private val deliveryRepository: EmailDeliveryRepository = mock()
    private val contactRepository: NotificationContactRepository = mock()
    private val ownerRepository: NotificationReferenceOwnerRepository = mock()
    private val msg91Provider: Msg91TransactionalEmailProvider = mock()
    private val brevoProvider: BrevoTransactionalEmailProvider = mock()
    private val objectMapper = ObjectMapper()

    private fun service(monthlyLimit: Long = 5000): TransactionalEmailService {
        val environment = MockEnvironment()
            .withProperty("notification.email.enabled", "true")
            .withProperty("notification.email.msg91.monthly-limit", monthlyLimit.toString())
            .withProperty("notification.email.max-attempts", "5")

        whenever(msg91Provider.providerName).thenReturn("MSG91")
        whenever(brevoProvider.providerName).thenReturn("BREVO")
        whenever(deliveryRepository.saveAndFlush(any())).thenAnswer { it.arguments[0] as EmailDelivery }
        whenever(deliveryRepository.save(any())).thenAnswer { it.arguments[0] as EmailDelivery }
        whenever(deliveryRepository.findById(any())).thenReturn(Optional.empty())
        whenever(deliveryRepository.countByProviderAndStatusAndSentAtGreaterThanEqual(eq("MSG91"), eq("SENT"), any()))
            .thenReturn(0)

        return TransactionalEmailService(
            deliveryRepository,
            contactRepository,
            ownerRepository,
            msg91Provider,
            brevoProvider,
            objectMapper,
            environment,
        )
    }

    private fun contact(userId: UUID) = NotificationContact(
        userId = userId,
        email = "customer@example.com",
        displayName = "Customer",
    )

    @Test
    fun `MSG91 is primary and accepted delivery is persisted as sent`() {
        val userId = UUID.randomUUID()
        whenever(contactRepository.findById(userId)).thenReturn(Optional.of(contact(userId)))
        whenever(deliveryRepository.findByIdempotencyKey("ORDER_PLACED:1")).thenReturn(null)
        whenever(msg91Provider.isConfigured("ORDER_PLACED")).thenReturn(true)
        whenever(msg91Provider.send(any())).thenReturn(
            EmailProviderResult("MSG91", accepted = true, providerMessageId = "msg-1")
        )

        val delivery = service().enqueueForUser(
            userId,
            "ORDER_PLACED",
            "ORDER_PLACED:1",
            mapOf("order_id" to "1"),
        )

        requireNotNull(delivery)
        assertEquals("SENT", delivery.status)
        assertEquals("MSG91", delivery.provider)
        assertEquals("msg-1", delivery.providerMessageId)
        verify(brevoProvider, never()).send(any())
    }

    @Test
    fun `definitive MSG91 quota or provider failure fails over to Brevo`() {
        val userId = UUID.randomUUID()
        whenever(contactRepository.findById(userId)).thenReturn(Optional.of(contact(userId)))
        whenever(deliveryRepository.findByIdempotencyKey("ORDER_DELIVERED:1")).thenReturn(null)
        whenever(msg91Provider.isConfigured("ORDER_DELIVERED")).thenReturn(true)
        whenever(brevoProvider.isConfigured("ORDER_DELIVERED")).thenReturn(true)
        whenever(msg91Provider.send(any())).thenReturn(
            EmailProviderResult(
                provider = "MSG91",
                accepted = false,
                safeToFailover = true,
                retryable = true,
                error = "MSG91 HTTP 429",
            )
        )
        whenever(brevoProvider.send(any())).thenReturn(
            EmailProviderResult("BREVO", accepted = true, providerMessageId = "brevo-1")
        )

        val delivery = service().enqueueForUser(
            userId,
            "ORDER_DELIVERED",
            "ORDER_DELIVERED:1",
            emptyMap(),
        )

        requireNotNull(delivery)
        assertEquals("SENT", delivery.status)
        assertEquals("BREVO", delivery.provider)
        assertEquals("brevo-1", delivery.providerMessageId)
        verify(msg91Provider).send(any())
        verify(brevoProvider).send(any())
    }

    @Test
    fun `ambiguous primary timeout is never duplicated through backup provider`() {
        val userId = UUID.randomUUID()
        whenever(contactRepository.findById(userId)).thenReturn(Optional.of(contact(userId)))
        whenever(deliveryRepository.findByIdempotencyKey("APPOINTMENT_BOOKED:1")).thenReturn(null)
        whenever(msg91Provider.isConfigured("APPOINTMENT_BOOKED")).thenReturn(true)
        whenever(brevoProvider.isConfigured("APPOINTMENT_BOOKED")).thenReturn(true)
        whenever(msg91Provider.send(any())).thenReturn(
            EmailProviderResult(
                provider = "MSG91",
                accepted = false,
                retryable = true,
                ambiguous = true,
                error = "timeout",
            )
        )

        val delivery = service().enqueueForUser(
            userId,
            "APPOINTMENT_BOOKED",
            "APPOINTMENT_BOOKED:1",
            emptyMap(),
        )

        requireNotNull(delivery)
        assertEquals("UNKNOWN", delivery.status)
        assertEquals("MSG91", delivery.provider)
        verify(brevoProvider, never()).send(any())
    }

    @Test
    fun `monthly MSG91 ceiling routes directly to Brevo`() {
        val userId = UUID.randomUUID()
        whenever(contactRepository.findById(userId)).thenReturn(Optional.of(contact(userId)))
        whenever(deliveryRepository.findByIdempotencyKey("ORDER_PLACED:quota")).thenReturn(null)
        whenever(msg91Provider.isConfigured("ORDER_PLACED")).thenReturn(true)
        whenever(brevoProvider.isConfigured("ORDER_PLACED")).thenReturn(true)
        whenever(brevoProvider.send(any())).thenReturn(
            EmailProviderResult("BREVO", accepted = true, providerMessageId = "brevo-quota")
        )

        val delivery = service(monthlyLimit = 0).enqueueForUser(
            userId,
            "ORDER_PLACED",
            "ORDER_PLACED:quota",
            emptyMap(),
        )

        requireNotNull(delivery)
        assertEquals("SENT", delivery.status)
        assertEquals("BREVO", delivery.provider)
        verify(msg91Provider, never()).send(any())
    }

    @Test
    fun `idempotency replay returns existing delivery without another provider call`() {
        val userId = UUID.randomUUID()
        val existing = EmailDelivery(
            idempotencyKey = "ORDER_PLACED:existing",
            userId = userId,
            recipientEmail = "customer@example.com",
            templateCode = "ORDER_PLACED",
            variablesJson = "{}",
            status = "SENT",
        )
        whenever(contactRepository.findById(userId)).thenReturn(Optional.of(contact(userId)))
        whenever(deliveryRepository.findByIdempotencyKey(existing.idempotencyKey)).thenReturn(existing)

        val delivery = service().enqueueForUser(
            userId,
            "ORDER_PLACED",
            existing.idempotencyKey,
            emptyMap(),
        )

        assertEquals(existing, delivery)
        verify(msg91Provider, never()).send(any())
        verify(brevoProvider, never()).send(any())
    }

    @Test
    fun `users without an email contact are skipped rather than failing order flow`() {
        val userId = UUID.randomUUID()
        whenever(contactRepository.findById(userId)).thenReturn(Optional.empty())

        val delivery = service().enqueueForUser(
            userId,
            "ORDER_PLACED",
            "ORDER_PLACED:no-email",
            emptyMap(),
        )

        assertNull(delivery)
        verify(msg91Provider, never()).send(any())
        verify(brevoProvider, never()).send(any())
    }
}
