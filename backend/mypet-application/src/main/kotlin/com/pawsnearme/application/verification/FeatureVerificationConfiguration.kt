package com.pawsnearme.application.verification

import org.springframework.boot.actuate.info.Info
import org.springframework.boot.actuate.info.InfoContributor
import org.springframework.stereotype.Component

enum class VerificationEvidenceKind {
    HTTP_CONTRACT,
    DATABASE_STATE,
    AUTHORIZATION_BOUNDARY,
    ASYNC_PROJECTION,
    IDEMPOTENCY,
    CONCURRENCY,
    SCHEDULER
}

data class FeatureVerificationDomain(
    val id: String,
    val scenario: String,
    val evidence: Set<VerificationEvidenceKind>
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9-]*"))) { "Invalid feature domain id: $id" }
        require(scenario.isNotBlank()) { "Feature verification scenario cannot be blank" }
        require(evidence.isNotEmpty()) { "Feature verification evidence cannot be empty" }
    }
}

class FeatureVerificationCatalog(domains: Collection<FeatureVerificationDomain>) {
    val domains: List<FeatureVerificationDomain> = domains.sortedBy(FeatureVerificationDomain::id)

    init {
        require(this.domains.isNotEmpty()) { "At least one feature domain is required" }
        require(this.domains.map(FeatureVerificationDomain::id).distinct().size == this.domains.size) {
            "Feature verification domain ids must be unique"
        }
        require(this.domains.any { VerificationEvidenceKind.AUTHORIZATION_BOUNDARY in it.evidence }) {
            "M8 must verify at least one authorization boundary"
        }
        require(this.domains.any { VerificationEvidenceKind.ASYNC_PROJECTION in it.evidence }) {
            "M8 must verify at least one asynchronous projection"
        }
        require(this.domains.any { VerificationEvidenceKind.IDEMPOTENCY in it.evidence }) {
            "M8 must verify at least one idempotent workflow"
        }
        require(this.domains.any { VerificationEvidenceKind.CONCURRENCY in it.evidence }) {
            "M8 must verify at least one concurrency boundary"
        }
    }
}

object MyPetFeatureVerificationCatalog {
    fun create(): FeatureVerificationCatalog = FeatureVerificationCatalog(
        listOf(
            domain("customer", "profile, address and favourite lifecycle", HTTP_CONTRACT, DATABASE_STATE),
            domain("provider", "merchant ownership and admin approval", HTTP_CONTRACT, AUTHORIZATION_BOUNDARY, DATABASE_STATE),
            domain("catalog", "offering, stock and appointment slot lifecycle", HTTP_CONTRACT, DATABASE_STATE),
            domain("appointment", "hold, double-book prevention, confirmation, completion, invoice and timeout", HTTP_CONTRACT, CONCURRENCY, SCHEDULER, DATABASE_STATE),
            domain("order", "quote-token checkout, stock reservation and delivered status", HTTP_CONTRACT, DATABASE_STATE, ASYNC_PROJECTION),
            domain("payment", "captured transaction and durable payment event", HTTP_CONTRACT, DATABASE_STATE, ASYNC_PROJECTION),
            domain("loyalty", "delivery award, progress and duplicate-event rejection", HTTP_CONTRACT, IDEMPOTENCY, DATABASE_STATE),
            domain("captain", "onboarding, protected bank data and online availability", HTTP_CONTRACT, AUTHORIZATION_BOUNDARY, DATABASE_STATE),
            domain("dispatch", "offer, acceptance, OTP pickup and delivery propagation", HTTP_CONTRACT, ASYNC_PROJECTION, DATABASE_STATE),
            domain("review", "completed appointment review and provider aggregate", HTTP_CONTRACT, ASYNC_PROJECTION, DATABASE_STATE),
            domain("notification", "appointment reminder and push token lifecycle", HTTP_CONTRACT, ASYNC_PROJECTION, DATABASE_STATE),
            domain("chat", "customer and merchant conversation, message and read state", HTTP_CONTRACT, AUTHORIZATION_BOUNDARY, DATABASE_STATE),
            domain("content", "admin publishing and public reads", HTTP_CONTRACT, AUTHORIZATION_BOUNDARY, DATABASE_STATE),
            domain("admin", "provider approval and profile revoke/restore", HTTP_CONTRACT, AUTHORIZATION_BOUNDARY, DATABASE_STATE)
        )
    )

    private fun domain(
        id: String,
        scenario: String,
        vararg evidence: VerificationEvidenceKind
    ) = FeatureVerificationDomain(id, scenario, evidence.toSet())

    private val HTTP_CONTRACT = VerificationEvidenceKind.HTTP_CONTRACT
    private val DATABASE_STATE = VerificationEvidenceKind.DATABASE_STATE
    private val AUTHORIZATION_BOUNDARY = VerificationEvidenceKind.AUTHORIZATION_BOUNDARY
    private val ASYNC_PROJECTION = VerificationEvidenceKind.ASYNC_PROJECTION
    private val IDEMPOTENCY = VerificationEvidenceKind.IDEMPOTENCY
    private val CONCURRENCY = VerificationEvidenceKind.CONCURRENCY
    private val SCHEDULER = VerificationEvidenceKind.SCHEDULER
}

@Component
class FeatureVerificationInfoContributor : InfoContributor {
    private val catalog = MyPetFeatureVerificationCatalog.create()

    override fun contribute(builder: Info.Builder) {
        builder.withDetail(
            "featureVerification",
            mapOf(
                "milestone" to "M8",
                "mode" to "clean-volume-connected-matrix",
                "domainCount" to catalog.domains.size,
                "domains" to catalog.domains.map { domain ->
                    mapOf(
                        "id" to domain.id,
                        "scenario" to domain.scenario,
                        "evidence" to domain.evidence.map(Enum<*>::name).sorted()
                    )
                },
                "cutoverAuthorized" to false,
                "legacyRollbackRequired" to true
            )
        )
    }
}
