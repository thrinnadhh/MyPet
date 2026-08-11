package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.AdminAuditLog
import com.pawsnearme.orderservice.model.Dispute
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.model.ServiceAreaConfig
import com.pawsnearme.orderservice.model.SupportCase
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
import org.mockito.kotlin.mock
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
    fun `snapshot reports active delayed failed and open work`() {
        val now = Instant.parse("2026-08-02T06:00:00Z")
        whenever(orderRepository.findAll()).thenReturn(
            listOf(
                order(OrderStatus.PREPARING, now.minus(3, ChronoUnit.HOURS), PaymentStatus.SUCCESS),
                order(OrderStatus.READY_FOR_PICKUP, now.minus(30, ChronoUnit.MINUTES), PaymentStatus.FAILED),
                order(OrderStatus.COMPLETED, now.minus(8, ChronoUnit.HOURS), PaymentStatus.SUCCESS)
            )
        )
        whenever(disputeRepository.findAll()).thenReturn(
            listOf(Dispute(orderId = UUID.randomUUID(), reason = "Damaged item"))
        )
        whenever(supportCaseRepository.findAllByOrderByCreatedAtDesc()).thenReturn(
            listOf(SupportCase(title = "Call customer", detail = "Callback", actionType = "CALLBACK"))
        )

        val snapshot = service.snapshot(now)

        assertEquals(2, snapshot.activeOrders)
        assertEquals(1, snapshot.delayedOrders)
        assertEquals(1, snapshot.failedPayments)
        assertEquals(1, snapshot.openDisputes)
        assertEquals(1, snapshot.openSupportCases)
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

    private fun order(status: OrderStatus, placedAt: Instant, paymentStatus: PaymentStatus) = Order(
        customerId = UUID.randomUUID(),
        providerId = UUID.randomUUID(),
        deliveryAddressId = UUID.randomUUID(),
        status = status,
        subtotalAmount = BigDecimal("100.00"),
        totalAmount = BigDecimal("100.00"),
        placedAt = placedAt,
        paymentStatus = paymentStatus
    )
}
