package com.pawsnearme.application.runtime

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Local/dev/test profiles intentionally use deterministic adapters and loopback
 * URLs. Any other full modular-monolith runtime is treated as deployment-like
 * and must fail closed instead of silently booting with sandbox payments,
 * logged-only notifications, weak credentials, or localhost URLs.
 */
@Component
@ConditionalOnProperty(prefix = "mypet.runtime", name = ["modules-enabled"], havingValue = "true")
class ProductionRuntimeGuard : EnvironmentAware {
    private lateinit var environment: Environment

    override fun setEnvironment(environment: Environment) {
        this.environment = environment
        validate(environment)
    }

    internal fun validate(env: Environment) {
        val profiles = env.activeProfiles.map(String::lowercase).toSet()
        if (profiles.any { it in SAFE_PROFILES }) return

        require(profiles.isNotEmpty()) {
            "SPRING_PROFILES_ACTIVE must explicitly identify local/dev/test or a deployment profile."
        }
        require(!bool(env, "mypet.edge.jwt.allow-unsigned", "ALLOW_UNSIGNED_JWT")) {
            "Unsigned JWT validation is forbidden outside local/dev/test profiles."
        }

        val jwkSetUri = value(env, "mypet.edge.jwt.jwk-set-uri", "SUPABASE_JWT_JWK_SET_URI")
        val jwtSecret = value(env, "mypet.edge.jwt.secret-key", "SUPABASE_JWT_SECRET")
        require(
            (jwkSetUri.isNotBlank() && !jwkSetUri.contains("your-project.supabase.co")) || jwtSecret.isNotBlank()
        ) { "Production JWT verification is not configured." }

        val notificationMode = value(env, "notification.delivery.mode", "NOTIFICATION_DELIVERY_MODE").uppercase()
        require(notificationMode == "EXPO_FCM") {
            "NOTIFICATION_DELIVERY_MODE must be EXPO_FCM outside local/dev/test profiles."
        }

        require(!bool(env, "CASHFREE_SANDBOX_MODE")) {
            "CASHFREE_SANDBOX_MODE must be false outside local/dev/test profiles."
        }
        require(value(env, "CASHFREE_CLIENT_ID").isNotBlank()) { "CASHFREE_CLIENT_ID must be configured." }
        require(value(env, "CASHFREE_CLIENT_SECRET").isNotBlank()) { "CASHFREE_CLIENT_SECRET must be configured." }
        require(value(env, "CASHFREE_WEBHOOK_SECRET").isNotBlank()) { "CASHFREE_WEBHOOK_SECRET must be configured." }
        requireStrongSecret(value(env, "PAYMENT_CHECKOUT_TOKEN_SECRET"), "PAYMENT_CHECKOUT_TOKEN_SECRET")
        requireStrongSecret(value(env, "MEDICAL_DOCUMENT_SIGNING_KEY"), "MEDICAL_DOCUMENT_SIGNING_KEY")
        requireStrongSecret(value(env, "CASE_EVIDENCE_SIGNING_KEY"), "CASE_EVIDENCE_SIGNING_KEY")

        val dbPassword = value(env, "mypet.database.password", "MYPET_DB_PASSWORD", "DB_PASSWORD")
        require(dbPassword.isNotBlank() && dbPassword != "postgres") {
            "Production database password must be explicitly configured and must not use the postgres development password."
        }

        listOf(
            "appointment.medical-documents.public-base-url" to "APPOINTMENT_MEDICAL_DOCUMENTS_PUBLIC_BASE_URL",
            "order.case-evidence.public-base-url" to "ORDER_CASE_EVIDENCE_PUBLIC_BASE_URL",
            "provider.public-base-url" to "PROVIDER_PUBLIC_BASE_URL",
            "chat.public-base-url" to "CHAT_PUBLIC_BASE_URL",
        ).forEach { (property, environmentName) ->
            val url = value(env, property, environmentName)
            require(url.startsWith("https://")) {
                "$environmentName must be an HTTPS public URL outside local/dev/test profiles."
            }
        }

        val origins = value(env, "mypet.edge.cors.allowed-origins", "GATEWAY_CORS_ALLOWED_ORIGINS")
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
        require(origins.isNotEmpty() && origins.all { it.startsWith("https://") && !it.contains("localhost") && it != "*" }) {
            "GATEWAY_CORS_ALLOWED_ORIGINS must contain only explicit HTTPS production origins."
        }
    }

    private fun requireStrongSecret(secret: String, name: String) {
        require(secret.length >= 32 && secret != "local-development-key") {
            "$name must be explicitly configured with at least 32 characters."
        }
    }

    private fun bool(env: Environment, vararg names: String): Boolean =
        value(env, *names).equals("true", ignoreCase = true) || value(env, *names) == "1"

    private fun value(env: Environment, vararg names: String): String =
        names.asSequence()
            .mapNotNull { env.getProperty(it)?.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()

    companion object {
        private val SAFE_PROFILES = setOf("local", "dev", "test")
    }
}
