package com.pawsnearme.application.edge

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.util.Collections
import java.util.Enumeration
import java.util.TreeMap

/**
 * Immutable request view that removes untrusted headers and adds values derived
 * by the consolidated application boundary.
 */
class MutableHeadersRequest(
    request: HttpServletRequest,
    removedHeaders: Set<String> = emptySet(),
    replacementHeaders: Map<String, String> = emptyMap()
) : HttpServletRequestWrapper(request) {

    private val headers = TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER)

    init {
        request.headerNames?.toList().orEmpty().forEach { name ->
            headers[name] = request.getHeaders(name)?.toList().orEmpty()
        }
        removedHeaders.forEach(headers::remove)
        replacementHeaders.forEach { (name, value) -> headers[name] = listOf(value) }
    }

    override fun getHeader(name: String): String? = headers[name]?.firstOrNull()

    override fun getHeaders(name: String): Enumeration<String> =
        Collections.enumeration(headers[name].orEmpty())

    override fun getHeaderNames(): Enumeration<String> =
        Collections.enumeration(headers.keys)

    private fun <T> Enumeration<T>.toList(): List<T> {
        val values = mutableListOf<T>()
        while (hasMoreElements()) values += nextElement()
        return values
    }
}
