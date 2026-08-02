package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.AdminAuditLog
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.ServiceAreaConfig
import com.pawsnearme.orderservice.repository.AdminAuditLogRepository
import com.pawsnearme.orderservice.repository.DisputeRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.ServiceAreaConfigRepository
import com.pawsnearme.orderservice.repository.SupportCaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class AdminOperationsSnapshot(
    val activeOrders: Int,
    val delayedOrders: Int,
    val failedPayments: Int,
    val openDisputes: Int,
    val openSupportCases: Int,
    val generatedAt: Instant
)

data class ServiceAreaUpdateRequest(
    val city: String,
    val enabled: Boolean,
    val deliveryEnabled: Boolean,
    val serviceRadiusKm: BigDecimal,
    val emergencyMessage: String? = null,
    val reason: String
)

data class ServiceAreaView(
    val pincode: String,
    val city: String,
    val enabled: Boolean,
    val deliveryEnabled: Boolean,
    val serviceRadiusKm: BigDecimal,
    val emergencyMessage: String?,
    val updatedByUserId: UUID,
    val updatedAt: Instant
)

data class AdminAuditView(
    val auditId: UUID,
    val adminUserId: UUID,
    val action: String,
    val entityType: String,
    val entityId: String?,
    val previousValue: String?,
    val newValue: String?,
    val reason: String,
    val traceId: String,
    val createdAt: Instant
)

@Service
class AdminOperationsService(
    private val orderRepository: OrderRepository,
    private val disputeRepository: DisputeRepository,
    private val supportCaseRepository: SupportCaseRepository,
    private val auditRepository: AdminAuditLogRepository,
    private val serviceAreaRepository: ServiceAreaConfigRepository
) {
    private val activeOrderStatuses = setOf(
        OrderStatus.PLACED,
        OrderStatus.ACCEPTED,
        OrderStatus.PREPARING,
        OrderStatus.READY_FOR_PICKUP,
        OrderStatus.ASSIGNED,
        OrderStatus.REASSIGNED,
        OrderStatus.PICKED_UP
    )

    @Transactional(readOnly = true)
    fun snapshot(now: Instant = Instant.now()): AdminOperationsSnapshot {
        val orders = orderRepository.findAll()
        val active = orders.filter { it.status in activeOrderStatuses }
        val delayBoundary = now.minus(2, ChronoUnit.HOURS)
        return AdminOperationsSnapshot(
            activeOrders = active.size,
            delayedOrders = active.count { it.placedAt.isBefore(delayBoundary) },
            failedPayments = orders.count { it.paymentStatus.equals("FAILED", ignoreCase = true) },
            openDisputes = disputeRepository.findAll().count { it.status.equals("OPEN", ignoreCase = true) },
            openSupportCases = supportCaseRepository.findAllByOrderByCreatedAtDesc()
                .count { it.status.equals("OPEN", ignoreCase = true) },
            generatedAt = now
        )
    }

    @Transactional(readOnly = true)
    fun listServiceAreas(): List<ServiceAreaView> =
        serviceAreaRepository.findAllByOrderByCityAscPincodeAsc().map(::toView)

    @Transactional(readOnly = true)
    fun listAuditLogs(limit: Int): List<AdminAuditView> =
        auditRepository.findTop100ByOrderByCreatedAtDesc()
            .take(limit.coerceIn(1, 100))
            .map(::toView)

    @Transactional
    fun updateServiceArea(
        pincode: String,
        request: ServiceAreaUpdateRequest,
        actorId: UUID,
        traceId: String
    ): ServiceAreaView {
        require(PINCODE.matches(pincode)) { "Pincode must be a valid six-digit Indian pincode." }
        val city = request.city.trim()
        require(city.length in 2..120) { "City must contain between 2 and 120 characters." }
        require(request.serviceRadiusKm >= MIN_RADIUS && request.serviceRadiusKm <= MAX_RADIUS) {
            "Service radius must be between 0.50 and 100.00 km."
        }
        val reason = request.reason.trim()
        require(reason.length in 3..500) { "A reason between 3 and 500 characters is required." }

        val existing = serviceAreaRepository.findById(pincode).orElse(null)
        val previousValue = existing?.let { describe(it) }
        val saved = serviceAreaRepository.save(
            existing?.apply {
                this.city = city
                this.enabled = request.enabled
                this.deliveryEnabled = request.deliveryEnabled
                this.serviceRadiusKm = request.serviceRadiusKm.setScale(2, RoundingMode.HALF_UP)
                this.emergencyMessage = request.emergencyMessage?.trim()?.takeIf(String::isNotEmpty)
                this.updatedByUserId = actorId
                this.updatedAt = Instant.now()
            } ?: ServiceAreaConfig(
                pincode = pincode,
                city = city,
                enabled = request.enabled,
                deliveryEnabled = request.deliveryEnabled,
                serviceRadiusKm = request.serviceRadiusKm.setScale(2, RoundingMode.HALF_UP),
                emergencyMessage = request.emergencyMessage?.trim()?.takeIf(String::isNotEmpty),
                updatedByUserId = actorId
            )
        )

        auditRepository.save(
            AdminAuditLog(
                adminUserId = actorId,
                action = if (existing == null) "SERVICE_AREA_CREATED" else "SERVICE_AREA_UPDATED",
                entityType = "SERVICE_AREA",
                entityId = pincode,
                previousValue = previousValue,
                newValue = describe(saved),
                reason = reason,
                traceId = traceId.trim().take(160).ifBlank { UUID.randomUUID().toString() }
            )
        )
        return toView(saved)
    }

    private fun toView(config: ServiceAreaConfig) = ServiceAreaView(
        pincode = config.pincode,
        city = config.city,
        enabled = config.enabled,
        deliveryEnabled = config.deliveryEnabled,
        serviceRadiusKm = config.serviceRadiusKm,
        emergencyMessage = config.emergencyMessage,
        updatedByUserId = config.updatedByUserId,
        updatedAt = config.updatedAt
    )

    private fun toView(audit: AdminAuditLog) = AdminAuditView(
        auditId = audit.auditId,
        adminUserId = audit.adminUserId,
        action = audit.action,
        entityType = audit.entityType,
        entityId = audit.entityId,
        previousValue = audit.previousValue,
        newValue = audit.newValue,
        reason = audit.reason,
        traceId = audit.traceId,
        createdAt = audit.createdAt
    )

    private fun describe(config: ServiceAreaConfig): String = listOf(
        "pincode=${config.pincode}",
        "city=${config.city}",
        "enabled=${config.enabled}",
        "deliveryEnabled=${config.deliveryEnabled}",
        "serviceRadiusKm=${config.serviceRadiusKm}",
        "emergencyMessage=${config.emergencyMessage.orEmpty()}"
    ).joinToString(";")

    companion object {
        private val PINCODE = Regex("^[1-9][0-9]{5}$")
        private val MIN_RADIUS = BigDecimal("0.50")
        private val MAX_RADIUS = BigDecimal("100.00")
    }
}
