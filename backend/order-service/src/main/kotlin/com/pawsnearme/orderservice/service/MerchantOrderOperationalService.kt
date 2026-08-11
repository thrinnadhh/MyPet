package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.OrderStatusHistoryRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class MerchantDeliveryAddressView(
    val label: String?,
    val line1: String,
    val line2: String?,
    val city: String,
    val state: String,
    val pincode: String,
    val latitude: Double,
    val longitude: Double,
)

data class MerchantOrderItemView(
    val orderItemId: UUID,
    val offeringId: UUID,
    val name: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val lineTotal: BigDecimal,
)

data class MerchantOrderHistoryView(
    val fromStatus: OrderStatus?,
    val toStatus: OrderStatus,
    val changedAt: Instant,
    val actorId: UUID?,
    val note: String?,
)

data class MerchantOrderDetailView(
    val orderId: UUID,
    val customerId: UUID,
    val customerName: String?,
    val deliveryAddressId: UUID,
    val deliveryAddress: MerchantDeliveryAddressView,
    val contactPhone: String?,
    val contactVerified: Boolean,
    val items: List<MerchantOrderItemView>,
    val paymentMethod: String,
    val paymentStatus: PaymentStatus,
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val delivery: BigDecimal,
    val tax: BigDecimal,
    val total: BigDecimal,
    val placedAt: Instant,
    val acceptedAt: Instant?,
    val preparingAt: Instant?,
    val readyAt: Instant?,
    val status: OrderStatus,
    val history: List<MerchantOrderHistoryView>,
)

@Service
class MerchantOrderOperationalService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val historyRepository: OrderStatusHistoryRepository,
    private val providerModule: ProviderModuleApi,
    private val entityManager: EntityManager,
) {
    @Transactional(readOnly = true)
    fun detail(orderId: UUID, merchantUserId: UUID): MerchantOrderDetailView {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        if (providerModule.ownerUserId(order.providerId) != merchantUserId) {
            throw OrderAccessDeniedException("Access denied to merchant operational order detail.")
        }

        val customerAndAddress = loadCustomerAndAddress(order.customerId, order.deliveryAddressId)
        val history = historyRepository.findByOrderId(orderId)
            .sortedBy { it.changedAt }
            .map {
                MerchantOrderHistoryView(
                    fromStatus = it.fromStatus,
                    toStatus = it.toStatus,
                    changedAt = it.changedAt,
                    actorId = it.changedByUserId,
                    note = it.note,
                )
            }
        val preparingAt = order.preparingAt
            ?: history.firstOrNull { it.toStatus == OrderStatus.PREPARING }?.changedAt

        return MerchantOrderDetailView(
            orderId = requireNotNull(order.orderId),
            customerId = order.customerId,
            customerName = customerAndAddress.first,
            deliveryAddressId = order.deliveryAddressId,
            deliveryAddress = customerAndAddress.second,
            contactPhone = order.deliveryContactPhone,
            contactVerified = order.deliveryContactVerified,
            items = orderItemRepository.findByOrderId(orderId).map { item ->
                MerchantOrderItemView(
                    orderItemId = requireNotNull(item.orderItemId),
                    offeringId = item.offeringId,
                    name = item.offeringNameSnapshot,
                    unitPrice = item.unitPriceSnapshot,
                    quantity = item.quantity,
                    lineTotal = item.lineTotal,
                )
            },
            paymentMethod = order.paymentMethod,
            paymentStatus = order.paymentStatus,
            subtotal = order.subtotalAmount,
            discount = order.discountAmount,
            delivery = order.deliveryFee,
            tax = order.taxAmount,
            total = order.totalAmount,
            placedAt = order.placedAt,
            acceptedAt = order.acceptedAt,
            preparingAt = preparingAt,
            readyAt = order.readyAt,
            status = order.status,
            history = history,
        )
    }

    private fun loadCustomerAndAddress(
        customerId: UUID,
        addressId: UUID,
    ): Pair<String?, MerchantDeliveryAddressView> {
        val rows = entityManager.createNativeQuery(
            """
                SELECT p.full_name,
                       a.label,
                       a.line1,
                       a.line2,
                       a.city,
                       a.state,
                       a.pincode,
                       a.geo_lat,
                       a.geo_lng
                FROM identity.addresses a
                LEFT JOIN identity.profiles p ON p.user_id = a.user_id
                WHERE a.address_id = :addressId
                  AND a.user_id = :customerId
                LIMIT 1
            """.trimIndent()
        )
            .setParameter("addressId", addressId)
            .setParameter("customerId", customerId)
            .resultList

        val row = rows.firstOrNull() as? Array<*>
            ?: throw IllegalStateException("The order delivery address is no longer available")
        return (row[0] as? String) to MerchantDeliveryAddressView(
            label = row[1] as? String,
            line1 = row[2] as String,
            line2 = row[3] as? String,
            city = row[4] as String,
            state = row[5] as String,
            pincode = row[6] as String,
            latitude = (row[7] as Number).toDouble(),
            longitude = (row[8] as Number).toDouble(),
        )
    }
}
