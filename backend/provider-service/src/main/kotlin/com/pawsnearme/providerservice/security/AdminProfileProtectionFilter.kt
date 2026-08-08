package com.pawsnearme.providerservice.security

import com.pawsnearme.providerservice.model.UserRole
import com.pawsnearme.providerservice.repository.ProfileRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Generic user suspension is a customer/merchant/captain support capability.
 * ADMIN identity lifecycle must not be controlled through that endpoint because
 * the application currently has no higher-privilege SUPER_ADMIN role.
 */
@Component
class AdminProfileProtectionFilter(
    private val profileRepository: ProfileRepository
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!request.method.equals("POST", ignoreCase = true)) return true
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        return REVOKE_PATH.matchEntire(path) == null
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        val match = REVOKE_PATH.matchEntire(path)
        if (match == null) {
            filterChain.doFilter(request, response)
            return
        }

        val targetId = runCatching { UUID.fromString(match.groupValues[1]) }.getOrNull()
        val target = targetId?.let { profileRepository.findById(it).orElse(null) }
        if (target?.role == UserRole.ADMIN) {
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = "application/json"
            response.writer.write(
                """{"error":"ADMIN identities cannot be suspended through generic user management"}"""
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        private val REVOKE_PATH = Regex(
            "^/api/v1/profiles/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/revoke/?$"
        )
    }
}
