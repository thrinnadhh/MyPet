package com.pawsnearme.catalogservice.security

import com.pawsnearme.catalogservice.repository.OfferingRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * A merchant must not be able to undo an Admin moderation decision by replaying
 * the normal offering update/delete endpoints. Restoration has a dedicated
 * ADMIN-only domain action with reason + audit evidence.
 */
@Component
class AdminModeratedOfferingMutationFilter(
    private val offeringRepository: OfferingRepository
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.method !in setOf("PUT", "DELETE")) return true
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        return OFFERING_PATH.matchEntire(path) == null
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        val match = OFFERING_PATH.matchEntire(path)
        if (match == null) {
            filterChain.doFilter(request, response)
            return
        }
        val offeringId = runCatching { UUID.fromString(match.groupValues[1]) }.getOrNull()
        val offering = offeringId?.let { offeringRepository.findById(it).orElse(null) }
        val isAdmin = request.getHeader("X-User-Role").equals("ADMIN", ignoreCase = true)
        if (offering?.adminDisabled == true && !isAdmin) {
            response.status = HttpStatus.CONFLICT.value()
            response.contentType = "application/json"
            response.writer.write(
                """{"code":"ADMIN_MODERATED","error":"This listing is locked by Admin moderation."}"""
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private val OFFERING_PATH = Regex(
            "^/api/v1/catalog/offerings/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/?$"
        )
    }
}
