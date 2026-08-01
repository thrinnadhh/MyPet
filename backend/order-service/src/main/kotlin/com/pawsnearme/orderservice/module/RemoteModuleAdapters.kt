package com.pawsnearme.orderservice.module

import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.CatalogOfferingSnapshot
import com.pawsnearme.common.module.CatalogSlotSnapshot
import com.pawsnearme.common.module.CodEligibilityDecision
import com.pawsnearme.common.module.CouponReservationCommand
import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.PaymentTransactionSnapshot
import com.pawsnearme.common.module.PromotionTerms
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.module.ServiceabilityDecision
import com.pawsnearme.common.module.StockMutationCommand
import com.pawsnearme.common.module.VaccinationReminderSnapshot
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestOperations
import org.springframework.web.util.UriComponentsBuilder
import java.math.BigDecimal
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class OrderRemoteModuleConfiguration {
    @Bean
    @ConditionalOnMissingBean(CatalogModuleApi::class)
    fun remoteCatalogModuleApi(
        restOperations: RestOperations,
        @Value("\${CATALOG_SERVICE_URL:http://localhost:8082}") baseUrl: String,
        @Value("\${internal.api.secret:}") secret: String
    ): CatalogModuleApi = RemoteCatalogModuleApi(restOperations, baseUrl, secret)

    @Bean
    @ConditionalOnMissingBean(PaymentModuleApi::class)
    fun remotePaymentModuleApi(
        restOperations: RestOperations,
        @Value("\${PAYMENT_SERVICE_URL:http://localhost:8090}") baseUrl: String,
        @Value("\${internal.api.secret:}") secret: String
    ): PaymentModuleApi = RemotePaymentModuleApi(restOperations, baseUrl, secret)

    @Bean
    @ConditionalOnMissingBean(ProviderModuleApi::class)
    fun remoteProviderModuleApi(
        restOperations: RestOperations,
        @Value("\${PROVIDER_SERVICE_URL:http://localhost:8081}") baseUrl: String,
        @Value("\${internal.api.secret:}") secret: String
    ): ProviderModuleApi = RemoteProviderModuleApi(restOperations, baseUrl, secret)

    @Bean
    @ConditionalOnMissingBean(DiscoveryModuleApi::class)
    fun remoteDiscoveryModuleApi(
        restOperations: RestOperations,
        @Value("\${DISCOVERY_SERVICE_URL:http://localhost:8083}") baseUrl: String
    ): DiscoveryModuleApi = RemoteDiscoveryModuleApi(restOperations, baseUrl)
}

class RemoteCatalogModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String,
    private val internalSecret: String
) : CatalogModuleApi {
    override fun offering(offeringId: UUID): CatalogOfferingSnapshot {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/internal/catalog/offerings/$offeringId",
            HttpMethod.GET,
            HttpEntity<Any>(internalHeaders()),
            Map::class.java
        ).body ?: throw IllegalStateException("Catalog service returned an empty offering response")
        return response.toOffering(offeringId)
    }

    override fun reserveStock(command: StockMutationCommand): CatalogOfferingSnapshot =
        mutate(command, "decrement-stock")

    override fun restoreStock(command: StockMutationCommand): CatalogOfferingSnapshot =
        mutate(command, "restore-stock")

    override fun slot(slotId: UUID): CatalogSlotSnapshot? = runCatching {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/catalog/slots/$slotId",
            HttpMethod.GET,
            HttpEntity<Any>(internalHeaders()),
            Map::class.java
        ).body ?: return@runCatching null
        CatalogSlotSnapshot(
            slotId = UUID.fromString(response["slotId"].toString()),
            slotStart = response["slotStart"]?.toString()?.let(java.time.Instant::parse),
            slotEnd = response["slotEnd"]?.toString()?.let(java.time.Instant::parse),
            status = response["status"].toString()
        )
    }.getOrNull()

    override fun updateSlotStatus(slotId: UUID, status: String): CatalogSlotSnapshot {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/catalog/slots/$slotId/status?status=${status.trim().uppercase()}",
            HttpMethod.PUT,
            HttpEntity<Any>(internalHeaders()),
            Map::class.java
        ).body ?: throw IllegalStateException("Catalog service returned an empty slot response")
        return CatalogSlotSnapshot(
            slotId = UUID.fromString(response["slotId"].toString()),
            slotStart = response["slotStart"]?.toString()?.let(java.time.Instant::parse),
            slotEnd = response["slotEnd"]?.toString()?.let(java.time.Instant::parse),
            status = response["status"].toString()
        )
    }

    private fun mutate(command: StockMutationCommand, operation: String): CatalogOfferingSnapshot {
        val headers = internalHeaders().apply {
            set("X-Idempotency-Key", command.idempotencyKey.toString())
        }
        val response = restOperations.exchange(
            "$baseUrl/api/v1/internal/catalog/offerings/${command.offeringId}/$operation?quantity=${command.quantity}",
            HttpMethod.PUT,
            HttpEntity<Any>(headers),
            Map::class.java
        ).body ?: throw IllegalStateException("Catalog service returned an empty stock response")
        return response.toOffering(command.offeringId)
    }

    private fun Map<*, *>.toOffering(fallbackId: UUID): CatalogOfferingSnapshot = CatalogOfferingSnapshot(
        offeringId = this["offeringId"]?.toString()?.let(UUID::fromString) ?: fallbackId,
        providerId = UUID.fromString(this["providerId"].toString()),
        name = this["name"]?.toString() ?: "Pet Product",
        price = decimal(this["price"]),
        status = this["status"]?.toString()?.uppercase() ?: "ACTIVE",
        stockQuantity = (this["stockQuantity"] as? Number)?.toInt()
    )

    private fun internalHeaders() = HttpHeaders().apply {
        if (internalSecret.isNotBlank()) {
            set("X-Internal-Secret", internalSecret)
            set("X-Service-Name", "order-service")
        }
    }
}

class RemotePaymentModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String,
    private val internalSecret: String
) : PaymentModuleApi {
    override fun transaction(transactionId: UUID): PaymentTransactionSnapshot? = runCatching {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/payments/transactions/$transactionId",
            HttpMethod.GET,
            HttpEntity<Any>(headers("ADMIN")),
            Map::class.java
        ).body ?: return@runCatching null
        PaymentTransactionSnapshot(
            transactionId = response["transactionId"]?.toString()?.let(UUID::fromString) ?: transactionId,
            userId = response["userId"]?.toString()?.let(UUID::fromString) ?: UUID(0L, 0L),
            referenceId = response["referenceId"]?.toString()?.let(UUID::fromString) ?: UUID(0L, 0L),
            transactionType = response["transactionType"]?.toString() ?: "ORDER_PAYMENT",
            amount = decimal(response["amount"]),
            status = response["status"].toString()
        )
    }.getOrNull()

    override fun promotionTerms(
        code: String,
        orderValue: BigDecimal,
        providerId: UUID,
        category: String?
    ): PromotionTerms {
        val url = UriComponentsBuilder.fromUriString("$baseUrl/api/v1/payments/promotions/validate")
            .queryParam("code", code)
            .queryParam("orderValue", orderValue)
            .queryParam("providerId", providerId)
            .apply { if (!category.isNullOrBlank()) queryParam("category", category) }
            .build().encode().toUriString()
        val response = restOperations.exchange(
            url,
            HttpMethod.GET,
            HttpEntity<Any>(headers()),
            Map::class.java
        ).body ?: throw IllegalArgumentException("Coupon validation returned no result")
        return PromotionTerms(
            discountType = response["discountType"].toString(),
            discountValue = decimal(response["discountValue"]),
            maxDiscountAmount = response["maxDiscountAmount"]?.let(::decimal)
        )
    }

    override fun reserveCoupon(command: CouponReservationCommand): BigDecimal {
        val response = restOperations.postForEntity(
            "$baseUrl/api/v1/payments/promotions/reserve",
            HttpEntity(
                mapOf(
                    "code" to command.code,
                    "orderValue" to command.orderValue,
                    "providerId" to command.providerId,
                    "userId" to command.userId,
                    "orderId" to command.orderId,
                    "category" to command.category
                ),
                headers("CUSTOMER", command.userId)
            ),
            Map::class.java
        ).body ?: throw IllegalStateException("Payment service returned an empty coupon reservation")
        return decimal(response["discountAmount"])
    }

    override fun releaseCoupon(code: String, userId: UUID, orderId: UUID) {
        postQuery("promotions/release", code, userId, orderId, "CUSTOMER")
    }

    override fun redeemCoupon(code: String, userId: UUID, orderId: UUID) {
        postQuery("promotions/redeem", code, userId, orderId, "ADMIN")
    }

    override fun codEligibility(
        amount: BigDecimal,
        city: String?,
        providerId: UUID?
    ): CodEligibilityDecision {
        val response = restOperations.postForEntity(
            "$baseUrl/api/v1/payments/cod/check",
            HttpEntity(mapOf("amount" to amount, "city" to city, "providerId" to providerId), headers()),
            Map::class.java
        ).body ?: throw IllegalStateException("Payment service returned an empty COD response")
        return CodEligibilityDecision(
            eligible = response["isEligible"] as? Boolean ?: false,
            maxAllowedAmount = response["maxAllowedAmount"]?.let(::decimal),
            reason = response["reason"]?.toString()
        )
    }

    override fun refundOrder(orderId: UUID) {
        restOperations.postForEntity(
            "$baseUrl/api/v1/payments/refund?orderId=$orderId",
            HttpEntity<Any>(headers("ADMIN")),
            Map::class.java
        )
    }

    override fun recordOrderDelivered(
        orderId: UUID,
        customerId: UUID,
        providerId: UUID,
        netAmount: BigDecimal
    ) {
        restOperations.postForEntity(
            "$baseUrl/api/v1/loyalty/events/order-delivered",
            HttpEntity(
                mapOf(
                    "orderId" to orderId,
                    "customerId" to customerId,
                    "providerId" to providerId,
                    "netAmount" to netAmount
                ),
                headers()
            ),
            Map::class.java
        )
    }

    override fun recordOrderRefunded(orderId: UUID, customerId: UUID, providerId: UUID) {
        restOperations.postForEntity(
            "$baseUrl/api/v1/loyalty/events/order-refunded",
            HttpEntity(
                mapOf("orderId" to orderId, "customerId" to customerId, "providerId" to providerId),
                headers()
            ),
            Map::class.java
        )
    }

    private fun postQuery(path: String, code: String, userId: UUID, orderId: UUID, role: String) {
        val url = UriComponentsBuilder.fromUriString("$baseUrl/api/v1/payments/$path")
            .queryParam("code", code)
            .queryParam("userId", userId)
            .queryParam("orderId", orderId)
            .build().encode().toUriString()
        restOperations.postForEntity(url, HttpEntity<Any>(headers(role, userId)), Map::class.java)
    }

    private fun headers(role: String? = null, userId: UUID? = null) = HttpHeaders().apply {
        if (internalSecret.isNotBlank()) {
            set("X-Internal-Secret", internalSecret)
            set("X-Service-Name", "order-service")
        }
        role?.let { set("X-User-Role", it) }
        userId?.let { set("X-User-Id", it.toString()) }
    }
}

class RemoteProviderModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String,
    private val internalSecret: String
) : ProviderModuleApi {
    override fun ownerUserId(providerId: UUID): UUID? = runCatching {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/providers/$providerId",
            HttpMethod.GET,
            HttpEntity<Any>(headers()),
            Map::class.java
        ).body
        response?.get("ownerUserId")?.toString()?.let(UUID::fromString)
    }.getOrNull()

    override fun enabledVaccinationReminders(): List<VaccinationReminderSnapshot> = emptyList()

    private fun headers() = HttpHeaders().apply {
        if (internalSecret.isNotBlank()) {
            set("X-Internal-Secret", internalSecret)
            set("X-Service-Name", "order-service")
        }
    }
}

class RemoteDiscoveryModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String
) : DiscoveryModuleApi {
    override fun checkServiceability(
        city: String?,
        latitude: Double?,
        longitude: Double?,
        pincode: String?
    ): ServiceabilityDecision {
        val url = UriComponentsBuilder.fromUriString("$baseUrl/api/v1/service-regions/check")
            .apply {
                if (!city.isNullOrBlank()) queryParam("city", city.trim())
                if (latitude != null) queryParam("latitude", latitude)
                if (longitude != null) queryParam("longitude", longitude)
                if (!pincode.isNullOrBlank()) queryParam("pincode", pincode.trim())
            }
            .build().encode().toUriString()
        val response = restOperations.getForObject(url, Map::class.java)
            ?: throw IllegalStateException("Discovery service returned an empty response")
        return ServiceabilityDecision(
            serviceable = response["serviceable"] as? Boolean ?: false,
            reason = response["reason"]?.toString()
        )
    }
}

private fun decimal(value: Any?): BigDecimal = when (value) {
    is BigDecimal -> value
    is Number -> BigDecimal(value.toString())
    is String -> value.toBigDecimal()
    else -> throw IllegalStateException("Remote module returned an invalid decimal value")
}
