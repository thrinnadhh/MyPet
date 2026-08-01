package com.pawsnearme.application.edge

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter

class IdentityHeaderFilter : OncePerRequestFilter() {

    companion object {
        const val IDENTITY_ATTRIBUTE = "com.pawsnearme.edge.identity"

        val SPOOFABLE_HEADERS = setOf(
            "X-User-Id",
            "X-User-Role",
            "X-User-Email",
            "X-User-Full-Name",
            "X-User-Phone",
            "X-Admin-Api-Key",
            "X-Internal-Gateway-Secret",
            "X-Internal-Secret",
            "X-Service-Name"
        )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        val identity = (authentication as? JwtAuthenticationToken)
            ?.takeIf { it.isAuthenticated }
            ?.let { JwtIdentityExtractor.extract(it.token) }

        val replacements = buildMap {
            identity?.let {
                put("X-User-Id", it.userId)
                put("X-User-Role", it.role)
                it.email?.let { value -> put("X-User-Email", value) }
                it.fullName?.let { value -> put("X-User-Full-Name", value) }
                it.phone?.let { value -> put("X-User-Phone", value) }
            }
        }

        val wrappedRequest = MutableHeadersRequest(
            request,
            removedHeaders = SPOOFABLE_HEADERS,
            replacementHeaders = replacements
        )
        identity?.let { wrappedRequest.setAttribute(IDENTITY_ATTRIBUTE, it) }

        filterChain.doFilter(wrappedRequest, response)
    }
}
