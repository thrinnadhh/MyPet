package com.pawsnearme.providerservice.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.pawsnearme.providerservice.service.ProviderAdminApprovalService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Temporary compatibility bridge for clients and connected release tests that still
 * call the pre-hardening approval URL. The legacy controller never executes: this
 * filter enforces the same ADMIN actor contract and delegates to the locked/audited
 * ProviderAdminApprovalService used by the canonical /providers/admin endpoint.
 *
 * It intentionally runs late in the servlet chain. In the modular monolith the
 * embedded edge validates the bearer token first and propagates trusted X-User-*
 * identity headers; in distributed mode the API gateway supplies those headers
 * before the request reaches provider-service.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
class LegacyProviderApprovalCompatibilityFilter(
    private val approvalService: ProviderAdminApprovalService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    private val route = Regex("^/api/v1/providers/([0-9a-fA-F-]{36})/approve$")

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != HttpMethod.POST.name() || route.matchEntire(request.requestURI) == null

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val match = route.matchEntire(request.requestURI)
            ?: return filterChain.doFilter(request, response)
        val role = request.getHeader("X-User-Role")
        if (!role.equals("ADMIN", ignoreCase = true)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Administrator role required")
            return
        }
        val actorId = runCatching { UUID.fromString(request.getHeader("X-User-Id")) }.getOrNull()
        if (actorId == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Valid administrator identity required")
            return
        }
        val providerId = runCatching { UUID.fromString(match.groupValues[1]) }.getOrNull()
        if (providerId == null) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid provider identifier")
            return
        }

        try {
            val approved = approvalService.approve(providerId, actorId)
            response.status = HttpServletResponse.SC_OK
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            objectMapper.writeValue(
                response.writer,
                mapOf(
                    "providerId" to approved.providerId,
                    "ownerUserId" to approved.ownerUserId,
                    "providerType" to approved.providerType.name,
                    "fulfillmentType" to approved.fulfillmentType.name,
                    "name" to approved.name,
                    "status" to approved.status.name,
                    "city" to approved.city,
                    "pincode" to approved.pincode,
                    "commissionPct" to approved.commissionPct,
                ),
            )
        } catch (error: IllegalArgumentException) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND, error.message ?: "Provider not found")
        } catch (error: IllegalStateException) {
            writeError(response, HttpServletResponse.SC_CONFLICT, error.message ?: "Provider state conflict")
        }
    }

    private fun writeError(response: HttpServletResponse, status: Int, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, mapOf("error" to message))
    }
}
