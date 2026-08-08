package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.AdminAuditLog
import com.pawsnearme.orderservice.model.Dispute
import com.pawsnearme.orderservice.model.SystemConfig
import com.pawsnearme.orderservice.repository.AdminAuditLogRepository
import com.pawsnearme.orderservice.repository.DisputeRepository
import com.pawsnearme.orderservice.repository.SupportCaseRepository
import com.pawsnearme.orderservice.repository.SystemConfigRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AdminControlPlaneServiceTests {
    private val disputeRepository: DisputeRepository = mock()
    private val supportCaseRepository: SupportCaseRepository = mock()
    private val systemConfigRepository: SystemConfigRepository = mock()
    private val paymentModule: PaymentModuleApi = mock()
    private val auditRepository: AdminAuditLogRepository = mock()
    private val outboxService: OutboxService = mock()
    private val service = AdminControlPlaneService(
        disputeRepository,
        supportCaseRepository,
        systemConfigRepository,
        paymentModule,
        auditRepository,
        outboxService
    )

    @Test
    fun `automated dispute resolution refunds before committing resolved state and audits actor`() {
        val disputeId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val actorId = UUID.randomUUID()
        val dispute = Dispute(
            disputeId = disputeId,
            orderId = orderId,
            status = "OPEN",
            reason = "Order was not fulfilled",
            createdAt = Instant.now()
        )
        whenever(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute))
        whenever(systemConfigRepository.findById("dispute_refund_mode"))
            .thenReturn(Optional.of(SystemConfig("dispute_refund_mode", "AUTOMATED")))
        whenever(disputeRepository.save(any<Dispute>())).thenAnswer { it.getArgument(0) }
        whenever(auditRepository.save(any<AdminAuditLog>())).thenAnswer { it.getArgument(0) }
        val audit = argumentCaptor<AdminAuditLog>()

        val result = service.resolveDispute(
            disputeId = disputeId,
            requestedDecision = "resolved",
            resolutionNotes = "Evidence confirmed non-fulfilment; refund approved.",
            actorId = actorId,
            traceId = "trace-admin-refund"
        )

        assertEquals("RESOLVED", result.status)
        verify(paymentModule).refundOrder(orderId)
        verify(disputeRepository).save(dispute)
        verify(auditRepository).save(audit.capture())
        assertEquals(actorId, audit.firstValue.adminUserId)
        assertEquals("DISPUTE_DECIDED", audit.firstValue.action)
        assertEquals("OPEN", audit.firstValue.previousValue)
        assertEquals("RESOLVED", audit.firstValue.newValue)
        assertEquals("trace-admin-refund", audit.firstValue.traceId)
        verify(outboxService).saveEvent(
            eventId = any(),
            aggregateType = eq("ADMIN_OPERATION"),
            aggregateId = eq(disputeId),
            eventType = eq("DisputeDecided"),
            eventPayload = any()
        )
    }

    @Test
    fun `automated refund failure leaves dispute open and creates no success audit`() {
        val disputeId = UUID.randomUUID()
        val orderId = UUID.randomUUID()
        val dispute = Dispute(
            disputeId = disputeId,
            orderId = orderId,
            status = "OPEN",
            reason = "Payment dispute",
            createdAt = Instant.now()
        )
        whenever(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute))
        whenever(systemConfigRepository.findById("dispute_refund_mode"))
            .thenReturn(Optional.of(SystemConfig("dispute_refund_mode", "AUTOMATED")))
        doThrow(IllegalStateException("payment provider unavailable"))
            .whenever(paymentModule).refundOrder(orderId)

        val error = assertThrows<IllegalStateException> {
            service.resolveDispute(
                disputeId,
                "RESOLVED",
                "Refund is required before resolution can commit.",
                UUID.randomUUID(),
                "trace-failure"
            )
        }

        assertTrue(error.message!!.contains("payment provider unavailable"))
        assertEquals("OPEN", dispute.status)
        verify(disputeRepository, never()).save(any<Dispute>())
        verify(auditRepository, never()).save(any<AdminAuditLog>())
        verify(outboxService, never()).saveEvent(any(), any(), any(), any(), any())
    }

    @Test
    fun `duplicate decision after row lock is rejected without second refund`() {
        val disputeId = UUID.randomUUID()
        val dispute = Dispute(
            disputeId = disputeId,
            orderId = UUID.randomUUID(),
            status = "RESOLVED",
            reason = "Already handled",
            resolutionNotes = "Refund completed",
            resolvedAt = Instant.now(),
            createdAt = Instant.now()
        )
        whenever(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute))

        assertThrows<IllegalStateException> {
            service.resolveDispute(
                disputeId,
                "RESOLVED",
                "Attempted duplicate resolution",
                UUID.randomUUID(),
                "trace-duplicate"
            )
        }

        verify(paymentModule, never()).refundOrder(any())
        verify(disputeRepository, never()).save(any<Dispute>())
    }

    @Test
    fun `invalid decision is rejected before database lookup`() {
        assertThrows<IllegalArgumentException> {
            service.resolveDispute(
                UUID.randomUUID(),
                "APPROVE_ANYTHING",
                "Invalid state mutation attempt",
                UUID.randomUUID(),
                "trace-invalid"
            )
        }
        verify(disputeRepository, never()).findByIdForUpdate(any())
    }

    @Test
    fun `refund policy change records before and after with actor and trace`() {
        val actorId = UUID.randomUUID()
        val config = SystemConfig("dispute_refund_mode", "MANUAL")
        whenever(systemConfigRepository.findById("dispute_refund_mode")).thenReturn(Optional.of(config))
        whenever(systemConfigRepository.save(any<SystemConfig>())).thenAnswer { it.getArgument(0) }
        whenever(auditRepository.save(any<AdminAuditLog>())).thenAnswer { it.getArgument(0) }
        val audit = argumentCaptor<AdminAuditLog>()

        val result = service.updateDisputeRefundMode(
            requestedMode = "AUTOMATED",
            actorId = actorId,
            reason = "Enable verified automated dispute refunds",
            traceId = "trace-policy"
        )

        assertEquals("AUTOMATED", result)
        verify(auditRepository).save(audit.capture())
        assertEquals("MANUAL", audit.firstValue.previousValue)
        assertEquals("AUTOMATED", audit.firstValue.newValue)
        assertEquals(actorId, audit.firstValue.adminUserId)
        assertEquals("trace-policy", audit.firstValue.traceId)
    }

    @Test
    fun `long dispute notes remain intact while audit reason respects column limit`() {
        val disputeId = UUID.randomUUID()
        val notes = "A".repeat(1200)
        val dispute = Dispute(
            disputeId = disputeId,
            orderId = UUID.randomUUID(),
            status = "OPEN",
            reason = "Detailed dispute",
            createdAt = Instant.now()
        )
        whenever(disputeRepository.findByIdForUpdate(disputeId)).thenReturn(Optional.of(dispute))
        whenever(systemConfigRepository.findById("dispute_refund_mode")).thenReturn(Optional.empty())
        whenever(disputeRepository.save(any<Dispute>())).thenAnswer { it.getArgument(0) }
        whenever(auditRepository.save(any<AdminAuditLog>())).thenAnswer { it.getArgument(0) }
        val audit = argumentCaptor<AdminAuditLog>()

        service.resolveDispute(disputeId, "REJECTED", notes, UUID.randomUUID(), "trace-long-notes")

        assertEquals(notes, dispute.resolutionNotes)
        verify(auditRepository).save(audit.capture())
        assertEquals(500, audit.firstValue.reason.length)
    }
}
