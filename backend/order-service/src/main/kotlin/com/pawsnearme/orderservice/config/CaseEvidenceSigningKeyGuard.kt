package com.pawsnearme.orderservice.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Customer-case evidence content is served through short-lived HMAC URLs.
 * Refuse to start if the service would use the known development signing key.
 */
@Component
class CaseEvidenceSigningKeyGuard(
    @Value("\${CASE_EVIDENCE_SIGNING_KEY:local-development-key}") signingKey: String,
) {
    init {
        require(signingKey != LOCAL_DEVELOPMENT_KEY) {
            "CASE_EVIDENCE_SIGNING_KEY must be explicitly configured; the development fallback is not allowed."
        }
        require(signingKey.length >= 32) {
            "CASE_EVIDENCE_SIGNING_KEY must contain at least 32 characters."
        }
    }

    companion object {
        private const val LOCAL_DEVELOPMENT_KEY = "local-development-key"
    }
}
