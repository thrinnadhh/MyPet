package com.pawsnearme.providerservice.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class LegacyAdminRouteBlockFilter : OncePerRequestFilter() {
    private val legacyProfileAccess = Regex("^/api/v1/profiles/[0-9a-fA-F-]{36}/(revoke|restore)$")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !isBlocked(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        response.status = HttpServletResponse.SC_GONE
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            """{"error":"Legacy administrative route is disabled; use the actor-aware /admin endpoint","code":"LEGACY_ADMIN_ROUTE_DISABLED"}""",
        )
    }

    internal fun isBlocked(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        val method = request.method
        return (method == HttpMethod.GET.name() && path == "/api/v1/providers/pending") ||
            (method == HttpMethod.GET.name() && path == "/api/v1/profiles") ||
            (method == HttpMethod.POST.name() && legacyProfileAccess.matches(path))
    }
}
