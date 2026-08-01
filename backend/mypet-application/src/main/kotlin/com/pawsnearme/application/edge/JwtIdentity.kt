package com.pawsnearme.application.edge

import org.springframework.security.oauth2.jwt.Jwt

data class EdgeIdentity(
    val userId: String,
    val role: String,
    val email: String?,
    val fullName: String?,
    val phone: String?
)

object JwtIdentityExtractor {
    private val platformRoles = setOf("AUTHENTICATED", "ANON", "SERVICE_ROLE")

    fun extract(jwt: Jwt): EdgeIdentity = EdgeIdentity(
        userId = jwt.subject,
        role = extractRole(jwt),
        email = stringClaim(jwt, "email"),
        fullName = extractFullName(jwt),
        phone = extractPhone(jwt)
    )

    fun extractRole(jwt: Jwt): String {
        val appMetadata = jwt.claims["app_metadata"] as? Map<*, *>
        val nestedRole = appMetadata?.get("role") as? String
        if (!nestedRole.isNullOrBlank()) return normalizeRole(nestedRole)

        val directRole = jwt.claims["role"] as? String
        if (!directRole.isNullOrBlank()) {
            val normalized = normalizeRole(directRole)
            if (normalized !in platformRoles) return normalized
        }

        return "CUSTOMER"
    }

    fun normalizeRole(role: String): String {
        val normalized = role.trim().uppercase()
        return if (normalized == "PROVIDER") "MERCHANT" else normalized
    }

    private fun stringClaim(jwt: Jwt, claim: String): String? =
        (jwt.claims[claim] as? String)?.takeIf(String::isNotBlank)

    private fun extractFullName(jwt: Jwt): String? {
        val metadata = jwt.claims["user_metadata"] as? Map<*, *>
        return sequenceOf(
            metadata?.get("full_name") as? String,
            metadata?.get("name") as? String,
            stringClaim(jwt, "name")
        ).filterNotNull().firstOrNull(String::isNotBlank)
    }

    private fun extractPhone(jwt: Jwt): String? {
        val metadata = jwt.claims["user_metadata"] as? Map<*, *>
        return sequenceOf(
            metadata?.get("phone") as? String,
            metadata?.get("phone_number") as? String,
            stringClaim(jwt, "phone"),
            stringClaim(jwt, "phone_number")
        ).filterNotNull().firstOrNull(String::isNotBlank)
    }
}
