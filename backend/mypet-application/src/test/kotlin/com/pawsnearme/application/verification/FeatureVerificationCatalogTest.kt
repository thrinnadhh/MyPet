package com.pawsnearme.application.verification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeatureVerificationCatalogTest {
    private val catalog = MyPetFeatureVerificationCatalog.create()

    @Test
    fun `catalog covers every M8 business domain exactly once`() {
        assertEquals(
            setOf(
                "customer", "provider", "catalog", "appointment", "order", "payment",
                "loyalty", "captain", "dispatch", "review", "notification", "chat",
                "content", "admin"
            ),
            catalog.domains.mapTo(sortedSetOf(), FeatureVerificationDomain::id)
        )
        assertEquals(14, catalog.domains.size)
    }

    @Test
    fun `matrix includes critical failure-mode evidence`() {
        val evidence = catalog.domains.flatMapTo(mutableSetOf()) { it.evidence }

        assertTrue(VerificationEvidenceKind.AUTHORIZATION_BOUNDARY in evidence)
        assertTrue(VerificationEvidenceKind.ASYNC_PROJECTION in evidence)
        assertTrue(VerificationEvidenceKind.IDEMPOTENCY in evidence)
        assertTrue(VerificationEvidenceKind.CONCURRENCY in evidence)
        assertTrue(VerificationEvidenceKind.SCHEDULER in evidence)
    }

    @Test
    fun `every domain has contract and state evidence`() {
        catalog.domains.forEach { domain ->
            assertTrue(
                VerificationEvidenceKind.HTTP_CONTRACT in domain.evidence,
                "${domain.id} must verify its HTTP contract"
            )
            assertFalse(domain.scenario.isBlank())
        }
    }
}
