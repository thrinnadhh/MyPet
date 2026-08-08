package com.pawsnearme.application.edge

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Revokes already-issued customer/merchant/captain JWTs in the primary
 * modular-monolith topology. Profile administration writes the same
 * `suspended_user:<subject>` marker used by the legacy API gateway, so both
 * deployment modes enforce one revocation contract.
 */
class SuspendedUserFilter(
    private val redisTemplate: StringRedisTemplate,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        val jwt = authentication?.principal as? Jwt
        val subject = jwt?.subject

        if (!subject.isNullOrBlank() && redisTemplate.hasKey("suspended_user:$subject")) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write("""{"error":"User access has been revoked"}""")
            return
        }

        filterChain.doFilter(request, response)
    }
}
