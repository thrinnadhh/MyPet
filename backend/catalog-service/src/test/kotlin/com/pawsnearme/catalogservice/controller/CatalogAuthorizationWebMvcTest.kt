package com.pawsnearme.catalogservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.catalogservice.dto.OfferingRequest
import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.model.OfferingStatus
import com.pawsnearme.catalogservice.service.CatalogService
import com.pawsnearme.catalogservice.service.InternalStockMutationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(controllers = [CatalogController::class, InternalCatalogController::class])
@TestPropertySource(properties = ["internal.api.secret=dev-internal-secret"])
class CatalogAuthorizationWebMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var catalogService: CatalogService

    @MockBean
    private lateinit var internalStockMutationService: InternalStockMutationService

    private val providerId = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val nonOwnerId = UUID.randomUUID()
    private val offeringId = UUID.randomUUID()

    private val sampleOffering = Offering(
        offeringId = offeringId,
        providerId = providerId,
        name = "Dog Shampoo",
        description = "Organic",
        category = "GROOMING",
        price = BigDecimal("25.00"),
        status = OfferingStatus.ACTIVE,
        stockQuantity = 50
    )

    @Test
    fun `createOffering - merchant owning provider succeeds with 201`() {
        whenever(catalogService.isProviderOwnedBy(eq(providerId), eq(ownerId))).thenReturn(true)
        whenever(catalogService.createOffering(any())).thenReturn(sampleOffering)

        val request = OfferingRequest(
            providerId = providerId,
            name = "Dog Shampoo",
            description = null,
            category = null,
            price = BigDecimal("25.00"),
            imageUrl = null,
            stockQuantity = 10,
            sku = null,
            durationMinutes = null
        )

        mockMvc.perform(
            post("/api/v1/catalog/offerings")
                .header("X-User-Id", ownerId.toString())
                .header("X-User-Role", "MERCHANT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `createOffering - non-owner merchant returns 403`() {
        whenever(catalogService.isProviderOwnedBy(eq(providerId), eq(nonOwnerId))).thenReturn(false)

        val request = OfferingRequest(
            providerId = providerId,
            name = "Dog Shampoo",
            description = null,
            category = null,
            price = BigDecimal("25.00"),
            imageUrl = null,
            stockQuantity = 10,
            sku = null,
            durationMinutes = null
        )

        mockMvc.perform(
            post("/api/v1/catalog/offerings")
                .header("X-User-Id", nonOwnerId.toString())
                .header("X-User-Role", "MERCHANT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `updateOffering - non-owner merchant returns 403`() {
        whenever(catalogService.getOfferingById(offeringId)).thenReturn(sampleOffering)
        whenever(catalogService.isProviderOwnedBy(eq(providerId), eq(nonOwnerId))).thenReturn(false)

        val request = OfferingRequest(
            providerId = providerId,
            name = "Updated Shampoo",
            description = null,
            category = null,
            price = BigDecimal("30.00"),
            imageUrl = null,
            stockQuantity = 10,
            sku = null,
            durationMinutes = null
        )

        mockMvc.perform(
            put("/api/v1/catalog/offerings/$offeringId")
                .header("X-User-Id", nonOwnerId.toString())
                .header("X-User-Role", "MERCHANT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOffering - non-owner merchant returns 403`() {
        whenever(catalogService.getOfferingById(offeringId)).thenReturn(sampleOffering)
        whenever(catalogService.isProviderOwnedBy(eq(providerId), eq(nonOwnerId))).thenReturn(false)

        mockMvc.perform(
            delete("/api/v1/catalog/offerings/$offeringId")
                .header("X-User-Id", nonOwnerId.toString())
                .header("X-User-Role", "MERCHANT")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `decrementStock - unauthenticated caller returns 403`() {
        whenever(catalogService.getOfferingById(offeringId)).thenReturn(sampleOffering)

        mockMvc.perform(
            put("/api/v1/catalog/offerings/$offeringId/decrement-stock?quantity=2")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `decrementStock - internal secret cannot bypass merchant ownership`() {
        whenever(catalogService.getOfferingById(offeringId)).thenReturn(sampleOffering)
        whenever(catalogService.decrementStock(eq(offeringId), eq(2))).thenReturn(sampleOffering)

        mockMvc.perform(
            put("/api/v1/catalog/offerings/$offeringId/decrement-stock?quantity=2")
                .header("X-Internal-Secret", "dev-internal-secret")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `decrementStockInternal - invalid secret returns 403`() {
        mockMvc.perform(
            put("/api/v1/internal/catalog/offerings/$offeringId/decrement-stock?quantity=2")
                .header("X-Internal-Secret", "wrong-secret")
                .header("X-Service-Name", "order-service")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Service-Name", "order-service")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Service-Name", "order-service")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Service-Name", "order-service")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Service-Name", "order-service")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Service-Name", "order-service")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Service-Name", "order-service")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Service-Name", "order-service")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `decrementStockInternal - valid internal secret succeeds with 200`() {
        val idempotencyKey = UUID.randomUUID()
        whenever(internalStockMutationService.mutate(eq(idempotencyKey), eq(offeringId), eq(2), eq("DECREMENT")))
            .thenReturn(sampleOffering)

        mockMvc.perform(
            put("/api/v1/internal/catalog/offerings/$offeringId/decrement-stock?quantity=2")
                .header("X-Internal-Secret", "dev-internal-secret")
                .header("X-Service-Name", "order-service")
                .header("X-Idempotency-Key", idempotencyKey.toString())
        )
            .andExpect(status().isOk)
    }
}
