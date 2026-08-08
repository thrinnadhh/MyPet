package com.pawsnearme.orderservice.service

import com.pawsnearme.orderservice.model.RecurringOrderOccurrence
import com.pawsnearme.orderservice.model.RecurringOrderOccurrenceStatus
import com.pawsnearme.orderservice.model.RecurringOrderStatus
import com.pawsnearme.orderservice.model.RecurringOrderSubscription
import com.pawsnearme.orderservice.model.RecurringOrderSubscriptionItem
import com.pawsnearme.orderservice.repository.RecurringOrderOccurrenceRepository
import com.pawsnearme.orderservice.repository.RecurringOrderSubscriptionItemRepository
import com.pawsnearme.orderservice.repository.RecurringOrderSubscriptionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class RecurringOrderAdminServiceTests {
    private val subscriptionRepository: RecurringOrderSubscriptionRepository = mock()
    private val itemRepository: RecurringOrderSubscriptionItemRepository = mock()
    private val occurrenceRepository: RecurringOrderOccurrenceRepository = mock()
    private val service = RecurringOrderAdminService(subscriptionRepository, itemRepository, occurrenceRepository)

    @Test
    fun `admin list is paginated and includes failure diagnostics`() {
        val subscription = subscription(cadenceDays = 35).apply {
            lastFailureCode = "OUT_OF_STOCK"
            lastFailureDetail = "Offering unavailable"
        }
        whenever(subscriptionRepository.findAllByOrderByCreatedAtDesc(any<Pageable>())).thenAnswer { invocation ->
            PageImpl(listOf(subscription), invocation.getArgument(0), 1L)
        }
        whenever(itemRepository.findBySubscriptionIdOrderByCreatedAtAsc(subscription.subscriptionId)).thenReturn(
            listOf(item(subscription.subscriptionId))
        )

        val result = service.list(page = 0, size = 25)

        assertEquals(1L, result.totalElements)
        assertEquals(35, result.content.single().cadenceDays)
        assertEquals("OUT_OF_STOCK", result.content.single().lastFailureCode)
        assertEquals(1, result.content.single().items.size)
    }

    @Test
    fun `trace links subscription occurrence generated order and failure evidence`() {
        val subscription = subscription(cadenceDays = 7)
        val occurrence = RecurringOrderOccurrence(
            subscriptionId = subscription.subscriptionId,
            scheduledFor = Instant.now(),
            orderId = UUID.randomUUID(),
            status = RecurringOrderOccurrenceStatus.ORDER_CREATED
        )
        whenever(subscriptionRepository.findById(subscription.subscriptionId)).thenReturn(Optional.of(subscription))
        whenever(itemRepository.findBySubscriptionIdOrderByCreatedAtAsc(subscription.subscriptionId)).thenReturn(
            listOf(item(subscription.subscriptionId))
        )
        whenever(occurrenceRepository.findBySubscriptionIdOrderByScheduledForDesc(
            org.mockito.kotlin.eq(subscription.subscriptionId),
            any<Pageable>()
        )).thenAnswer { invocation -> PageImpl(listOf(occurrence), invocation.getArgument(1), 1L) }

        val trace = service.trace(subscription.subscriptionId, page = 0, size = 25)

        assertEquals(subscription.subscriptionId, trace.subscription.subscriptionId)
        assertEquals(occurrence.orderId, trace.occurrences.single().orderId)
        assertEquals(RecurringOrderOccurrenceStatus.ORDER_CREATED, trace.occurrences.single().status)
    }

    @Test
    fun `subscription admin list rejects unbounded page size`() {
        assertThrows<IllegalArgumentException> { service.list(0, 1000) }
        verify(subscriptionRepository, never()).findAllByOrderByCreatedAtDesc(any<Pageable>())
    }

    @Test
    fun `all supported MyPet recurrence cadences remain representable`() {
        val supported = setOf(7, 15, 25, 30, 35)
        assertEquals(supported, supported.map { subscription(it).cadenceDays }.toSet())
    }

    private fun subscription(cadenceDays: Int) = RecurringOrderSubscription(
        customerId = UUID.randomUUID(),
        providerId = UUID.randomUUID(),
        sourceOrderId = UUID.randomUUID(),
        deliveryAddressId = UUID.randomUUID(),
        cadenceDays = cadenceDays,
        status = RecurringOrderStatus.ACTIVE,
        nextOrderAt = Instant.now().plusSeconds(cadenceDays * 86400L)
    )

    private fun item(subscriptionId: UUID) = RecurringOrderSubscriptionItem(
        subscriptionId = subscriptionId,
        offeringId = UUID.randomUUID(),
        offeringNameSnapshot = "Pet food",
        baseQuantity = 1,
        unitPriceAtCreation = BigDecimal("499.00")
    )
}