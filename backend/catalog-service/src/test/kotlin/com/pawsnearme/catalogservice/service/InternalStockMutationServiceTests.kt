package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.InternalStockMutation
import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.repository.InternalStockMutationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class InternalStockMutationServiceTests {
    @Test
    fun `100 independent qty one reservations against stock 100 end at zero`() {
        val offeringId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val stock = AtomicInteger(100)
        val mutations = ConcurrentHashMap<UUID, InternalStockMutation>()
        val catalogService: CatalogService = mock()
        val mutationRepository: InternalStockMutationRepository = mock()

        whenever(mutationRepository.findById(any())).thenAnswer { invocation ->
            Optional.ofNullable(mutations[invocation.getArgument(0)])
        }
        whenever(mutationRepository.saveAndFlush(any<InternalStockMutation>())).thenAnswer { invocation ->
            val mutation = invocation.getArgument<InternalStockMutation>(0)
            val previous = mutations.putIfAbsent(mutation.idempotencyKey, mutation)
            if (previous != null) throw DataIntegrityViolationException("duplicate mutation")
            mutation
        }
        whenever(catalogService.decrementStock(eq(offeringId), eq(1))).thenAnswer {
            while (true) {
                val current = stock.get()
                if (current < 1) throw IllegalArgumentException("Insufficient stock quantity")
                if (stock.compareAndSet(current, current - 1)) {
                    return@thenAnswer offering(offeringId, providerId, current - 1)
                }
            }
            error("unreachable")
        }
        whenever(catalogService.getOfferingById(offeringId)).thenAnswer {
            offering(offeringId, providerId, stock.get())
        }

        val service = InternalStockMutationService(catalogService, mutationRepository)
        val pool = Executors.newFixedThreadPool(20)
        val gate = CountDownLatch(1)
        val futures = (1..100).map {
            pool.submit {
                gate.await()
                val orderId = UUID.randomUUID()
                val orderItemId = UUID.randomUUID()
                service.mutate(
                    operationId("RESERVE", orderId, orderItemId),
                    offeringId,
                    1,
                    "DECREMENT",
                )
            }
        }
        gate.countDown()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)

        assertEquals(0, stock.get(), "100 independent reservations must consume all 100 units")
        assertEquals(100, mutations.size, "each order line must have its own mutation identity")

        assertThrows(IllegalArgumentException::class.java) {
            service.mutate(
                operationId("RESERVE", UUID.randomUUID(), UUID.randomUUID()),
                offeringId,
                1,
                "DECREMENT",
            )
        }
        assertEquals(0, stock.get(), "stock must never become negative")
    }

    @Test
    fun `replaying same reservation operation does not decrement twice`() {
        val offeringId = UUID.randomUUID()
        val providerId = UUID.randomUUID()
        val stock = AtomicInteger(2)
        val mutationKey = operationId("RESERVE", UUID.randomUUID(), UUID.randomUUID())
        val mutations = ConcurrentHashMap<UUID, InternalStockMutation>()
        val catalogService: CatalogService = mock()
        val mutationRepository: InternalStockMutationRepository = mock()

        whenever(mutationRepository.findById(any())).thenAnswer { invocation ->
            Optional.ofNullable(mutations[invocation.getArgument(0)])
        }
        whenever(mutationRepository.saveAndFlush(any<InternalStockMutation>())).thenAnswer { invocation ->
            invocation.getArgument<InternalStockMutation>(0).also { mutations[it.idempotencyKey] = it }
        }
        whenever(catalogService.decrementStock(offeringId, 1)).thenAnswer {
            val remaining = stock.decrementAndGet()
            offering(offeringId, providerId, remaining)
        }
        whenever(catalogService.getOfferingById(offeringId)).thenAnswer {
            offering(offeringId, providerId, stock.get())
        }

        val service = InternalStockMutationService(catalogService, mutationRepository)
        service.mutate(mutationKey, offeringId, 1, "DECREMENT")
        service.mutate(mutationKey, offeringId, 1, "DECREMENT")

        assertEquals(1, stock.get())
        assertEquals(1, mutations.size)
    }

    private fun operationId(operation: String, orderId: UUID, orderItemId: UUID): UUID =
        UUID.nameUUIDFromBytes("$operation:$orderId:$orderItemId".toByteArray(StandardCharsets.UTF_8))

    private fun offering(offeringId: UUID, providerId: UUID, stock: Int) = Offering(
        offeringId = offeringId,
        providerId = providerId,
        name = "Concurrent Dog Food",
        price = BigDecimal("100.00"),
        stockQuantity = stock,
    )
}
