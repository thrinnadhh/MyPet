#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text.rstrip() + '\n', encoding='utf-8')
    print(f'fixed {path}')


# Mobile API clients must only forward the authenticated bearer token. The gateway derives identity.
for path in [
    'apps/customer-app/src/services/api-client.ts',
    'apps/merchant-captain-app/src/services/api-client.ts',
]:
    text = read(path)
    text = re.sub(r'\n\s*private userId: string \| null = [^;]+;', '', text)
    text = re.sub(r'\n\s*private userRole: string \| null = [^;]+;', '', text)
    text = re.sub(r'\n\s*public setUserContext\([^)]*\) \{.*?\n\s*\}', '', text, flags=re.S)
    text = re.sub(r'\n\s*if \(this\.userId\) \{.*?\n\s*\}', '', text, flags=re.S)
    text = re.sub(r'\n\s*if \(this\.userRole\) \{.*?\n\s*\}', '', text, flags=re.S)
    if 'this.userId' in text or 'this.userRole' in text or 'setUserContext' in text:
        raise RuntimeError(f'client-controlled identity remains in {path}')
    write(path, text)

legacy_generated = ROOT / 'backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/InternalStockController.kt'
legacy_generated.unlink(missing_ok=True)
write(
    'backend/catalog-service/src/main/kotlin/com/pawsnearme/catalogservice/controller/InternalCatalogController.kt',
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
@RequestMapping("/api/v1/internal/catalog")
class InternalCatalogController(
    private val mutationService: InternalStockMutationService,
    @Value("\${internal.api.secret}") private val internalSecret: String
) {
    @PutMapping("/offerings/{offeringId}/decrement-stock")
    fun decrementStockInternal(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-Service-Name", required = false) serviceName: String?,
        @RequestHeader("X-Internal-Secret", required = false) suppliedSecret: String?,
        @RequestHeader("X-Idempotency-Key", required = false) idempotencyKey: UUID?
    ): ResponseEntity<Offering> = mutate(
        offeringId, quantity, serviceName, suppliedSecret, idempotencyKey, "DECREMENT"
    )

    @PutMapping("/offerings/{offeringId}/restore-stock")
    fun restoreStockInternal(
        @PathVariable offeringId: UUID,
        @RequestParam quantity: Int,
        @RequestHeader("X-Service-Name", required = false) serviceName: String?,
        @RequestHeader("X-Internal-Secret", required = false) suppliedSecret: String?,
        @RequestHeader("X-Idempotency-Key", required = false) idempotencyKey: UUID?
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
        if (serviceName != "order-service" || suppliedSecret == null ||
            !MessageDigest.isEqual(suppliedSecret.toByteArray(), internalSecret.toByteArray())
        ) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val key = idempotencyKey ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(mutationService.mutate(key, offeringId, quantity, operation))
    }
}
'''
)

for path in [
    'backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderService.kt',
    'backend/order-service/src/main/kotlin/com/pawsnearme/orderservice/service/OrderCompensationService.kt',
]:
    text = read(path)
    text = text.replace('/internal/v1/catalog/', '/api/v1/internal/catalog/')
    text = text.replace('/api/v1/catalog/offerings/', '/api/v1/internal/catalog/offerings/')
    write(path, text)

payment_build = 'backend/payment-service/build.gradle.kts'
text = read(payment_build)
if 'shedlock-spring' not in text:
    marker = '    testRuntimeOnly("org.junit.platform:junit-platform-launcher")\n'
    if marker not in text:
        raise RuntimeError('payment build dependency insertion point missing')
    text = text.replace(
        marker,
        marker + '\n    implementation("net.javacrumbs.shedlock:shedlock-spring:5.10.2")\n'
        '    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.10.2")\n',
    )
write(payment_build, text)
write(
    'backend/payment-service/src/main/kotlin/com/pawsnearme/paymentservice/config/ShedLockConfig.kt',
    r'''package com.pawsnearme.paymentservice.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class ShedLockConfig {
    @Bean
    fun lockProvider(dataSource: DataSource): LockProvider =
        JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(JdbcTemplate(dataSource))
                .usingDbTime()
                .build()
        )
}
'''
)

gateway_test = 'backend/api-gateway/src/test/kotlin/com/pawsnearme/apigateway/filter/AuthenticationHeaderFilterTests.kt'
text = read(gateway_test)
text = text.replace(
    'ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext))',
    'ReactiveSecurityContextHolder.withAuthentication(authentication)',
)
text = text.replace('.header("X-Admin-Api-Key", "legacy-key")',
                    '.header("X-Admin-Api-Key", "legacy-key")\n'
                    '                .header("X-Internal-Gateway-Secret", "forged")\n'
                    '                .header("X-Internal-Secret", "forged")\n'
                    '                .header("X-Service-Name", "order-service")')
text = text.replace('assertNull(headers.getFirst("X-Admin-Api-Key"))',
                    'assertNull(headers.getFirst("X-Admin-Api-Key"))\n'
                    '        assertNull(headers.getFirst("X-Internal-Gateway-Secret"))\n'
                    '        assertNull(headers.getFirst("X-Internal-Secret"))\n'
                    '        assertNull(headers.getFirst("X-Service-Name"))')
write(gateway_test, text)

catalog_test = 'backend/catalog-service/src/test/kotlin/com/pawsnearme/catalogservice/controller/CatalogAuthorizationWebMvcTest.kt'
text = read(catalog_test)
if 'InternalStockMutationService' not in text:
    text = text.replace(
        'import com.pawsnearme.catalogservice.service.CatalogService\n',
        'import com.pawsnearme.catalogservice.service.CatalogService\n'
        'import com.pawsnearme.catalogservice.service.InternalStockMutationService\n',
    )
    text = text.replace(
        '    private lateinit var catalogService: CatalogService\n',
        '    private lateinit var catalogService: CatalogService\n\n'
        '    @MockBean\n'
        '    private lateinit var internalStockMutationService: InternalStockMutationService\n',
    )
text = text.replace(
    'fun `decrementStock - internal secret header succeeds with 200`() {',
    'fun `decrementStock - internal secret cannot bypass merchant ownership`() {',
)
text = text.replace(
    '.andExpect(status().isOk)\n    }\n\n    @Test\n    fun `decrementStockInternal - invalid secret returns 403`',
    '.andExpect(status().isForbidden)\n    }\n\n    @Test\n    fun `decrementStockInternal - invalid secret returns 403`',
    1,
)
text = text.replace(
    '.header("X-Internal-Secret", "wrong-secret")',
    '.header("X-Internal-Secret", "wrong-secret")\n'
    '                .header("X-Service-Name", "order-service")\n'
    '                .header("X-Idempotency-Key", UUID.randomUUID().toString())',
)
text = text.replace(
    'whenever(catalogService.decrementStock(eq(offeringId), eq(2))).thenReturn(sampleOffering)\n\n'
    '        mockMvc.perform(\n'
    '            put("/api/v1/internal/catalog/offerings/$offeringId/decrement-stock?quantity=2")\n'
    '                .header("X-Internal-Secret", "dev-internal-secret")',
    'val idempotencyKey = UUID.randomUUID()\n'
    '        whenever(internalStockMutationService.mutate(eq(idempotencyKey), eq(offeringId), eq(2), eq("DECREMENT")))\n'
    '            .thenReturn(sampleOffering)\n\n'
    '        mockMvc.perform(\n'
    '            put("/api/v1/internal/catalog/offerings/$offeringId/decrement-stock?quantity=2")\n'
    '                .header("X-Internal-Secret", "dev-internal-secret")\n'
    '                .header("X-Service-Name", "order-service")\n'
    '                .header("X-Idempotency-Key", idempotencyKey.toString())',
)
write(catalog_test, text)

order_test = 'backend/order-service/src/test/kotlin/com/pawsnearme/orderservice/service/OrderServiceTests.kt'
text = read(order_test).replace('/api/v1/catalog/offerings/', '/api/v1/internal/catalog/offerings/')
write(order_test, text)

print('Verification fixes applied.')
