package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class MerchantOrderItemView(
    val offeringId: UUID,
    val name: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val lineTotal: BigDecimal,
)

data class MerchantOrderView(
    val orderId: UUID,
    val customerId: UUID,
    val providerId: UUID,
    val captainId: UUID?,
    val deliveryAddressId: UUID,
    val deliveryContactPhone: String?,
    val deliveryContactVerified: Boolean,
    val status: OrderStatus,
    val subtotalAmount: BigDecimal,
    val deliveryFee: BigDecimal,
    val discountAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val couponCode: String?,
    val paymentId: UUID?,
    val paymentMethod: String,
    val paymentStatus: String,
    val placedAt: Instant,
    val acceptedAt: Instant?,
    val readyAt: Instant?,
    val pickedUpAt: Instant?,
    val deliveredAt: Instant?,
    val cancelledAt: Instant?,
    val cancellationReason: String?,
    val items: List<MerchantOrderItemView>,
)

@Service
class MerchantOrderQueryService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val providerModule: ProviderModuleApi,
) {
    @Transactional(readOnly = true)
    fun listProviderOrders(
        providerId: UUID,
        callerId: UUID,
        callerRole: String?,
    ): List<MerchantOrderView> {
        val role = callerRole?.trim()?.uppercase()
        val allowed = role == "ADMIN" ||
            (role == "MERCHANT" && providerModule.ownerUserId(providerId) == callerId)
        if (!allowed) throw OrderAccessDeniedException("Access denied to provider orders.")

        return orderRepository.findByProviderId(providerId)
            .sortedByDescending(Order::placedAt)
            .map(::toView)
    }

    private fun toView(order: Order): MerchantOrderView {
        val orderId = requireNotNull(order.orderId)
        val items = orderItemRepository.findByOrderId(orderId).map { item ->
            MerchantOrderItemView(
                offeringId = item.offeringId,
                name = item.offeringNameSnapshot,
                unitPrice = item.unitPriceSnapshot,
                quantity = item.quantity,
                lineTotal = item.lineTotal,
            )
        }
        return MerchantOrderView(
            orderId = orderId,
            customerId = order.customerId,
            providerId = order.providerId,
            captainId = order.captainId,
            deliveryAddressId = order.deliveryAddressId,
            deliveryContactPhone = order.deliveryContactPhone,
            deliveryContactVerified = order.deliveryContactVerified,
            status = order.status,
            subtotalAmount = order.subtotalAmount,
            deliveryFee = order.deliveryFee,
            discountAmount = order.discountAmount,
            taxAmount = order.taxAmount,
            totalAmount = order.totalAmount,
            couponCode = order.couponCode,
            paymentId = order.paymentId,
            paymentMethod = order.paymentMethod,
            paymentStatus = order.paymentStatus,
            placedAt = order.placedAt,
            acceptedAt = order.acceptedAt,
            readyAt = order.readyAt,
            pickedUpAt = order.picked_upAt,
            deliveredAt = order.deliveredAt,
            cancelledAt = order.cancelledAt,
            cancellationReason = order.cancellationReason,
            items = items,
        )
    }
}
