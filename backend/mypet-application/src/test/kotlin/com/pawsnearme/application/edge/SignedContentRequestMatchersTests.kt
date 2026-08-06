package com.pawsnearme.application.edge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class SignedContentRequestMatchersTests {
    @Test
    fun `case evidence matcher permits only the signed content GET path`() {
        assertThat(
            SignedContentRequestMatchers.customerCaseEvidence.matches(
                request("GET", "/api/v1/orders/customer-cases/evidence/evidence-123/content"),
            ),
        ).isTrue()
        assertThat(
            SignedContentRequestMatchers.customerCaseEvidence.matches(
                request("POST", "/api/v1/orders/customer-cases/evidence/evidence-123/content"),
            ),
        ).isFalse()
        assertThat(
            SignedContentRequestMatchers.customerCaseEvidence.matches(
                request("GET", "/api/v1/orders/customer-cases/evidence/content"),
            ),
        ).isFalse()
        assertThat(
            SignedContentRequestMatchers.customerCaseEvidence.matches(
                request("GET", "/api/v1/orders/customer-cases/evidence/evidence-123"),
            ),
        ).isFalse()
    }

    @Test
    fun `medical document matcher permits only the signed content GET path`() {
        assertThat(
            SignedContentRequestMatchers.medicalDocument.matches(
                request("GET", "/api/v1/appointments/medical-documents/document-123/content"),
            ),
        ).isTrue()
        assertThat(
            SignedContentRequestMatchers.medicalDocument.matches(
                request("PUT", "/api/v1/appointments/medical-documents/document-123/content"),
            ),
        ).isFalse()
        assertThat(
            SignedContentRequestMatchers.medicalDocument.matches(
                request("GET", "/api/v1/appointments/medical-documents/document-123/signed-link"),
            ),
        ).isFalse()
        assertThat(
            SignedContentRequestMatchers.medicalDocument.matches(
                request("GET", "/api/v1/appointments/medical-documents/content"),
            ),
        ).isFalse()
    }

    @Test
    fun `hosted checkout matcher permits browser GETs but no mutations or session APIs`() {
        assertThat(
            SignedContentRequestMatchers.hostedCheckout.matches(
                request("GET", "/api/v1/payments/checkout/transaction-123"),
            ),
        ).isTrue()
        assertThat(
            SignedContentRequestMatchers.hostedCheckout.matches(
                request("GET", "/api/v1/payments/checkout/transaction-123/result"),
            ),
        ).isTrue()
        assertThat(
            SignedContentRequestMatchers.hostedCheckout.matches(
                request("POST", "/api/v1/payments/checkout/transaction-123"),
            ),
        ).isFalse()
        assertThat(
            SignedContentRequestMatchers.hostedCheckout.matches(
                request("GET", "/api/v1/payments/checkout-sessions"),
            ),
        ).isFalse()
        assertThat(
            SignedContentRequestMatchers.hostedCheckout.matches(
                request("GET", "/api/v1/payments/transactions/transaction-123"),
            ),
        ).isFalse()
    }

    private fun request(method: String, path: String): MockHttpServletRequest =
        MockHttpServletRequest(method, path).apply {
            contextPath = ""
            servletPath = path
        }
}
