package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.AdminAuditLog
import com.pawsnearme.orderservice.model.ServiceAreaConfig
import com.pawsnearme.orderservice.repository.AdminAuditLogRepository
import com.pawsnearme.orderservice.repository.DisputeRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.ServiceAreaConfigRepository
import com.pawsnearme.orderservice.repository.SupportCaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class AdminOperationsServiceTests {
    private lateinit var orderRepository: OrderRepository
    private lateinit var disputeRepository: DisputeRepository
    private lateinit var supportCaseRepository: SupportCaseRepository
    private lateinit var auditRepository: AdminAuditLogRepository
    private lateinit var serviceAreaRepository: ServiceAreaConfigRepository
    private lateinit var service: AdminOperationsService

    @BeforeEach
    fun setUp() {
        orderRepository = mock()
        disputeRepository = mock()
        supportCaseRepository = mock()
        auditRepository = mock()
        serviceAreaRepository = mock()
        service = AdminOperationsService(
            orderRepository,
            disputeRepository,
            supportCaseRepository,
            auditRepository,
            serviceAreaRepository
        )
    }

    @Test
    fun `snapshot uses bounded database counts for operational work`() {
        val now = Instant.parse("2026-08-02T06:00:00Z")
        val delayedBefore = now.minus(2, ChronoUnit.HOURS)
        whenever(orderRepository.countByStatusIn(any())).thenReturn(2L)
        whenever(orderRepository.countByStatusInAndPlacedAtBefore(any(), eq(delayedBefore))).thenReturn(1L)
        whenever(orderRepository.countByPaymentStatusIgnoreCase("FAILED")).thenReturn(1L)
        whenever(disputeRepository.countByStatusIgnoreCase("OPEN")).thenReturn(3L)
        whenever(supportCaseRepository.countByStatusIgnoreCase("OPEN")).thenReturn(4L)

        val snapshot = service.snapshot(now)

        assertEquals(2L, snapshot.activeOrders)
        assertEquals(1L, snapshot.delayedOrders)
        assertEquals(1L, snapshot.failedPayments)
        assertEquals(3L, snapshot.openDisputes)
        assertEquals(4L, snapshot.openSupportCases)
        assertEquals(now, snapshot.generatedAt)
        verify(orderRepository, never()).findAll()
        verify(disputeRepository, never()).findAll()
        verify(supportCaseRepository, never()).findAllByOrderByCreatedAtDesc()
    }

    @Test
    fun `service area update validates input and creates immutable audit record`() {
        val actorId = UUID.randomUUID()
        whenever(serviceAreaRepository.findById("517501")).thenReturn(Optional.empty())
        whenever(serviceAreaRepository.save(any<ServiceAreaConfig>())).thenAnswer { it.getArgument(0) }
        whenever(auditRepository.save(any<AdminAuditLog>())).thenAnswer { it.getArgument(0) }

        val result = service.updateServiceArea(
            pincode = "517501",
            request = ServiceAreaUpdateRequest(
                city = "Tirupati",
                enabled = true,
                deliveryEnabled = true,
                serviceRadiusKm = BigDecimal("8"),
                emergencyMessage = "Weather delays possible",
                reason = "Enable controlled Tirupati pilot"
            ),
            actorId = actorId,
            traceId = "trace-admin-7"
        )

        assertEquals("Tirupati", result.city)
        assertEquals(BigDecimal("8.00"), result.serviceRadiusKm)
        val audit = argumentCaptor<AdminAuditLog>()
        verify(auditRepository).save(audit.capture())
        assertEquals("SERVICE_AREA_CREATED", audit.firstValue.action)
        assertEquals("517501", audit.firstValue.entityId)
        assertEquals("trace-admin-7", audit.firstValue.traceId)
        assertTrue(audit.firstValue.newValue!!.contains("Tirupati"))
    }

    @Test
    fun `service area update rejects malformed pincode`() {
        assertThrows<IllegalArgumentException> {
            service.updateServiceArea(
                pincode = "123",
                request = ServiceAreaUpdateRequest(
                    city = "Tirupati",
                    enabled = true,
                    deliveryEnabled = true,
                    serviceRadiusKm = BigDecimal("5"),
                    reason = "Pilot"
                ),
                actorId = UUID.randomUUID(),
                traceId = "trace"
            )
        }
    }
}
