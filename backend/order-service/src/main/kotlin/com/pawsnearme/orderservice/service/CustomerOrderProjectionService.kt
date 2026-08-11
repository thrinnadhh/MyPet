package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.InvoiceRepository
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.OrderStatusHistoryRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class CustomerProviderView(
    val providerId: UUID,
    val name: String,
    val providerType: String,
)

data class CustomerOrderItemView(
    val orderItemId: UUID,
    val offeringId: UUID,
    val name: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val lineTotal: BigDecimal,
)

data class CustomerOrderPricingView(
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val loyaltyDiscount: BigDecimal,
    val delivery: BigDecimal,
    val tax: BigDecimal,
    val total: BigDecimal,
)

data class CustomerOrderPaymentView(
    val method: String,
    val status: PaymentStatus,
    val paymentId: UUID?,
)

data class CustomerOrderDeliveryAddressView(
    val addressId: UUID,
    val label: String?,
    val line1: String,
    val line2: String?,
    val city: String,
    val state: String,
    val pincode: String,
    val latitude: Double,
    val longitude: Double,
)

data class CustomerOrderDeliveryContactView(
    val phone: String?,
    val verified: Boolean,
)

data class CustomerOrderCaptainView(
    val captainId: UUID,
    val assignedAt: Instant?,
)

data class CustomerOrderTimestampsView(
    val placedAt: Instant,
    val acceptedAt: Instant?,
    val preparingAt: Instant?,
    val readyAt: Instant?,
    val pickedUpAt: Instant?,
    val deliveredAt: Instant?,
    val cancelledAt: Instant?,
)

data class CustomerOrderCancellationView(
    val cancelled: Boolean,
    val reason: String?,
    val cancelledAt: Instant?,
)

data class CustomerOrderInvoiceView(
    val invoiceId: UUID,
    val invoiceNumber: String,
    val subtotal: BigDecimal,
    val tax: BigDecimal,
    val total: BigDecimal,
    val generatedAt: Instant,
)

data class CustomerOrderDetailResponse(
    val orderId: UUID,
    val provider: CustomerProviderView,
    val items: List<CustomerOrderItemView>,
    val pricing: CustomerOrderPricingView,
    val payment: CustomerOrderPaymentView,
    val status: OrderStatus,
    val flowStep: String,
    val statusHistory: List<OrderStatusHistoryEntry>,
    val deliveryAddress: CustomerOrderDeliveryAddressView,
    val deliveryContact: CustomerOrderDeliveryContactView,
    val captain: CustomerOrderCaptainView?,
    val timestamps: CustomerOrderTimestampsView,
    val cancellation: CustomerOrderCancellationView,
    val invoice: CustomerOrderInvoiceView?,
)

data class CustomerOrderTrackingResponse(
    val orderId: UUID,
    val providerId: UUID,
    val providerName: String,
    val status: OrderStatus,
    val flowStep: String,
    val totalAmount: BigDecimal,
    val placedAt: Instant,
    val items: List<String>,
    val paymentMethod: String,
    val paymentStatus: PaymentStatus,
    val captain: CustomerOrderCaptainView?,
    val etaMinutes: Int?,
    val deliveryStatus: String?,
    val statusHistory: List<OrderStatusHistoryEntry>,
)

private data class ProviderProjection(
    val view: CustomerProviderView,
    val latitude: Double,
    val longitude: Double,
)

private data class DispatchProjection(
    val status: String,
    val captainId: UUID?,
    val assignedAt: Instant?,
)

@Service
class CustomerOrderProjectionService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val historyRepository: OrderStatusHistoryRepository,
    private val invoiceRepository: InvoiceRepository,
    private val entityManager: EntityManager,
) {
    @Transactional(readOnly = true)
    fun detail(orderId: UUID, callerId: UUID, callerRole: String?): CustomerOrderDetailResponse {
        val order = ownedOrder(orderId, callerId, callerRole)
        return detail(order)
    }

    @Transactional(readOnly = true)
    fun tracking(customerId: UUID, callerId: UUID, callerRole: String?): List<CustomerOrderTrackingResponse> {
        if (!callerRole.equals("ADMIN", ignoreCase = true) && customerId != callerId) {
            throw OrderAccessDeniedException("Access denied to customer order tracking.")
        }
        return orderRepository.findByCustomerId(customerId)
            .sortedByDescending { it.placedAt }
            .map(::tracking)
    }

    private fun detail(order: Order): CustomerOrderDetailResponse {
        val orderId = requireNotNull(order.orderId)
        val provider = provider(order.providerId)
        val address = address(order.customerId, order.deliveryAddressId)
        val dispatch = dispatch(orderId)
        val history = history(orderId)
        val captainId = order.captainId ?: dispatch?.captainId
        val invoice = invoiceRepository.findByOrderId(orderId).orElse(null)
        return CustomerOrderDetailResponse(
            orderId = orderId,
            provider = provider.view,
            items = orderItemRepository.findByOrderId(orderId).map { item ->
                CustomerOrderItemView(
                    orderItemId = requireNotNull(item.orderItemId),
                    offeringId = item.offeringId,
                    name = item.offeringNameSnapshot,
                    unitPrice = item.unitPriceSnapshot,
                    quantity = item.quantity,
                    lineTotal = item.lineTotal,
                )
            },
            pricing = CustomerOrderPricingView(
                subtotal = order.subtotalAmount,
                discount = order.discountAmount,
                loyaltyDiscount = order.loyaltyDiscountAmount,
                delivery = order.deliveryFee,
                tax = order.taxAmount,
                total = order.totalAmount,
            ),
            payment = CustomerOrderPaymentView(order.paymentMethod, order.paymentStatus, order.paymentId),
            status = order.status,
            flowStep = flowStep(order.status),
            statusHistory = history,
            deliveryAddress = address,
            deliveryContact = CustomerOrderDeliveryContactView(order.deliveryContactPhone, order.deliveryContactVerified),
            captain = captainId?.let { CustomerOrderCaptainView(it, dispatch?.assignedAt) },
            timestamps = CustomerOrderTimestampsView(
                placedAt = order.placedAt,
                acceptedAt = order.acceptedAt,
                preparingAt = order.preparingAt ?: history.firstOrNull { it.toStatus == OrderStatus.PREPARING }?.changedAt,
                readyAt = order.readyAt,
                pickedUpAt = order.picked_upAt,
                deliveredAt = order.deliveredAt,
                cancelledAt = order.cancelledAt,
            ),
            cancellation = CustomerOrderCancellationView(
                cancelled = order.status == OrderStatus.CANCELLED,
                reason = order.cancellationReason,
                cancelledAt = order.cancelledAt,
            ),
            invoice = invoice?.let {
                CustomerOrderInvoiceView(
                    invoiceId = requireNotNull(it.invoiceId),
                    invoiceNumber = it.invoiceNumber,
                    subtotal = it.subtotalAmount,
                    tax = it.taxAmount,
                    total = it.totalAmount,
                    generatedAt = it.generatedAt,
                )
            },
        )
    }

    private fun tracking(order: Order): CustomerOrderTrackingResponse {
        val orderId = requireNotNull(order.orderId)
        val provider = provider(order.providerId)
        val address = address(order.customerId, order.deliveryAddressId)
        val dispatch = dispatch(orderId)
        val captainId = order.captainId ?: dispatch?.captainId
        val eta = if (order.status in ETA_STATUSES) {
            estimateDeliveryMinutes(provider.latitude, provider.longitude, address.latitude, address.longitude)
        } else null
        return CustomerOrderTrackingResponse(
            orderId = orderId,
            providerId = order.providerId,
            providerName = provider.view.name,
            status = order.status,
            flowStep = flowStep(order.status),
            totalAmount = order.totalAmount,
            placedAt = order.placedAt,
            items = orderItemRepository.findByOrderId(orderId).map { it.offeringNameSnapshot },
            paymentMethod = order.paymentMethod,
            paymentStatus = order.paymentStatus,
            captain = captainId?.let { CustomerOrderCaptainView(it, dispatch?.assignedAt) },
            etaMinutes = eta,
            deliveryStatus = dispatch?.status,
            statusHistory = history(orderId),
        )
    }

    private fun ownedOrder(orderId: UUID, callerId: UUID, callerRole: String?): Order {
        val order = orderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order with ID $orderId not found") }
        val isAdmin = callerRole.equals("ADMIN", ignoreCase = true)
        if (!isAdmin && order.customerId != callerId) {
            throw OrderAccessDeniedException("Customer order detail is available only to the order customer or an administrator.")
        }
        return order
    }

    private fun provider(providerId: UUID): ProviderProjection {
        val row = entityManager.createNativeQuery(
            """
                SELECT provider_id, name, provider_type, ST_Y(geo_location), ST_X(geo_location)
                FROM providers.providers
                WHERE provider_id = :providerId
                LIMIT 1
            """.trimIndent()
        ).setParameter("providerId", providerId).resultList.firstOrNull() as? Array<*>
            ?: throw IllegalStateException("Provider $providerId is no longer available")
        return ProviderProjection(
            view = CustomerProviderView(
                providerId = row[0] as UUID,
                name = row[1] as String,
                providerType = row[2].toString(),
            ),
            latitude = (row[3] as Number).toDouble(),
            longitude = (row[4] as Number).toDouble(),
        )
    }

    private fun address(customerId: UUID, addressId: UUID): CustomerOrderDeliveryAddressView {
        val row = entityManager.createNativeQuery(
            """
                SELECT address_id, label, line1, line2, city, state, pincode, geo_lat, geo_lng
                FROM identity.addresses
                WHERE address_id = :addressId AND user_id = :customerId
                LIMIT 1
            """.trimIndent()
        )
            .setParameter("addressId", addressId)
            .setParameter("customerId", customerId)
            .resultList.firstOrNull() as? Array<*>
            ?: throw IllegalStateException("The order delivery address is no longer available")
        return CustomerOrderDeliveryAddressView(
            addressId = row[0] as UUID,
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

    private fun dispatch(orderId: UUID): DispatchProjection? {
        val row = entityManager.createNativeQuery(
            """
                SELECT j.status, o.captain_id, o.responded_at
                FROM dispatch.dispatch_jobs j
                LEFT JOIN dispatch.dispatch_offers o
                  ON o.job_id = j.job_id AND o.response = 'ACCEPTED'
                WHERE j.order_id = :orderId
                ORDER BY o.responded_at DESC NULLS LAST
                LIMIT 1
            """.trimIndent()
        ).setParameter("orderId", orderId).resultList.firstOrNull() as? Array<*> ?: return null
        return DispatchProjection(
            status = row[0].toString(),
            captainId = row[1] as? UUID,
            assignedAt = row[2] as? Instant,
        )
    }

    private fun history(orderId: UUID): List<OrderStatusHistoryEntry> =
        historyRepository.findByOrderId(orderId)
            .sortedBy { it.changedAt }
            .map { OrderStatusHistoryEntry(it.fromStatus, it.toStatus, it.changedAt, it.note) }

    private fun flowStep(status: OrderStatus): String = status.name.lowercase()

    private fun estimateDeliveryMinutes(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Int {
        val routeKm = haversineKm(fromLat, fromLng, toLat, toLng) * ROUTE_FACTOR
        return ((routeKm / AVERAGE_DELIVERY_KMH) * 60.0).toInt().coerceAtLeast(MIN_ETA_MINUTES)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    companion object {
        private val ETA_STATUSES = setOf(
            OrderStatus.READY_FOR_PICKUP,
            OrderStatus.ASSIGNED,
            OrderStatus.PICKED_UP,
        )
        private const val EARTH_RADIUS_KM = 6371.0
        private const val ROUTE_FACTOR = 1.25
        private const val AVERAGE_DELIVERY_KMH = 25.0
        private const val MIN_ETA_MINUTES = 3
    }
}
