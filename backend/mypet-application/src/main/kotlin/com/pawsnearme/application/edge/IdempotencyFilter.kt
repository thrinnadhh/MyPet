package com.pawsnearme.application.edge

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class IdempotencyFilter(
    private val properties: EdgeSecurityProperties,
    private val store: InMemoryIdempotencyStore
) : OncePerRequestFilter() {

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        const val REPLAYED_HEADER = "Idempotent-Replayed"
        private val unsafeMethods = setOf("POST", "PUT", "PATCH", "DELETE")
        private val validKey = Regex("[A-Za-z0-9._:-]{8,128}")
        private val replayableHeaders = setOf("Content-Type", "Location", "ETag", "Cache-Control")
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !properties.idempotency.enabled || request.method.uppercase() !in unsafeMethods

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val key = request.getHeader(IDEMPOTENCY_KEY_HEADER)?.trim()
        if (key.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }
        if (!validKey.matches(key)) {
            writeJson(response, HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key")
            return
        }

        val cachedRequest = try {
            CachedBodyRequest(request, properties.idempotency.maxBodyBytes)
        } catch (_: PayloadTooLargeException) {
            writeJson(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body exceeds idempotency limit")
            return
        }

        val principalScope = cachedRequest.getHeader("X-User-Id")
            ?.takeIf(String::isNotBlank)
            ?: "anonymous:${cachedRequest.remoteAddr ?: "unknown"}"
        val requestPath = buildString {
            append(cachedRequest.requestURI)
            cachedRequest.queryString?.let { append('?').append(it) }
        }
        val fingerprint = sha256(
            listOf(cachedRequest.method.uppercase(), requestPath, principalScope, sha256(cachedRequest.body))
                .joinToString("\n")
                .toByteArray(StandardCharsets.UTF_8)
        )
        val scopedKey = "$principalScope|${cachedRequest.method.uppercase()}|$requestPath|$key"

        when (val result = store.begin(scopedKey, fingerprint)) {
            IdempotencyBegin.Proceed -> executeAndCache(
                scopedKey,
                fingerprint,
                cachedRequest,
                response,
                filterChain
            )
            is IdempotencyBegin.Replay -> replay(response, result.response)
            IdempotencyBegin.Conflict -> writeJson(
                response,
                HttpStatus.CONFLICT,
                "Idempotency-Key was already used for a different request"
            )
            IdempotencyBegin.Pending -> {
                response.setHeader("Retry-After", "1")
                writeJson(response, HttpStatus.CONFLICT, "Idempotent request is already in progress")
            }
        }
    }

    private fun executeAndCache(
        scopedKey: String,
        fingerprint: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val responseWrapper = ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(request, responseWrapper)
            val body = responseWrapper.contentAsByteArray
            if (responseWrapper.status in 200..299 && body.size <= properties.idempotency.maxBodyBytes) {
                val headers = responseWrapper.headerNames
                    .filter { header -> replayableHeaders.any { it.equals(header, ignoreCase = true) } }
                    .associateWith { responseWrapper.getHeaders(it).toList() }
                store.complete(
                    scopedKey,
                    fingerprint,
                    CachedHttpResponse(responseWrapper.status, headers, body.copyOf())
                )
            } else {
                store.abort(scopedKey, fingerprint)
            }
        } catch (error: Throwable) {
            store.abort(scopedKey, fingerprint)
            throw error
        } finally {
            responseWrapper.copyBodyToResponse()
        }
    }

    private fun replay(response: HttpServletResponse, cached: CachedHttpResponse) {
        response.status = cached.status
        cached.headers.forEach { (name, values) ->
            values.firstOrNull()?.let { response.setHeader(name, it) }
            values.drop(1).forEach { response.addHeader(name, it) }
        }
        response.setHeader(REPLAYED_HEADER, "true")
        response.setContentLength(cached.body.size)
        response.outputStream.write(cached.body)
    }

    private fun writeJson(response: HttpServletResponse, status: HttpStatus, message: String) {
        response.status = status.value()
        response.contentType = "application/json"
        response.writer.write("""{"error":"$message"}""")
    }

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString("") { byte -> "%02x".format(byte) }
}

data class CachedHttpResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray
)

sealed interface IdempotencyBegin {
    data object Proceed : IdempotencyBegin
    data class Replay(val response: CachedHttpResponse) : IdempotencyBegin
    data object Conflict : IdempotencyBegin
    data object Pending : IdempotencyBegin
}

class InMemoryIdempotencyStore(
    private val properties: EdgeSecurityProperties.IdempotencyProperties
) {
    private sealed interface Entry {
        val fingerprint: String
        val expiresAtMillis: Long
    }

    private data class PendingEntry(
        override val fingerprint: String,
        override val expiresAtMillis: Long
    ) : Entry

    private data class CompletedEntry(
        override val fingerprint: String,
        override val expiresAtMillis: Long,
        val response: CachedHttpResponse
    ) : Entry

    private val entries = ConcurrentHashMap<String, Entry>()

    init {
        require(properties.ttlSeconds > 0) { "Idempotency TTL must be positive" }
        require(properties.maxEntries > 0) { "Idempotency max entries must be positive" }
        require(properties.maxBodyBytes > 0) { "Idempotency body limit must be positive" }
    }

    fun begin(key: String, fingerprint: String, nowMillis: Long = System.currentTimeMillis()): IdempotencyBegin {
        var result: IdempotencyBegin? = null
        val expiresAt = nowMillis + properties.ttlSeconds * 1_000

        entries.compute(key) { _, current ->
            val active = current?.takeIf { it.expiresAtMillis > nowMillis }
            when {
                active == null -> {
                    result = IdempotencyBegin.Proceed
                    PendingEntry(fingerprint, expiresAt)
                }
                active.fingerprint != fingerprint -> {
                    result = IdempotencyBegin.Conflict
                    active
                }
                active is CompletedEntry -> {
                    result = IdempotencyBegin.Replay(active.response)
                    active
                }
                else -> {
                    result = IdempotencyBegin.Pending
                    active
                }
            }
        }

        evictOverflow(nowMillis, key)
        return requireNotNull(result)
    }

    fun complete(key: String, fingerprint: String, response: CachedHttpResponse) {
        val expiresAt = System.currentTimeMillis() + properties.ttlSeconds * 1_000
        entries.computeIfPresent(key) { _, current ->
            if (current is PendingEntry && current.fingerprint == fingerprint) {
                CompletedEntry(fingerprint, expiresAt, response)
            } else {
                current
            }
        }
    }

    fun abort(key: String, fingerprint: String) {
        entries.computeIfPresent(key) { _, current ->
            if (current is PendingEntry && current.fingerprint == fingerprint) null else current
        }
    }

    fun clear() = entries.clear()

    private fun evictOverflow(nowMillis: Long, currentKey: String) {
        if (entries.size <= properties.maxEntries) return
        entries.entries.removeIf { it.value.expiresAtMillis <= nowMillis }
        if (entries.size > properties.maxEntries) {
            entries.keys.firstOrNull { it != currentKey }?.let(entries::remove)
        }
    }
}

private class CachedBodyRequest(
    request: HttpServletRequest,
    maxBodyBytes: Int
) : HttpServletRequestWrapper(request) {
    val body: ByteArray = readLimited(request.inputStream, maxBodyBytes)

    override fun getInputStream(): ServletInputStream {
        val input = ByteArrayInputStream(body)
        return object : ServletInputStream() {
            override fun read(): Int = input.read()
            override fun isFinished(): Boolean = input.available() == 0
            override fun isReady(): Boolean = true
            override fun setReadListener(readListener: ReadListener?) {
                if (readListener == null) return
                if (isFinished) readListener.onAllDataRead() else readListener.onDataAvailable()
            }
        }
    }

    override fun getReader(): BufferedReader = BufferedReader(
        InputStreamReader(inputStream, characterEncoding ?: StandardCharsets.UTF_8.name())
    )

    private fun readLimited(input: ServletInputStream, maxBodyBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBodyBytes, 8_192))
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBodyBytes) throw PayloadTooLargeException()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

private class PayloadTooLargeException : RuntimeException()
