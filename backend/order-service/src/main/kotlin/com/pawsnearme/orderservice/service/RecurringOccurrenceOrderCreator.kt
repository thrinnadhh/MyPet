package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.Order
import com.pawsnearme.orderservice.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Commits the generated order independently from the subscription scheduler
 * transaction. A worker crash after this method commits can therefore be
 * reconciled by recurringOccurrenceId on the next scheduler attempt.
 *
 * The deterministic internal quote token also becomes OrderService's stock
 * reservation scope. If the worker dies after the remote catalog mutation but
 * before the local order commit, the retry uses the same catalog idempotency key
 * instead of reserving the same stock twice.
 */
@Service
class RecurringOccurrenceOrderCreator(
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    private val quoteStore: QuoteStore,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createOrGet(
        occurrenceId: UUID,
        customerId: UUID,
        providerId: UUID,
        deliveryAddressId: UUID,
        items: List<OrderItemRequest>,
        paymentMethod: String,
        city: String,
        latitude: Double,
        longitude: Double,
    ): Order {
        orderRepository.findByRecurringOccurrenceId(occurrenceId).orElse(null)?.let { return it }

        val quoteRequest = CheckoutQuoteRequest(
            customerId = customerId,
            providerId = providerId,
            deliveryAddressId = deliveryAddressId,
            items = items,
            paymentMethod = paymentMethod,
            city = city,
            latitude = latitude,
            longitude = longitude,
        )
        val quote = orderService.calculateQuote(quoteRequest)
        // The public quote is random. Recurring execution replaces it with a
        // deterministic single-use token whose identity survives worker restarts.
        quoteStore.delete(quote.quoteToken)
        val deterministicToken = recurringQuoteToken(occurrenceId)
        quoteStore.store(
            deterministicToken,
            QuoteSnapshot(
                total = quote.payableTotal,
                couponCode = null,
                customerId = customerId,
                providerId = providerId,
                paymentMethod = paymentMethod,
                deliveryAddressId = deliveryAddressId,
                loyaltyRewardId = null,
                items = items.map { QuoteItemSnapshot(it.offeringId, it.quantity) },
            )
        )

        val created = orderService.createOrder(
            CreateOrderRequest(
                customerId = customerId,
                providerId = providerId,
                deliveryAddressId = deliveryAddressId,
                items = items,
                paymentMethod = paymentMethod,
                quoteToken = deterministicToken,
                city = city,
                latitude = latitude,
                longitude = longitude,
            )
        )
        created.recurringOccurrenceId = occurrenceId
        return orderRepository.save(created)
    }

    internal fun recurringQuoteToken(occurrenceId: UUID): String = "R-$occurrenceId"
}
