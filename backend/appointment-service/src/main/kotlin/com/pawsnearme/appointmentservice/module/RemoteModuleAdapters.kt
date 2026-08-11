package com.pawsnearme.appointmentservice.module

import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.CatalogOfferingSnapshot
import com.pawsnearme.common.module.CatalogSlotSnapshot
import com.pawsnearme.common.module.CodEligibilityDecision
import com.pawsnearme.common.module.CouponReservationCommand
import com.pawsnearme.common.module.PaymentModuleApi
import com.pawsnearme.common.module.PaymentTransactionSnapshot
import com.pawsnearme.common.module.PromotionTerms
import com.pawsnearme.common.module.ProviderModuleApi
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Configuration(proxyBeanMethods = false)
class AppointmentRemoteModuleConfiguration {
    @Bean
    @ConditionalOnMissingBean(CatalogModuleApi::class)
    fun remoteCatalogModuleApi(
        restOperations: RestOperations,
        @Value("\${CATALOG_SERVICE_URL:http://localhost:8082}") baseUrl: String,
        @Value("\${gateway.trust.secret:}") gatewayTrustSecret: String
    ): CatalogModuleApi = RemoteCatalogModuleApi(restOperations, baseUrl, gatewayTrustSecret)

    @Bean
    @ConditionalOnMissingBean(ProviderModuleApi::class)
    fun remoteProviderModuleApi(
        restOperations: RestOperations,
        @Value("\${PROVIDER_SERVICE_URL:http://localhost:8081}") baseUrl: String,
        @Value("\${internal.api.secret:}") internalSecret: String,
        @Value("\${gateway.trust.secret:}") gatewayTrustSecret: String
    ): ProviderModuleApi = RemoteProviderModuleApi(restOperations, baseUrl, internalSecret, gatewayTrustSecret)

    @Bean
    @ConditionalOnMissingBean(PaymentModuleApi::class)
    fun remotePaymentModuleApi(
        restOperations: RestOperations,
        @Value("\${PAYMENT_SERVICE_URL:http://localhost:8090}") baseUrl: String,
        @Value("\${gateway.trust.secret:}") gatewayTrustSecret: String,
        @Value("\${internal.api.secret:}") internalSecret: String,
    ): PaymentModuleApi = RemotePaymentModuleApi(restOperations, baseUrl, gatewayTrustSecret, internalSecret)
}

class RemoteCatalogModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String,
    private val gatewayTrustSecret: String
) : CatalogModuleApi {
    override fun offering(offeringId: UUID): CatalogOfferingSnapshot {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/catalog/offerings/$offeringId",
            HttpMethod.GET,
            HttpEntity<Any>(headers()),
            Map::class.java
        ).body ?: throw IllegalStateException("Catalog service returned an empty offering response")
        return CatalogOfferingSnapshot(
            offeringId = response["offeringId"]?.toString()?.let(UUID::fromString) ?: offeringId,
            providerId = UUID.fromString(response["providerId"].toString()),
            name = response["name"]?.toString() ?: "Pet Service",
            price = decimal(response["price"]),
            status = response["status"]?.toString() ?: "ACTIVE",
            stockQuantity = (response["stockQuantity"] as? Number)?.toInt()
        )
    }

    override fun reserveStock(command: StockMutationCommand): CatalogOfferingSnapshot =
        throw UnsupportedOperationException("Appointment module does not mutate catalog stock")

    override fun restoreStock(command: StockMutationCommand): CatalogOfferingSnapshot =
        throw UnsupportedOperationException("Appointment module does not mutate catalog stock")

    override fun slot(slotId: UUID): CatalogSlotSnapshot? = runCatching {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/catalog/slots/$slotId",
            HttpMethod.GET,
            HttpEntity<Any>(headers()),
            Map::class.java
        ).body ?: return@runCatching null
        CatalogSlotSnapshot(
            slotId = response["slotId"]?.toString()?.let(UUID::fromString) ?: slotId,
            slotStart = response["slotStart"]?.toString()?.let(::parseInstant),
            slotEnd = response["slotEnd"]?.toString()?.let(::parseInstant),
            status = response["status"]?.toString() ?: "AVAILABLE"
        )
    }.getOrNull()

    override fun updateSlotStatus(slotId: UUID, status: String): CatalogSlotSnapshot {
        val normalizedStatus = status.trim().uppercase()
        restOperations.exchange(
            "$baseUrl/api/v1/catalog/slots/$slotId/status?status=$normalizedStatus",
            HttpMethod.PUT,
            HttpEntity<Any>(headers()),
            Void::class.java
        )
        return CatalogSlotSnapshot(slotId = slotId, slotStart = null, slotEnd = null, status = normalizedStatus)
    }

    private fun headers() = HttpHeaders().apply {
        if (gatewayTrustSecret.isNotBlank()) set("X-Internal-Gateway-Secret", gatewayTrustSecret)
        set("X-User-Role", "ADMIN")
    }
}

class RemoteProviderModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String,
    private val internalSecret: String,
    private val gatewayTrustSecret: String
) : ProviderModuleApi {
    override fun ownerUserId(providerId: UUID): UUID? = runCatching {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/internal/providers/$providerId/owner",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply {
                if (internalSecret.isNotBlank()) set("X-Internal-Secret", internalSecret)
                if (gatewayTrustSecret.isNotBlank()) set("X-Internal-Gateway-Secret", gatewayTrustSecret)
            }),
            Map::class.java
        ).body
        response?.get("ownerUserId")?.toString()?.let(UUID::fromString)
    }.getOrNull()

    override fun enabledVaccinationReminders(): List<VaccinationReminderSnapshot> = emptyList()
}

class RemotePaymentModuleApi(
    private val restOperations: RestOperations,
    private val baseUrl: String,
    private val gatewayTrustSecret: String,
    private val internalSecret: String = gatewayTrustSecret,
) : PaymentModuleApi {
    override fun transaction(transactionId: UUID): PaymentTransactionSnapshot? = runCatching {
        val response = restOperations.exchange(
            "$baseUrl/api/v1/payments/transactions/$transactionId",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply {
                if (gatewayTrustSecret.isNotBlank()) set("X-Internal-Gateway-Secret", gatewayTrustSecret)
                set("X-User-Role", "ADMIN")
            }),
            Map::class.java
        ).body ?: return@runCatching null
        PaymentTransactionSnapshot(
            transactionId = response["transactionId"]?.toString()?.let(UUID::fromString) ?: transactionId,
            userId = UUID.fromString(response["userId"].toString()),
            referenceId = UUID.fromString(response["referenceId"].toString()),
            transactionType = response["transactionType"].toString(),
            amount = decimal(response["amount"]),
            status = response["status"].toString()
        )
    }.getOrNull()

    override fun promotionTerms(code: String, orderValue: BigDecimal, providerId: UUID, category: String?): PromotionTerms =
        throw UnsupportedOperationException("Appointment module does not validate promotions")

    override fun reserveCoupon(command: CouponReservationCommand): BigDecimal =
        throw UnsupportedOperationException("Appointment module does not reserve coupons")

    override fun releaseCoupon(code: String, userId: UUID, orderId: UUID) = Unit
    override fun redeemCoupon(code: String, userId: UUID, orderId: UUID) = Unit
    override fun codEligibility(amount: BigDecimal, city: String?, providerId: UUID?): CodEligibilityDecision =
        throw UnsupportedOperationException("Appointment module does not check COD")

    override fun refundOrder(orderId: UUID) = Unit
    override fun recordOrderDelivered(orderId: UUID, customerId: UUID, providerId: UUID, netAmount: BigDecimal) = Unit
    override fun recordOrderRefunded(orderId: UUID, customerId: UUID, providerId: UUID) = Unit

    override fun recordServiceCompleted(
        referenceId: UUID,
        customerId: UUID,
        providerId: UUID,
        netAmount: BigDecimal,
        serviceType: String,
    ) {
        restOperations.exchange(
            "$baseUrl/api/v1/loyalty/events/service-completed",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "referenceId" to referenceId,
                    "customerId" to customerId,
                    "providerId" to providerId,
                    "netAmount" to netAmount,
                    "serviceType" to serviceType,
                ),
                HttpHeaders().apply {
                    if (internalSecret.isNotBlank()) set("X-Internal-Secret", internalSecret)
                    if (gatewayTrustSecret.isNotBlank()) set("X-Internal-Gateway-Secret", gatewayTrustSecret)
                },
            ),
            Map::class.java,
        )
    }
}

private fun parseInstant(value: String): Instant = runCatching { Instant.parse(value) }
    .getOrElse { throw IllegalStateException("Remote catalog returned an invalid slot timestamp: $value", it) }

private fun decimal(value: Any?): BigDecimal = when (value) {
    is BigDecimal -> value
    is Number -> BigDecimal(value.toString())
    is String -> value.toBigDecimal()
    else -> throw IllegalStateException("Remote module returned an invalid decimal value")
}
