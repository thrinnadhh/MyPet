package com.pawsnearme.application.runtime

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

class ProductionRuntimeGuardTest {
    private val guard = ProductionRuntimeGuard()

    @Test
    fun `local profile permits deterministic development adapters`() {
        val environment = MockEnvironment().apply {
            setActiveProfiles("local")
        }

        guard.validate(environment)
    }

    @Test
    fun `deployment profile rejects sandbox and development configuration`() {
        val environment = productionEnvironment().withProperty("CASHFREE_SANDBOX_MODE", "true")

        assertThrows<IllegalArgumentException> {
            guard.validate(environment)
        }
    }

    @Test
    fun `deployment profile accepts explicit production-safe configuration`() {
        guard.validate(productionEnvironment())
    }

    private fun productionEnvironment(): MockEnvironment = MockEnvironment().apply {
        setActiveProfiles("production")
        setProperty("mypet.edge.jwt.allow-unsigned", "false")
        setProperty("SUPABASE_JWT_JWK_SET_URI", "https://example.supabase.co/auth/v1/.well-known/jwks.json")
        setProperty("NOTIFICATION_DELIVERY_MODE", "EXPO_FCM")
        setProperty("CASHFREE_SANDBOX_MODE", "false")
        setProperty("CASHFREE_CLIENT_ID", "cashfree-client")
        setProperty("CASHFREE_CLIENT_SECRET", "cashfree-client-secret")
        setProperty("CASHFREE_WEBHOOK_SECRET", "cashfree-webhook-secret")
        setProperty("PAYMENT_CHECKOUT_TOKEN_SECRET", "0123456789abcdef0123456789abcdef")
        setProperty("MEDICAL_DOCUMENT_SIGNING_KEY", "abcdef0123456789abcdef0123456789")
        setProperty("CASE_EVIDENCE_SIGNING_KEY", "fedcba9876543210fedcba9876543210")
        setProperty("MYPET_DB_PASSWORD", "production-db-password")
        setProperty("APPOINTMENT_MEDICAL_DOCUMENTS_PUBLIC_BASE_URL", "https://api.mypet.example")
        setProperty("ORDER_CASE_EVIDENCE_PUBLIC_BASE_URL", "https://api.mypet.example")
        setProperty("PROVIDER_PUBLIC_BASE_URL", "https://api.mypet.example")
        setProperty("CHAT_PUBLIC_BASE_URL", "https://api.mypet.example")
        setProperty("GATEWAY_CORS_ALLOWED_ORIGINS", "https://admin.mypet.example")
    }
}
