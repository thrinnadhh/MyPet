package com.pawsnearme.orderservice.service

import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.CouponReservationCommand
import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.PrepareOrderPaymentCommand
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.module.StockMutationCommand
import com.pawsnearme.common.outbox.OutboxService
import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.model.OrderItem
import com.pawsnearme.orderservice.model.OrderStatus
import com.pawsnearme.orderservice.model.OrderStatusHistory
import com.pawsnearme.orderservice.model.PaymentStatus
import com.pawsnearme.orderservice.repository.OrderItemRepository
import com.pawsnearme.orderservice.repository.OrderRepository
import com.pawsnearme.orderservice.repository.OrderStatusHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class CheckoutIntegrityService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderStatusHistoryRepository: OrderStatusHistoryRepository,
    private val catalogModule: CatalogModuleApi,
    private val paymentModule: PaymentModuleApi,
    private val providerModule: ProviderModuleApi,
    private val discoveryModule: DiscoveryModuleApi,
    private val quoteStore: QuoteStore,
    private val outboxService: OutboxService,
    private val compensationService: OrderCompensationService,
    private val onlinePaymentsEnabled: Boolean,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun calculateQuote(request: CheckoutQuoteRequest): CheckoutQuoteResponse = calculateQuote(request, persist = true)

    private fun calculateQuote(request: CheckoutQuoteRequest, persist: Boolean): CheckoutQuoteResponse {
        validateItems(request.items, "Quote")
        val customerId = requireNotNull(request.customerId) { "Authenticated customer context is required" }
        val paymentMethod = normalizePaymentMethod(request.paymentMethod)
        if (paymentMethod != "COD" && !onlinePaymentsEnabled) {
            throw IllegalStateException("Online checkout is temporarily unavailable. Select cash on delivery.")
        }
        validateCustomerServiceability(request.city, request.latitude, request.longitude)

        var subtotal = BigDecimal.ZERO
        request.items.forEach { item ->
            val offering = catalogModule.offering(item.offeringId)
            require(offering.providerId == request.providerId) {
                "All checkout items must belong to the selected provider"
            }
            require(offering.status == "ACTIVE") { "Offering ${item.offeringId} is not available" }
            val stock = offering.stockQuantity
                ?: throw IllegalArgumentException("Offering ${item.offeringId} is not a delivery product")
            require(stock >= item.quantity) { "Insufficient stock for offering ${item.offeringId}" }
            subtotal = subtotal.add(offering.price.multiply(BigDecimal(item.quantity)))
        }
        subtotal = money(subtotal)

        val couponCode = request.couponCode?.trim()?.uppercase()?.takeIf(String::isNotBlank)
        val couponDiscount = couponCode?.let { couponDiscount(it, subtotal, request.providerId) } ?: BigDecimal.ZERO
        val loyaltyTerms = request.loyaltyRewardId?.let { rewardId ->
            paymentModule.loyaltyRewardTerms(rewardId, customerId, request.providerId)
        }
        if (couponCode != null && loyaltyTerms != null && !loyaltyTerms.stackableWithCoupon) {
            throw IllegalArgumentException("This loyalty reward cannot be combined with a normal coupon")
        }

        val itemDiscount = BigDecimal.ZERO
        val afterCoupon = subtotal.subtract(itemDiscount).subtract(couponDiscount).max(BigDecimal.ZERO)
        val loyaltyDiscount = loyaltyTerms?.amount?.min(afterCoupon) ?: BigDecimal.ZERO
        val taxableBase = afterCoupon.subtract(loyaltyDiscount).max(BigDecimal.ZERO)
        val deliveryFee = quoteDelivery(request.providerId, request.latitude, request.longitude)
        val tax = money(taxableBase.multiply(CheckoutPricingContract.TAX_RATE))
        val roundOff = BigDecimal.ZERO
        val payable = money(taxableBase.add(deliveryFee).add(tax))
        require(payable >= BigDecimal.ZERO) { "Calculated order total cannot be negative" }

        var codAvailable = true
        var codReason: String? = null
        if (paymentMethod == "COD") {
            val decision = paymentModule.codEligibility(payable, request.city, request.providerId)
            codAvailable = decision.eligible
            codReason = decision.reason
        }

        val quoteToken = "Q-${UUID.randomUUID().toString().take(12)}"
        val expiresAt = Instant.now().plusSeconds(900)
        if (persist) {
            quoteStore.store(
                quoteToken,
                QuoteSnapshot(
                    total = payable,
                    couponCode = couponCode,
                    customerId = customerId,
                    providerId = request.providerId,
                    paymentMethod = paymentMethod,
                    deliveryAddressId = request.deliveryAddressId,
                    loyaltyRewardId = request.loyaltyRewardId,
                    items = request.items.map { QuoteItemSnapshot(it.offeringId, it.quantity) },
                ),
            )
        }

        return CheckoutQuoteResponse(
            quoteToken = quoteToken,
            subtotal = subtotal,
            itemDiscount = itemDiscount,
            couponDiscount = money(couponDiscount),
            loyaltyDiscount = money(loyaltyDiscount),
            deliveryFee = deliveryFee,
            tax = tax,
            roundOff = roundOff,
            payableTotal = payable,
            couponCode = couponCode,
            paymentMethod = paymentMethod,
            isCodAvailable = codAvailable,
            codRejectionReason = codReason,
            expiresAt = expiresAt,
        )
    }

    @Transactional
    fun createOrder(request: CreateOrderRequest, idempotencyKey: String?): Order {
        validateItems(request.items, "Order")
        val customerId = requireNotNull(request.customerId) { "Authenticated customer context is required" }
        val paymentMethod = normalizePaymentMethod(request.paymentMethod)
        if (paymentMethod != "COD" && !onlinePaymentsEnabled) {
            throw IllegalStateException("Online checkout is temporarily unavailable. Select cash on delivery.")
        }
        val quoteToken = request.quoteToken?.trim()?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Quote token is mandatory for order creation")
        val checkoutRequestId = checkoutRequestId(customerId, idempotencyKey ?: quoteToken)
        orderRepository.findByCheckoutRequestId(checkoutRequestId)?.let { existing ->
            require(existing.customerId == customerId && existing.providerId == request.providerId) {
                "Idempotency key is already bound to a different order"
            }
            return existing
        }

        val locked = quoteStore.consume(quoteToken)
            ?: throw IllegalArgumentException("Quote token has expired, is invalid, or was already used. Request a new quote.")
        validateLockedQuote(locked, request, customerId, paymentMethod)

        val fresh = calculateQuote(
            CheckoutQuoteRequest(
                customerId = customerId,
                providerId = request.providerId,
                deliveryAddressId = request.deliveryAddressId,
                items = request.items,
                couponCode = request.couponCode,
                loyaltyRewardId = request.loyaltyRewardId,
                paymentMethod = paymentMethod,
                city = request.city,
                latitude = request.latitude,
                longitude = request.longitude,
            ),
            persist = false,
        )
        require(fresh.payableTotal.compareTo(locked.total) == 0) {
            "Price has changed since your quote. Please request a new quote."
        }
        if (paymentMethod == "COD" && !fresh.isCodAvailable) {
            throw IllegalArgumentException("COD_NOT_ELIGIBLE: ${fresh.codRejectionReason ?: "Cash on delivery is unavailable"}")
        }

        val isCod = paymentMethod == "COD"
        val order = orderRepository.saveAndFlush(
            Order(
                customerId = customerId,
                providerId = request.providerId,
                deliveryAddressId = request.deliveryAddressId,
                status = OrderStatus.PLACED,
                checkoutRequestId = checkoutRequestId,
                subtotalAmount = fresh.subtotal,
                deliveryFee = fresh.deliveryFee,
                discountAmount = fresh.itemDiscount.add(fresh.couponDiscount).add(fresh.loyaltyDiscount),
                loyaltyRewardId = request.loyaltyRewardId,
                loyaltyDiscountAmount = fresh.loyaltyDiscount,
                taxAmount = fresh.tax,
                totalAmount = fresh.payableTotal,
                couponCode = fresh.couponCode,
                paymentMethod = paymentMethod,
                paymentStatus = if (isCod) PaymentStatus.COD_PENDING else PaymentStatus.PENDING,
            )
        )
        val orderId = requireNotNull(order.orderId)
        val reservedItems = mutableListOf<OrderItem>()
        var couponReserved = false
        var loyaltyReserved = false
        var paymentPrepared = false

        try {
            request.items.forEach { requestItem ->
                val offering = catalogModule.offering(requestItem.offeringId)
                require(offering.providerId == request.providerId && offering.status == "ACTIVE") {
                    "Offering ${requestItem.offeringId} is no longer available from this merchant"
                }
                val orderItem = orderItemRepository.saveAndFlush(
                    OrderItem(
                        orderId = orderId,
                        offeringId = requestItem.offeringId,
                        offeringNameSnapshot = offering.name,
                        unitPriceSnapshot = offering.price,
                        quantity = requestItem.quantity,
                        lineTotal = money(offering.price.multiply(BigDecimal(requestItem.quantity))),
                    )
                )
                val orderItemId = requireNotNull(orderItem.orderItemId)
                val reserved = catalogModule.reserveStock(
                    StockMutationCommand(
                        offeringId = orderItem.offeringId,
                        quantity = orderItem.quantity,
                        idempotencyKey = stockOperationId("RESERVE", orderId, orderItemId),
                    )
                )
                require(reserved.price.compareTo(orderItem.unitPriceSnapshot) == 0) {
                    "Offering price changed while inventory was being reserved"
                }
                reservedItems += orderItem
            }

            fresh.couponCode?.let { code ->
                val discount = paymentModule.reserveCoupon(
                    CouponReservationCommand(
                        code = code,
                        orderValue = fresh.subtotal,
                        providerId = request.providerId,
                        userId = customerId,
                        orderId = orderId,
                    )
                )
                couponReserved = true
                require(money(discount).compareTo(fresh.couponDiscount) == 0) {
                    "Coupon pricing changed before order placement. Please request a new quote."
                }
            }

            request.loyaltyRewardId?.let { rewardId ->
                paymentModule.reserveLoyaltyReward(rewardId, customerId, request.providerId, orderId)
                loyaltyReserved = true
            }

            if (!isCod) {
                val payment = paymentModule.prepareOrderPayment(
                    PrepareOrderPaymentCommand(orderId, customerId, fresh.payableTotal)
                )
                require(payment.status == "PENDING") { "Online payment was not prepared in PENDING state" }
                order.paymentId = payment.transactionId
                orderRepository.saveAndFlush(order)
                paymentPrepared = true
            }

            orderStatusHistoryRepository.save(
                OrderStatusHistory(
                    orderId = orderId,
                    fromStatus = null,
                    toStatus = OrderStatus.PLACED,
                    changedByUserId = customerId,
                    note = "Order placed with checkout integrity contract",
                )
            )
            publishOrderPlaced(order)
            if (isCod) publishMerchantActionable(order)
            return order
        } catch (error: Exception) {
            val compensationFailures = mutableListOf<Throwable>()
            reservedItems.asReversed().forEach { item ->
                runCatching {
                    catalogModule.restoreStock(
                        StockMutationCommand(
                            offeringId = item.offeringId,
                            quantity = item.quantity,
                            idempotencyKey = stockOperationId("RESTORE", orderId, requireNotNull(item.orderItemId)),
                        )
                    )
                }.onFailure(compensationFailures::add)
            }
            if (couponReserved && fresh.couponCode != null) {
                runCatching { paymentModule.releaseCoupon(fresh.couponCode, customerId, orderId) }
                    .onFailure(compensationFailures::add)
            }
            if (loyaltyReserved && request.loyaltyRewardId != null) {
                runCatching { paymentModule.releaseLoyaltyReward(request.loyaltyRewardId, customerId, orderId) }
                    .onFailure(compensationFailures::add)
            }
            if (paymentPrepared) {
                runCatching { paymentModule.expireOrderPayment(orderId, "Checkout failed before commit") }
                    .onFailure(compensationFailures::add)
            }
            if (compensationFailures.isNotEmpty()) {
                compensationService.recordCheckoutFailure(
                    orderId = orderId,
                    customerId = customerId,
                    couponCode = if (couponReserved) fresh.couponCode else null,
                    loyaltyRewardId = if (loyaltyReserved) request.loyaltyRewardId else null,
                    paymentPrepared = paymentPrepared,
                    items = reservedItems,
                )
                compensationFailures.forEach(error::addSuppressed)
            }
            throw error
        }
    }

    private fun validateLockedQuote(
        snapshot: QuoteSnapshot,
        request: CreateOrderRequest,
        customerId: UUID,
        paymentMethod: String,
    ) {
        require(snapshot.customerId == customerId) { "Quote token belongs to a different customer" }
        require(snapshot.providerId == request.providerId) { "Quote token does not match order provider" }
        require(snapshot.deliveryAddressId == request.deliveryAddressId) { "Delivery address does not match the quote" }
        require(snapshot.loyaltyRewardId == request.loyaltyRewardId) { "Loyalty reward does not match the quote" }
        val coupon = request.couponCode?.trim()?.uppercase()?.takeIf(String::isNotBlank)
        require(snapshot.couponCode == coupon) { "Coupon does not match the quote" }
        require(snapshot.paymentMethod == paymentMethod) { "Payment method does not match the quote" }
        val requestedItems = request.items.associate { it.offeringId to it.quantity }
        val lockedItems = snapshot.items.associate { it.offeringId to it.quantity }
        require(requestedItems == lockedItems && requestedItems.size == request.items.size) {
            "Order items do not match the locked quote"
        }
    }

    private fun couponDiscount(code: String, subtotal: BigDecimal, providerId: UUID): BigDecimal {
        val promo = paymentModule.promotionTerms(code, subtotal, providerId)
        return money(
            when (promo.discountType.uppercase()) {
                "PERCENTAGE" -> {
                    val raw = subtotal.multiply(promo.discountValue)
                        .divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
                    promo.maxDiscountAmount?.let(raw::min) ?: raw
                }
                "FLAT" -> promo.discountValue.min(subtotal)
                else -> throw IllegalStateException("Payment module returned an invalid coupon type")
            }
        )
    }

    private fun quoteDelivery(providerId: UUID, latitude: Double?, longitude: Double?): BigDecimal {
        require(latitude != null && longitude != null) {
            "Delivery coordinates are required for merchant-origin quotation"
        }
        val origin = providerModule.location(providerId)
        val straightLine = haversineKm(origin.latitude, origin.longitude, latitude, longitude)
        val routeKm = money(BigDecimal.valueOf(straightLine).multiply(CheckoutPricingContract.ROUTE_DISTANCE_FACTOR))
        require(routeKm <= CheckoutPricingContract.MAX_SERVICE_DISTANCE_KM) {
            "UNSERVICEABLE_REGION: Merchant is ${routeKm.toPlainString()} km away, beyond the delivery radius"
        }
        val billableKm = routeKm.subtract(CheckoutPricingContract.INCLUDED_DISTANCE_KM).max(BigDecimal.ZERO)
        return money(
            CheckoutPricingContract.BASE_DELIVERY_FEE.add(
                billableKm.multiply(CheckoutPricingContract.PER_KM_FEE)
            )
        )
    }

    private fun validateCustomerServiceability(city: String?, latitude: Double?, longitude: Double?) {
        require((latitude == null) == (longitude == null)) { "Latitude and longitude must be provided together" }
        require(latitude != null && longitude != null) { "Delivery coordinates are required" }
        val decision = discoveryModule.checkServiceability(city, latitude, longitude)
        require(decision.serviceable) {
            "UNSERVICEABLE_REGION: ${decision.reason ?: "Location is outside active service regions"}"
        }
    }

    private fun publishOrderPlaced(order: Order) {
        val event = OrderPlacedEvent(
            orderId = requireNotNull(order.orderId),
            actorId = order.customerId,
            customerId = order.customerId,
            providerId = order.providerId,
            merchantOwnerUserId = providerModule.ownerUserId(order.providerId),
            totalAmount = order.totalAmount,
        )
        outboxService.saveEvent(
            eventId = event.eventId,
            aggregateType = "ORDER",
            aggregateId = requireNotNull(order.orderId),
            eventType = event.eventType,
            eventPayload = event,
        )
    }

    private fun publishMerchantActionable(order: Order) {
        val event = MerchantOrderActionableEvent(
            orderId = requireNotNull(order.orderId),
            actorId = order.customerId,
            customerId = order.customerId,
            providerId = order.providerId,
            merchantOwnerUserId = providerModule.ownerUserId(order.providerId),
            totalAmount = order.totalAmount,
        )
        outboxService.saveEvent(
            eventId = event.eventId,
            aggregateType = "ORDER",
            aggregateId = requireNotNull(order.orderId),
            eventType = event.eventType,
            eventPayload = event,
        )
    }

    private fun validateItems(items: List<OrderItemRequest>, subject: String) {
        require(items.isNotEmpty()) { "$subject must contain at least one item" }
        require(items.size <= 50) { "$subject cannot contain more than 50 line items" }
        require(items.all { it.quantity in 1..99 }) { "Item quantities must be between 1 and 99" }
        require(items.map { it.offeringId }.distinct().size == items.size) { "Duplicate offering IDs are not allowed" }
    }

    private fun normalizePaymentMethod(value: String?): String {
        val normalized = value?.trim()?.uppercase() ?: "CARD"
        require(normalized in setOf("CARD", "UPI", "COD")) { "Unsupported payment method" }
        return normalized
    }

    private fun checkoutRequestId(customerId: UUID, rawKey: String): UUID = UUID.nameUUIDFromBytes(
        "ORDER:$customerId:${rawKey.trim()}".toByteArray(StandardCharsets.UTF_8)
    )

    private fun stockOperationId(operation: String, orderId: UUID, orderItemId: UUID): UUID = UUID.nameUUIDFromBytes(
        "$operation:$orderId:$orderItemId".toByteArray(StandardCharsets.UTF_8)
    )

    private fun money(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val startLat = Math.toRadians(lat1)
        val endLat = Math.toRadians(lat2)
        val a = sin(dLat / 2).pow(2) + cos(startLat) * cos(endLat) * sin(dLon / 2).pow(2)
        return 2 * earthRadiusKm * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
