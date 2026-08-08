package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.CatalogModerationAuditLog
import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.model.OfferingStatus
import com.pawsnearme.catalogservice.repository.CatalogModerationAuditLogRepository
import com.pawsnearme.catalogservice.repository.OfferingRepository
import com.pawsnearme.common.outbox.OutboxService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class CatalogAdminModerationServiceTests {
    private val offeringRepository: OfferingRepository = mock()
    private val auditRepository: CatalogModerationAuditLogRepository = mock()
    private val outboxService: OutboxService = mock()
    private val service = CatalogAdminModerationService(offeringRepository, auditRepository, outboxService)

    @Test
    fun `admin disable persists lock reason actor audit and outbox event`() {
        val offeringId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val offering = offering(offeringId)
        whenever(offeringRepository.findByIdForUpdate(offeringId)).thenReturn(Optional.of(offering))
        whenever(offeringRepository.save(any<Offering>())).thenAnswer { it.getArgument(0) }
        whenever(auditRepository.save(any<CatalogModerationAuditLog>())).thenAnswer { it.getArgument(0) }
        val audit = argumentCaptor<CatalogModerationAuditLog>()

        val result = service.disable(offeringId, actorId, "Prohibited listing content")

        assertTrue(result.adminDisabled)
        assertEquals(OfferingStatus.INACTIVE, result.status)
        assertEquals("Prohibited listing content", result.moderationReason)
        assertEquals(actorId, result.moderatedByUserId)
        verify(auditRepository).save(audit.capture())
        assertEquals(actorId, audit.firstValue.adminUserId)
        assertEquals("OFFERING_DISABLED", audit.firstValue.action)
        assertEquals("ACTIVE", audit.firstValue.previousStatus)
        assertEquals("INACTIVE", audit.firstValue.newStatus)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("OFFERING"),
            aggregateId = eq(offeringId),
            eventType = eq("OfferingModerated"),
            eventPayload = any()
        )
    }

    @Test
    fun `admin restore clears lock and records restoration`() {
        val offeringId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val offering = offering(offeringId).apply {
            adminDisabled = true
            status = OfferingStatus.INACTIVE
            moderationReason = "Policy review"
        }
        whenever(offeringRepository.findByIdForUpdate(offeringId)).thenReturn(Optional.of(offering))
        whenever(offeringRepository.save(any<Offering>())).thenAnswer { it.getArgument(0) }
        whenever(auditRepository.save(any<CatalogModerationAuditLog>())).thenAnswer { it.getArgument(0) }

        val result = service.restore(offeringId, actorId, "Listing remediated")

        assertFalse(result.adminDisabled)
        assertEquals(OfferingStatus.ACTIVE, result.status)
        assertEquals(null, result.moderationReason)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("OFFERING"),
            aggregateId = eq(offeringId),
            eventType = eq("OfferingRestored"),
            eventPayload = any()
        )
    }

    @Test
    fun `duplicate disable is rejected without second audit`() {
        val offeringId = UUID.randomUUID()
        val offering = offering(offeringId).apply { adminDisabled = true }
        whenever(offeringRepository.findByIdForUpdate(offeringId)).thenReturn(Optional.of(offering))

        assertThrows<IllegalStateException> {
            service.disable(offeringId, UUID.randomUUID(), "Duplicate moderation attempt")
        }

        verify(offeringRepository, never()).save(any<Offering>())
        verify(auditRepository, never()).save(any<CatalogModerationAuditLog>())
        verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any())
    }

    private fun offering(id: UUID) = Offering(
        offeringId = id,
        providerId = UUID.randomUUID(),
        name = "Dog Food",
        price = BigDecimal("499.00"),
        stockQuantity = 10,
        status = OfferingStatus.ACTIVE
    )
}
