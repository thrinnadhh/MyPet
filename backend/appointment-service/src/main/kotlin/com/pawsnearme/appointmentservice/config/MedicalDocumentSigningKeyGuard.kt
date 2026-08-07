package com.pawsnearme.appointmentservice.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Signed medical-document content is intentionally reachable without a JWT.
 * The short-lived HMAC token is therefore the authorization boundary and must
 * never use the source-code development fallback in a running application.
 */
@Component
class MedicalDocumentSigningKeyGuard(
    @Value("\${MEDICAL_DOCUMENT_SIGNING_KEY:local-development-key}") signingKey: String,
) {
    init {
        require(signingKey != LOCAL_DEVELOPMENT_KEY) {
            "MEDICAL_DOCUMENT_SIGNING_KEY must be explicitly configured; the development fallback is not allowed."
        }
        require(signingKey.length >= 32) {
            "MEDICAL_DOCUMENT_SIGNING_KEY must contain at least 32 characters."
        }
    }

    companion object {
        private const val LOCAL_DEVELOPMENT_KEY = "local-development-key"
    }
}
