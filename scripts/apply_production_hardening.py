#!/usr/bin/env python3
"""Apply MyPet production hardening for Sprints 24-28.

The script is intentionally deterministic and fail-fast. It is executed by a
one-shot GitHub Actions workflow on the hardening branch, then the resulting
repository is compiled and tested before being committed.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content.rstrip() + "\n", encoding="utf-8")
    print(f"updated {path}")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one match in {path}, found {count}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


def regex_replace(path: str, pattern: str, replacement: str, *, flags: int = 0, minimum: int = 1) -> None:
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, flags=flags)
    if count < minimum:
        raise RuntimeError(f"Expected at least {minimum} regex matches in {path}, found {count}: {pattern}")
    write(path, updated)


# ---------------------------------------------------------------------------
# S24: remove mobile trust credentials and client-controlled identity headers.
# ---------------------------------------------------------------------------

def harden_api_client(path: str) -> None:
    text = read(path)
    text = re.sub(r"\n\s*private userId: string \| null = null;", "", text)
    text = re.sub(r"\n\s*private userRole: string \| null = null;", "", text)
    text = re.sub(r"\n\s*private gatewaySecret: string = ['\"][^'\"]+['\"];", "", text)
    text = re.sub(
        r"\n\s*public setUserContext\(userId: string \| null, role: string \| null\) \{.*?\n\s*\}",
        "",
        text,
        flags=re.S,
    )
    text = re.sub(
        r"\n\s*public setGatewaySecret\(secret: string\) \{.*?\n\s*\}",
        "",
        text,
        flags=re.S,
    )
    text = re.sub(r"\n\s*'X-Internal-Gateway-Secret': this\.gatewaySecret,", "", text)
    text = re.sub(
        r"\n\s*if \(this\.userId\) \{.*?\n\s*\}\n\s*if \(this\.userRole\) \{.*?\n\s*\}",
        "",
        text,
        flags=re.S,
    )
    if "X-Internal-Gateway-Secret" in text or "setGatewaySecret" in text:
        raise RuntimeError(f"Gateway secret remains in {path}")
    write(path, text)


for client in [
    "apps/customer-app/src/services/api-client.ts",
    "apps/merchant-captain-app/src/services/api-client.ts",
]:
    harden_api_client(client)

for auth_path in [
    "apps/customer-app/src/context/AuthContext.tsx",
    "apps/merchant-captain-app/src/context/AuthContext.tsx",
]:
    p = ROOT / auth_path
    if p.exists():
        text = p.read_text(encoding="utf-8")
        text = re.sub(r"\n\s*apiClient\.setUserContext\([^;]+;", "", text)
        p.write_text(text, encoding="utf-8")
        print(f"updated {auth_path}")

# Gateway derives X-User-* from a validated JWT. It must never stamp a generic
# downstream trust credential on every public request.
gateway_yml = "backend/api-gateway/src/main/resources/application.yml"
replace_once(
    gateway_yml,
    "      # Stamp every proxied request with the gateway secret so downstream services\n"
    "      # can verify the request originated from the gateway (not a direct port hit).\n"
    "      default-filters:\n"
    "        - AddRequestHeader=X-Internal-Gateway-Secret, ${GATEWAY_SECRET:dev-secret-change-in-production}\n"
    "        - name: RequestRateLimiter\n",
    "      default-filters:\n"
    "        - RemoveRequestHeader=X-Internal-Gateway-Secret\n"
    "        - RemoveRequestHeader=X-Internal-Secret\n"
    "        - RemoveRequestHeader=X-Service-Name\n"
    "        - name: RequestRateLimiter\n",
)

# Also sanitize service-identity headers in the global auth filter.
replace_once(
    "backend/api-gateway/src/main/kotlin/com/pawsnearme/apigateway/filter/AuthenticationHeaderFilter.kt",
    '                        it.remove("X-Admin-Api-Key")\n',
    '                        it.remove("X-Admin-Api-Key")\n'
    '                        it.remove("X-Internal-Gateway-Secret")\n'
    '                        it.remove("X-Internal-Secret")\n'
    '                        it.remove("X-Service-Name")\n',
)

# ---------------------------------------------------------------------------
# S24: catalog tenant isolation and a dedicated internal stock API.
# ---------------------------------------------------------------------------
controllers = "backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/Controllers.kt"
text = read(controllers)
text = text.replace(
    "    @Value(\"\\${internal.api.secret:dev-internal-secret}\")\n"
    "    private val internalSecret: String = \"dev-internal-secret\",\n"
    "    @Value(\"\\${gateway.trust.secret:dev-gateway-secret-key}\")\n"
    "    private val gatewayTrustSecret: String = \"dev-gateway-secret-key\"\n",
    "",
)
text = re.sub(
    r"\n\s*private fun verifyStockMutationAccess\(.*?\n\s*\}\n\n",
    "\n",
    text,
    flags=re.S,
)
old_decrement = '''    @PutMapping("/offerings/{offeringId}/decrement-stock")
    fun decrementStock(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-Internal-Secret", required = false) xInternalSecret: String?,
        @RequestHeader("X-Internal-Gateway-Secret", required = false) xGatewaySecret: String?
    ): ResponseEntity<Offering> {
        val existing = catalogService.getOfferingById(offeringId)
        verifyStockMutationAccess(existing.providerId, xUserId, role, xInternalSecret, xGatewaySecret)
        val updated = catalogService.decrementStock(offeringId, quantity)
        return ResponseEntity.ok(updated)
    }

    @PutMapping("/offerings/{offeringId}/restore-stock")
    fun restoreStock(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-Internal-Secret", required = false) xInternalSecret: String?,
        @RequestHeader("X-Internal-Gateway-Secret", required = false) xGatewaySecret: String?
    ): ResponseEntity<Offering> {
        val existing = catalogService.getOfferingById(offeringId)
        verifyStockMutationAccess(existing.providerId, xUserId, role, xInternalSecret, xGatewaySecret)
        val updated = catalogService.restoreStock(offeringId, quantity)
        return ResponseEntity.ok(updated)
    }
'''
new_decrement = '''    @PutMapping("/offerings/{offeringId}/decrement-stock")
    fun decrementStock(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Offering> {
        val existing = catalogService.getOfferingById(offeringId)
        verifyProviderOwnership(existing.providerId, xUserId, role)
        return ResponseEntity.ok(catalogService.decrementStock(offeringId, quantity))
    }

    @PutMapping("/offerings/{offeringId}/restore-stock")
    fun restoreStock(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-User-Role", required = false) role: String?,
        @RequestHeader("X-User-Id", required = false) xUserId: String?
    ): ResponseEntity<Offering> {
        val existing = catalogService.getOfferingById(offeringId)
        verifyProviderOwnership(existing.providerId, xUserId, role)
        return ResponseEntity.ok(catalogService.restoreStock(offeringId, quantity))
    }
'''
if old_decrement not in text:
    raise RuntimeError("Catalog stock endpoints did not match audited source")
write(controllers, text.replace(old_decrement, new_decrement, 1))

write(
    "backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/model/InternalStockMutation.kt",
    r'''package com.pawsnearme.catalogservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "internal_stock_mutations", schema = "catalog")
class InternalStockMutation(
    @Id
    @Column(name = "idempotency_key", nullable = false)
    var idempotencyKey: UUID,

    @Column(name = "offering_id", nullable = false)
    var offeringId: UUID,

    @Column(name = "operation", nullable = false, length = 16)
    var operation: String,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
''',
)

write(
    "backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/repository/InternalStockMutationRepository.kt",
    r'''package com.pawsnearme.catalogservice.repository

import com.pawsnearme.catalogservice.model.InternalStockMutation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InternalStockMutationRepository : JpaRepository<InternalStockMutation, UUID>
''',
)

write(
    "backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/service/InternalStockMutationService.kt",
    r'''package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.InternalStockMutation
import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.repository.InternalStockMutationRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class InternalStockMutationService(
    private val catalogService: CatalogService,
    private val mutationRepository: InternalStockMutationRepository
) {
    @Transactional
    fun mutate(idempotencyKey: UUID, offeringId: UUID, quantity: Int, operation: String): Offering {
        require(quantity in 1..999) { "Quantity must be between 1 and 999" }
        require(operation == "DECREMENT" || operation == "RESTORE") { "Unsupported stock operation" }

        val existing = mutationRepository.findById(idempotencyKey).orElse(null)
        if (existing != null) {
            require(existing.offeringId == offeringId && existing.quantity == quantity && existing.operation == operation) {
                "Idempotency key was already used with different stock mutation parameters"
            }
            return catalogService.getOfferingById(offeringId)
        }

        val updated = if (operation == "DECREMENT") {
            catalogService.decrementStock(offeringId, quantity)
        } else {
            catalogService.restoreStock(offeringId, quantity)
        }

        try {
            mutationRepository.saveAndFlush(
                InternalStockMutation(idempotencyKey, offeringId, operation, quantity)
            )
        } catch (duplicate: DataIntegrityViolationException) {
            // A concurrent duplicate completed the same mutation. The transaction
            // will roll back this attempt, preventing a double mutation.
            throw duplicate
        }
        return updated
    }
}
''',
)

write(
    "backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/InternalStockController.kt",
    r'''package com.pawsnearme.catalogservice.controller

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.service.InternalStockMutationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/catalog/offerings")
class InternalStockController(
    private val mutationService: InternalStockMutationService,
    @Value("\${internal.api.secret}") private val internalSecret: String
) {
    @PutMapping("/{offeringId}/decrement-stock")
    fun decrement(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-Service-Name") serviceName: String?,
        @RequestHeader("X-Internal-Secret") suppliedSecret: String?,
        @RequestHeader("X-Idempotency-Key") idempotencyKey: UUID?
    ): ResponseEntity<Offering> = mutate(
        offeringId, quantity, serviceName, suppliedSecret, idempotencyKey, "DECREMENT"
    )

    @PutMapping("/{offeringId}/restore-stock")
    fun restore(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-Service-Name") serviceName: String?,
        @RequestHeader("X-Internal-Secret") suppliedSecret: String?,
        @RequestHeader("X-Idempotency-Key") idempotencyKey: UUID?
    ): ResponseEntity<Offering> = mutate(
        offeringId, quantity, serviceName, suppliedSecret, idempotencyKey, "RESTORE"
    )

    private fun mutate(
        offeringId: UUID,
        quantity: Int,
        serviceName: String?,
        suppliedSecret: String?,
        idempotencyKey: UUID?,
        operation: String
    ): ResponseEntity<Offering> {
        if (serviceName != "order-service" || suppliedSecret == null || !constantTimeEquals(suppliedSecret, internalSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val key = idempotencyKey ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
        return ResponseEntity.ok(mutationService.mutate(key, offeringId, quantity, operation))
    }

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8)
    )
}
''',
)

write(
    "backend/catalog-service/src/main/resources/db/migration/V4__internal_stock_mutations.sql",
    r'''CREATE TABLE IF NOT EXISTS catalog.internal_stock_mutations (
    idempotency_key UUID PRIMARY KEY,
    offering_id UUID NOT NULL,
    operation VARCHAR(16) NOT NULL CHECK (operation IN ('DECREMENT', 'RESTORE')),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_internal_stock_mutations_offering
    ON catalog.internal_stock_mutations(offering_id, created_at DESC);
''',
)

# ---------------------------------------------------------------------------
# S24: exact, single-use checkout quote bindings.
# ---------------------------------------------------------------------------
write(
    "backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/QuoteStore.kt",
    r'''package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

data class QuoteItemSnapshot(
    val offeringId: UUID,
    val quantity: Int
)

data class QuoteSnapshot(
    val total: BigDecimal,
    val couponCode: String?,
    val customerId: UUID,
    val providerId: UUID,
    val paymentMethod: String,
    val deliveryAddressId: UUID,
    val loyaltyRewardId: UUID?,
    val items: List<QuoteItemSnapshot>
)

@Component
class QuoteStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val KEY_PREFIX = "quote:"
        private val TTL = Duration.ofMinutes(15)
    }

    fun store(token: String, snapshot: QuoteSnapshot): String {
        redisTemplate.opsForValue().set(key(token), objectMapper.writeValueAsString(snapshot), TTL)
        return token
    }

    /** Atomic read-and-delete. A quote can authorize exactly one order attempt. */
    fun consume(token: String): QuoteSnapshot? {
        val raw = redisTemplate.opsForValue().getAndDelete(key(token)) ?: return null
        return objectMapper.readValue(raw, QuoteSnapshot::class.java)
    }

    fun delete(token: String) {
        redisTemplate.delete(key(token))
    }

    private fun key(token: String) = "$KEY_PREFIX$token"
}
''',
)

order_service = "backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderService.kt"
text = read(order_service)
text = text.replace(
    '@Value("\\${gateway.trust.secret:}")\n    private val gatewayTrustSecret: String = "",',
    '@Value("\\${internal.api.secret:}")\n    private val internalServiceSecret: String = "",',
)
text = text.replace(
    "            paymentMethod = paymentMethod,\n"
    "            items = request.items.map { QuoteItemSnapshot(it.offeringId, it.quantity) }\n",
    "            paymentMethod = paymentMethod,\n"
    "            deliveryAddressId = request.deliveryAddressId,\n"
    "            loyaltyRewardId = request.loyaltyRewardId,\n"
    "            items = request.items.map { QuoteItemSnapshot(it.offeringId, it.quantity) }\n",
)
old_binding = '''        // Validate immutable snapshot bindings
        if (snapshot.customerId != activeCustomerId && snapshot.customerId.toString() != "00000000-0000-0000-0000-000000000000") {
            if (snapshot.customerId.mostSignificantBits != 0L && snapshot.customerId.leastSignificantBits != 0L) {
                // Ignore dummy random IDs from backward compatibility helper, check matching customerId
            }
        }
        if (snapshot.providerId != request.providerId && snapshot.items.isNotEmpty()) {
            throw IllegalArgumentException("Quote token does not match order provider")
        }
        if (snapshot.items.isNotEmpty()) {
            val reqItemsMap = request.items.associate { it.offeringId to it.quantity }
            val snapItemsMap = snapshot.items.associate { it.offeringId to it.quantity }
            if (reqItemsMap != snapItemsMap) {
                throw IllegalArgumentException("Order items do not match locked-in quote snapshot")
            }
        }
'''
new_binding = '''        // Enforce every security- and money-sensitive quote binding exactly.
        if (snapshot.customerId != activeCustomerId) {
            throw IllegalArgumentException("Quote token belongs to a different customer")
        }
        if (snapshot.providerId != request.providerId) {
            throw IllegalArgumentException("Quote token does not match order provider")
        }
        if (snapshot.deliveryAddressId != request.deliveryAddressId) {
            throw IllegalArgumentException("Delivery address does not match the quote")
        }
        if (snapshot.loyaltyRewardId != request.loyaltyRewardId) {
            throw IllegalArgumentException("Loyalty reward does not match the quote")
        }
        val normalizedCoupon = request.couponCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        if (snapshot.couponCode != normalizedCoupon) {
            throw IllegalArgumentException("Coupon does not match the quote")
        }
        if (snapshot.paymentMethod != paymentMethod) {
            throw IllegalArgumentException("Payment method does not match the quote")
        }
        val reqItemsMap = request.items.associate { it.offeringId to it.quantity }
        val snapItemsMap = snapshot.items.associate { it.offeringId to it.quantity }
        if (reqItemsMap != snapItemsMap || reqItemsMap.size != request.items.size) {
            throw IllegalArgumentException("Order items do not match the locked quote")
        }
'''
if old_binding not in text:
    raise RuntimeError("Order quote binding block did not match")
text = text.replace(old_binding, new_binding, 1)
text = text.replace(
    '''        ).also { freshQuote ->
            if (freshQuote.payableTotal.subtract(snapshot.total).abs() > BigDecimal("1.00")) {
                throw IllegalStateException("Price has changed since your quote. Please request a new quote.")
            }
        }
''',
    '''        ).also { freshQuote ->
            quoteStore?.delete(freshQuote.quoteToken)
            if (freshQuote.payableTotal.compareTo(snapshot.total) != 0) {
                throw IllegalStateException("Price has changed since your quote. Please request a new quote.")
            }
        }
''',
)
# Internal stock API with deterministic idempotency keys.
text = text.replace(
    'val url = "$baseUrl/api/v1/catalog/offerings/$offeringId/decrement-stock?quantity=$quantity"\n'
    '        val entity = org.springframework.http.HttpEntity<Any>(internalHeaders())',
    'val url = "$baseUrl/internal/v1/catalog/offerings/$offeringId/decrement-stock?quantity=$quantity"\n'
    '        val headers = internalHeaders()\n'
    '        headers.set("X-Idempotency-Key", UUID.nameUUIDFromBytes("reserve:$offeringId:$quantity".toByteArray()).toString())\n'
    '        val entity = org.springframework.http.HttpEntity<Any>(headers)',
)
text = text.replace(
    'val url = "$catalogServiceUrl/api/v1/catalog/offerings/${item.offeringId}/restore-stock?quantity=${item.quantity}"\n'
    '                val entity = org.springframework.http.HttpEntity<Any>(internalHeaders())',
    'val url = "$catalogServiceUrl/internal/v1/catalog/offerings/${item.offeringId}/restore-stock?quantity=${item.quantity}"\n'
    '                val headers = internalHeaders()\n'
    '                headers.set("X-Idempotency-Key", UUID.nameUUIDFromBytes("restore:${item.offeringId}:${item.quantity}".toByteArray()).toString())\n'
    '                val entity = org.springframework.http.HttpEntity<Any>(headers)',
)
# Service-to-service identity is distinct from the public gateway.
old_headers = '''    private fun internalHeaders(): org.springframework.http.HttpHeaders {
        val headers = org.springframework.http.HttpHeaders()
        if (gatewayTrustSecret.isNotBlank()) {
            headers.set("X-Internal-Gateway-Secret", gatewayTrustSecret)
            headers.set("X-Internal-Secret", gatewayTrustSecret)
        }
        return headers
    }
'''
new_headers = '''    private fun internalHeaders(): org.springframework.http.HttpHeaders {
        val headers = org.springframework.http.HttpHeaders()
        if (internalServiceSecret.isNotBlank()) {
            headers.set("X-Internal-Secret", internalServiceSecret)
            headers.set("X-Service-Name", "order-service")
        }
        return headers
    }
'''
if old_headers not in text:
    raise RuntimeError("Order internalHeaders block did not match")
text = text.replace(old_headers, new_headers, 1)
write(order_service, text)

# ---------------------------------------------------------------------------
# S25: durable compensation outside the failed order transaction.
# ---------------------------------------------------------------------------
write(
    "backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/model/OrderCompensation.kt",
    r'''package com.pawsnearme.orderservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "order_compensations", schema = "orders")
class OrderCompensation(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "compensation_id")
    var compensationId: UUID? = null,

    @Column(name = "order_id")
    var orderId: UUID? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "coupon_code")
    var couponCode: String? = null,

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    var payloadJson: String,

    @Column(name = "status", nullable = false, length = 32)
    var status: String = "PENDING",

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    var version: Long = 0
)
''',
)

write(
    "backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/repository/OrderCompensationRepository.kt",
    r'''package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.OrderCompensation
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface OrderCompensationRepository : JpaRepository<OrderCompensation, UUID> {
    fun findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(statuses: Collection<String>, now: Instant): List<OrderCompensation>
}
''',
)

write(
    "backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderCompensationService.kt",
    r'''package com.pawsnearme.orderservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.orderservice.model.OrderCompensation
import com.pawsnearme.orderservice.repository.OrderCompensationRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class OrderCompensationService(
    private val repository: OrderCompensationRepository,
    private val objectMapper: ObjectMapper,
    private val restTemplate: RestTemplate,
    @Value("\${CATALOG_SERVICE_URL:http://localhost:8082}") private val catalogServiceUrl: String,
    @Value("\${PAYMENT_SERVICE_URL:http://localhost:8090}") private val paymentServiceUrl: String,
    @Value("\${internal.api.secret:}") private val internalSecret: String
) {
    data class Item(val offeringId: UUID, val quantity: Int)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(orderId: UUID?, customerId: UUID, couponCode: String?, items: List<OrderItemRequest>) {
        if (items.isEmpty() && couponCode.isNullOrBlank()) return
        repository.saveAndFlush(
            OrderCompensation(
                orderId = orderId,
                customerId = customerId,
                couponCode = couponCode?.trim()?.uppercase(),
                payloadJson = objectMapper.writeValueAsString(items.map { Item(it.offeringId, it.quantity) })
            )
        )
    }

    @Scheduled(fixedDelayString = "\${order.compensation.poll-delay-ms:5000}")
    @SchedulerLock(name = "orderCompensationWorker", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1S")
    fun runPending() {
        repository.findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(
            listOf("PENDING", "RETRY"), Instant.now()
        ).forEach(::processOne)
    }

    @Transactional
    fun processOne(compensation: OrderCompensation) {
        compensation.status = "PROCESSING"
        compensation.updatedAt = Instant.now()
        repository.saveAndFlush(compensation)
        try {
            val items: List<Item> = objectMapper.readValue(
                compensation.payloadJson, object : TypeReference<List<Item>>() {}
            )
            items.forEach { item ->
                val headers = internalHeaders()
                val keyMaterial = "compensate:${compensation.compensationId}:${item.offeringId}:${item.quantity}"
                headers.set("X-Idempotency-Key", UUID.nameUUIDFromBytes(keyMaterial.toByteArray()).toString())
                val url = "$catalogServiceUrl/internal/v1/catalog/offerings/${item.offeringId}/restore-stock?quantity=${item.quantity}"
                restTemplate.exchange(url, HttpMethod.PUT, HttpEntity<Any>(headers), Map::class.java)
            }
            val coupon = compensation.couponCode
            val orderId = compensation.orderId
            if (!coupon.isNullOrBlank() && orderId != null) {
                val url = UriComponentsBuilder.fromUriString("$paymentServiceUrl/api/v1/payments/promotions/release")
                    .queryParam("code", coupon)
                    .queryParam("userId", compensation.customerId)
                    .queryParam("orderId", orderId)
                    .build().encode().toUriString()
                restTemplate.postForEntity(url, HttpEntity<Any>(internalHeaders()), Map::class.java)
            }
            compensation.status = "COMPENSATED"
            compensation.lastError = null
        } catch (error: Exception) {
            compensation.attemptCount += 1
            compensation.lastError = error.message?.take(4000)
            compensation.status = if (compensation.attemptCount >= 12) "FAILED" else "RETRY"
            val delay = Duration.ofSeconds((1L shl compensation.attemptCount.coerceAtMost(8)).coerceAtMost(300))
            compensation.nextAttemptAt = Instant.now().plus(delay)
            logger.error("Compensation {} failed on attempt {}", compensation.compensationId, compensation.attemptCount, error)
        }
        compensation.updatedAt = Instant.now()
        repository.save(compensation)
    }

    private fun internalHeaders(): HttpHeaders = HttpHeaders().also {
        require(internalSecret.isNotBlank()) { "Internal service secret is not configured" }
        it.set("X-Internal-Secret", internalSecret)
        it.set("X-Service-Name", "order-service")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OrderCompensationService::class.java)
    }
}
''',
)

write(
    "backend/order-service/src/main/resources/db/migration/V7__durable_order_compensation.sql",
    r'''CREATE TABLE IF NOT EXISTS orders.order_compensations (
    compensation_id UUID PRIMARY KEY,
    order_id UUID NULL,
    customer_id UUID NOT NULL,
    coupon_code VARCHAR(64) NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_order_compensations_pending
    ON orders.order_compensations(status, next_attempt_at, created_at);
''',
)

text = read(order_service)
# Add optional dependency at end to preserve existing direct unit-test construction.
text = text.replace(
    "    private val restTemplate: RestTemplate,\n    private val quoteStore: QuoteStore? = null\n) {",
    "    private val restTemplate: RestTemplate,\n"
    "    private val quoteStore: QuoteStore? = null,\n"
    "    private val compensationService: OrderCompensationService? = null\n) {",
)
old_catch = '''        } catch (e: Exception) {
            recordStockAndCouponCompensation(
                orderId = savedOrderId,
                customerId = activeCustomerId,
                couponCode = if (couponReserved) request.couponCode else null,
                reservedItems = reservedItems
            )
            if (couponReserved && !request.couponCode.isNullOrBlank() && savedOrderId != null) {
                releaseCouponReservation(
                    request.couponCode.trim().uppercase(),
                    activeCustomerId,
                    savedOrderId
                )
            }
            restoreReservedCatalogStock(reservedItems)
            throw e
        }
    }

    private fun recordStockAndCouponCompensation(
        orderId: UUID?,
        customerId: UUID,
        couponCode: String?,
        reservedItems: List<OrderItemRequest>
    ) {
        try {
            val payload = mapOf(
                "orderId" to (orderId ?: UUID.randomUUID()),
                "customerId" to customerId,
                "couponCode" to couponCode,
                "items" to reservedItems.map { mapOf("offeringId" to it.offeringId, "quantity" to it.quantity) }
            )
            outboxService.saveEvent(
                aggregateType = "ORDER",
                aggregateId = orderId ?: customerId,
                eventType = "COMPENSATE_STOCK_AND_COUPON",
                eventPayload = payload
            )
            logger.info("Durable compensation outbox event recorded for order {}", orderId)
        } catch (ex: Exception) {
            logger.warn("Could not record durable compensation outbox event: {}", ex.message)
        }
    }
'''
new_catch = '''        } catch (e: Exception) {
            try {
                compensationService?.recordFailure(
                    orderId = savedOrderId,
                    customerId = activeCustomerId,
                    couponCode = if (couponReserved) request.couponCode else null,
                    items = reservedItems
                ) ?: throw IllegalStateException("Durable compensation service is unavailable")
            } catch (recordingFailure: Exception) {
                logger.error("CRITICAL: Failed to durably record order compensation", recordingFailure)
                throw IllegalStateException(
                    "Order failed and compensation could not be recorded safely",
                    recordingFailure
                ).also { it.addSuppressed(e) }
            }
            throw e
        }
    }
'''
if old_catch not in text:
    raise RuntimeError("Order compensation block did not match")
write(order_service, text.replace(old_catch, new_catch, 1))

# ---------------------------------------------------------------------------
# S25: payment idempotency is mandatory and scanned in the real context.
# ---------------------------------------------------------------------------
payment_app = "backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/PaymentServiceApplication.kt"
replace_once(
    payment_app,
    '@ComponentScan(basePackages = ["com.pawsnearme.paymentservice", "com.pawsnearme.common.security"])',
    '@ComponentScan(basePackages = ["com.pawsnearme.paymentservice", "com.pawsnearme.common.security", "com.pawsnearme.common.idempotency"])',
)

payment_service = "backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/service/PaymentService.kt"
text = read(payment_service)
text = text.replace("import org.springframework.beans.factory.annotation.Autowired\n", "")
text = text.replace(
    "    @Autowired(required = false)\n    private val idempotencyService: IdempotencyService? = null,",
    "    private val idempotencyService: IdempotencyService,",
)
text = text.replace(
    'val eventIdRaw = eventIdHeader ?: eventMap["id"] as? String ?: (eventMap["event"] as? String ?: "evt") + "_" + System.currentTimeMillis()',
    'val eventIdRaw = eventIdHeader?.trim()?.takeIf { it.isNotBlank() }\n'
    '            ?: throw IllegalArgumentException("Missing X-Razorpay-Event-Id header")',
)
text = text.replace(
    "        if (idempotencyService != null && !idempotencyService.checkAndRecord(deterministicUuid)) {",
    "        if (!idempotencyService.checkAndRecord(deterministicUuid)) {",
)
write(payment_service, text)

# Add scheduler lock to payout scheduler if it is currently unlocked.
payout_scheduler = ROOT / "backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/scheduler/PayoutScheduler.kt"
if payout_scheduler.exists():
    text = payout_scheduler.read_text(encoding="utf-8")
    if "SchedulerLock" not in text:
        text = text.replace(
            "import org.springframework.scheduling.annotation.Scheduled\n",
            "import org.springframework.scheduling.annotation.Scheduled\nimport net.javacrumbs.shedlock.spring.annotation.SchedulerLock\n",
        )
        text = re.sub(
            r"(@Scheduled\([^\n]+\)\n)(\s*)(fun )",
            r'\1\2@SchedulerLock(name = "paymentPayoutScheduler", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")\n\2\3',
            text,
            count=1,
        )
        payout_scheduler.write_text(text, encoding="utf-8")
        print(f"updated {payout_scheduler.relative_to(ROOT)}")

# ---------------------------------------------------------------------------
# S28: coherent Spring Boot BOM, declared merchant test runtime, CI coverage.
# ---------------------------------------------------------------------------
for gradle in (ROOT / "backend").rglob("build.gradle.kts"):
    text = gradle.read_text(encoding="utf-8")
    updated = text.replace("3.2.3", "3.5.14").replace("3.4.3", "3.5.14")
    if updated != text:
        gradle.write_text(updated, encoding="utf-8")
        print(f"updated {gradle.relative_to(ROOT)}")

merchant_pkg_path = ROOT / "apps/merchant-captain-app/package.json"
merchant_pkg = json.loads(merchant_pkg_path.read_text(encoding="utf-8"))
merchant_pkg.setdefault("devDependencies", {})["tsx"] = "^4.20.6"
merchant_pkg["scripts"]["test"] = "tsx --test src/__tests__/*.test.ts"
merchant_pkg_path.write_text(json.dumps(merchant_pkg, indent=2) + "\n", encoding="utf-8")
print("updated apps/merchant-captain-app/package.json")

ci = ".github/workflows/ci.yml"
text = read(ci)
text = text.replace(
    "          npm run typecheck\n          npm run lint\n",
    "          npm run typecheck\n          npm run lint\n          npm test\n",
    1,
)
# Ensure merchant job also runs tests (second occurrence may not have been replaced).
merchant_marker = "      - name: Validate Merchant/Captain App\n        run: |\n          cd apps/merchant-captain-app\n          npm ci\n          npm run typecheck\n          npm run lint\n"
if merchant_marker in text:
    text = text.replace(merchant_marker, merchant_marker + "          npm test\n", 1)
write(ci, text)

# ---------------------------------------------------------------------------
# S27: fail-closed NetworkPolicies matching real communication categories.
# ---------------------------------------------------------------------------
write(
    "infra/k8s/network-policy.yaml",
    r'''apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
spec:
  podSelector: {}
  policyTypes: [Ingress]
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-egress
spec:
  podSelector: {}
  policyTypes: [Egress]
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-dns-and-https-egress
spec:
  podSelector: {}
  policyTypes: [Egress]
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: kube-system
      ports:
        - { protocol: UDP, port: 53 }
        - { protocol: TCP, port: 53 }
    # Kubernetes NetworkPolicy is L3/L4 only. Restrict external HTTPS further
    # with an egress gateway/FQDN-aware policy in the target cluster.
    - ports:
        - { protocol: TCP, port: 443 }
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-gateway-to-backends
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: In
        values: [provider-service, catalog-service, discovery-service, order-service, appointment-service, dispatch-service, captain-service, notification-service, review-service, payment-service, chat-service, content-service]
  policyTypes: [Ingress]
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: api-gateway
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-backend-service-mesh
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: In
        values: [provider-service, catalog-service, discovery-service, order-service, appointment-service, dispatch-service, captain-service, notification-service, review-service, payment-service, chat-service, content-service]
  policyTypes: [Ingress, Egress]
  ingress:
    - from:
        - podSelector:
            matchExpressions:
              - key: app
                operator: In
                values: [provider-service, catalog-service, discovery-service, order-service, appointment-service, dispatch-service, captain-service, notification-service, review-service, payment-service, chat-service, content-service]
  egress:
    - to:
        - podSelector:
            matchExpressions:
              - key: app
                operator: In
                values: [provider-service, catalog-service, discovery-service, order-service, appointment-service, dispatch-service, captain-service, notification-service, review-service, payment-service, chat-service, content-service]
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-backends-to-datastores
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: In
        values: [provider-service, catalog-service, discovery-service, order-service, appointment-service, dispatch-service, captain-service, notification-service, review-service, payment-service, chat-service, content-service, api-gateway]
  policyTypes: [Egress]
  egress:
    - to:
        - podSelector:
            matchExpressions:
              - key: app
                operator: In
                values: [postgres, redis, kafka]
      ports:
        - { protocol: TCP, port: 5432 }
        - { protocol: TCP, port: 6379 }
        - { protocol: TCP, port: 9092 }
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-datastore-ingress
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: In
        values: [postgres, redis, kafka]
  policyTypes: [Ingress]
  ingress:
    - from:
        - podSelector:
            matchExpressions:
              - key: app
                operator: In
                values: [provider-service, catalog-service, discovery-service, order-service, appointment-service, dispatch-service, captain-service, notification-service, review-service, payment-service, chat-service, content-service, api-gateway]
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-prometheus-scrapes
spec:
  podSelector:
    matchLabels:
      metrics: enabled
  policyTypes: [Ingress]
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              monitoring: "true"
      ports:
        - { protocol: TCP, port: 8080 }
        - { protocol: TCP, port: 8081 }
        - { protocol: TCP, port: 8082 }
        - { protocol: TCP, port: 8083 }
        - { protocol: TCP, port: 8084 }
        - { protocol: TCP, port: 8085 }
        - { protocol: TCP, port: 8086 }
        - { protocol: TCP, port: 8087 }
        - { protocol: TCP, port: 8088 }
        - { protocol: TCP, port: 8089 }
        - { protocol: TCP, port: 8090 }
        - { protocol: TCP, port: 8091 }
        - { protocol: TCP, port: 8092 }
''',
)

# Production deployment manifests: remove placeholder registry/latest and add
# baseline pod/container hardening plus requests where absent. Digests remain
# explicit variables that release validation must replace before apply.
backend_manifest = ROOT / "infra/k8s/backend-services.yaml"
if backend_manifest.exists():
    text = backend_manifest.read_text(encoding="utf-8")
    text = text.replace("ghcr.io/your-org/", "ghcr.io/thrinnadhh/")
    text = re.sub(r"(image: ghcr\.io/thrinnadhh/[^:\s]+):latest", r"\1@sha256:REQUIRED_IMAGE_DIGEST", text)
    # Label all backend pods for metrics policy.
    text = re.sub(r"(\n\s+labels:\n\s+app: [^\n]+)", r"\1\n        metrics: enabled", text)
    # Add pod security context after each pod spec if absent in that block.
    text = text.replace(
        "    spec:\n      containers:\n",
        "    spec:\n"
        "      securityContext:\n"
        "        runAsNonRoot: true\n"
        "        seccompProfile:\n"
        "          type: RuntimeDefault\n"
        "      containers:\n",
    )
    # Add container security context before ports.
    text = re.sub(
        r"(\n\s+- name: ([^\n]+)\n\s+image: [^\n]+\n)(\s+ports:)",
        r"\1        securityContext:\n          allowPrivilegeEscalation: false\n          readOnlyRootFilesystem: true\n          capabilities:\n            drop: [\"ALL\"]\n\3",
        text,
    )
    backend_manifest.write_text(text, encoding="utf-8")
    print("updated infra/k8s/backend-services.yaml")

# A deterministic verifier fails closed on placeholders, unavailable services,
# unexpected 5xx responses, leaked secrets, or inconsistent framework versions.
write(
    "backend/verify_release_gates.py",
    r'''#!/usr/bin/env python3
from __future__ import annotations
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

def fail(message: str) -> None:
    failures.append(message)
    print(f"FAIL: {message}")

def check_http(name: str, url: str, expected: set[int], headers: dict[str, str] | None = None) -> None:
    try:
        with urlopen(Request(url, headers=headers or {}), timeout=5) as response:
            status = response.status
    except HTTPError as error:
        status = error.code
    except (URLError, TimeoutError, OSError) as error:
        fail(f"{name} is unavailable: {error}")
        return
    if status >= 500 or status not in expected:
        fail(f"{name} returned HTTP {status}; expected {sorted(expected)} and never 5xx")

for client in [
    ROOT / "apps/customer-app/src/services/api-client.ts",
    ROOT / "apps/merchant-captain-app/src/services/api-client.ts",
]:
    source = client.read_text(encoding="utf-8")
    if "X-Internal-Gateway-Secret" in source or "setGatewaySecret" in source:
        fail(f"internal gateway credential remains in {client.relative_to(ROOT)}")

manifest = ROOT / "infra/k8s/backend-services.yaml"
if manifest.exists():
    value = manifest.read_text(encoding="utf-8")
    for marker in ["your-org", ":latest", "REQUIRED_IMAGE_DIGEST"]:
        if marker in value:
            fail(f"deployment manifest contains unresolved marker: {marker}")

versions = set()
for gradle in (ROOT / "backend").rglob("build.gradle.kts"):
    versions.update(re.findall(r"spring-boot-dependencies:([0-9.]+)", gradle.read_text(encoding="utf-8")))
if len(versions) > 1:
    fail(f"multiple Spring Boot BOM versions found: {sorted(versions)}")

if os.getenv("RUN_LIVE_SMOKE_TESTS") == "true":
    base = os.environ["STAGING_GATEWAY_URL"].rstrip("/")
    check_http("gateway readiness", f"{base}/actuator/health/readiness", {200})
    check_http("unauthenticated admin rejection", f"{base}/api/v1/providers/pending", {401, 403})

report = {"passed": not failures, "failures": failures}
(ROOT / "backend/release-gate-report.json").write_text(json.dumps(report, indent=2) + "\n")
if failures:
    sys.exit(1)
print("All configured release gates passed.")
''',
)

write(
    "backend/scan_dependencies.py",
    r'''#!/usr/bin/env python3
from __future__ import annotations
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
versions: dict[str, list[str]] = {}
for gradle in (ROOT / "backend").rglob("build.gradle.kts"):
    text = gradle.read_text(encoding="utf-8")
    for version in re.findall(r"spring-boot-dependencies:([0-9.]+)", text):
        versions.setdefault(version, []).append(str(gradle.relative_to(ROOT)))
root_gradle = (ROOT / "backend/build.gradle.kts").read_text(encoding="utf-8")
plugin = re.search(r'org\.springframework\.boot"\) version "([0-9.]+)"', root_gradle)
if plugin:
    versions.setdefault(plugin.group(1), []).append("backend/build.gradle.kts (plugin)")
if len(versions) != 1:
    print(json.dumps({"error": "inconsistent Spring Boot versions", "versions": versions}, indent=2))
    sys.exit(1)
print(json.dumps({"springBootVersion": next(iter(versions)), "files": sum(versions.values(), [])}, indent=2))
''',
)

# Static release gate during CI, excluding digest resolution which occurs in a
# deployment pipeline after images have been built.
write(
    "scripts/check-production-hardening.sh",
    r'''#!/usr/bin/env bash
set -euo pipefail

! grep -R --line-number --exclude-dir=node_modules --exclude='*.md' \
  'X-Internal-Gateway-Secret' apps/customer-app apps/merchant-captain-app
! grep -R --line-number --exclude-dir=node_modules 'dev-gateway-secret-key' apps
! grep -R --line-number 'ghcr.io/your-org' infra/k8s
! grep -R --line-number 'image: .*:latest' infra/k8s
python3 backend/scan_dependencies.py
''',
)

# Remove executable-bit dependency by explicitly invoking bash/python in CI.
text = read(ci)
if "Production hardening static gates" not in text:
    text = text.replace(
        "      - name: Check Generated Artifacts\n        run: scripts/check-no-generated-artifacts.sh\n",
        "      - name: Check Generated Artifacts\n        run: scripts/check-no-generated-artifacts.sh\n\n"
        "      - name: Production hardening static gates\n"
        "        run: bash scripts/check-production-hardening.sh\n",
    )
write(ci, text)

print("Production hardening codemod completed successfully.")
