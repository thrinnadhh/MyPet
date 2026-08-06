package com.pawsnearme.application.edge

import org.springframework.http.HttpMethod
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

/**
 * Public edge matchers for documents whose controllers independently validate
 * short-lived HMAC signatures. All mutation and metadata endpoints remain on
 * the authenticated application security chain.
 */
internal object SignedContentRequestMatchers {
    private val paths = PathPatternRequestMatcher.withDefaults()

    val customerCaseEvidence: RequestMatcher = paths.matcher(
        HttpMethod.GET,
        "/api/v1/orders/customer-cases/evidence/{evidenceId}/content",
    )

    val medicalDocument: RequestMatcher = paths.matcher(
        HttpMethod.GET,
        "/api/v1/appointments/medical-documents/{documentId}/content",
    )

    val hostedCheckout: RequestMatcher = paths.matcher(
        HttpMethod.GET,
        "/api/v1/payments/checkout/**",
    )
}
