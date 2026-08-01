package com.pawsnearme.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class BaseGlobalExceptionHandlerTests {
    private fun request(traceId: String? = "trace-123"): HttpServletRequest {
        val request = mock<HttpServletRequest>()
        whenever(request.requestURI).thenReturn("/api/v1/orders")
        whenever(request.getHeader("X-Request-Id")).thenReturn(traceId)
        whenever(request.getHeader("X-Trace-Id")).thenReturn(null)
        return request
    }

    @Test
    fun `invalid request parameter returns typed bad-request envelope`() {
        val exception = mock<MethodArgumentTypeMismatchException>()
        whenever(exception.value).thenReturn("IN_PROGRESS")
        whenever(exception.name).thenReturn("status")

        val response = BaseGlobalExceptionHandler().handleTypeMismatch(exception, request())

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_PARAMETER", response.body?.code)
        assertEquals(
            "Invalid value 'IN_PROGRESS' for parameter 'status'.",
            response.body?.message
        )
        assertEquals(response.body?.message, response.body?.error)
        assertEquals("trace-123", response.body?.traceId)
        assertEquals("/api/v1/orders", response.body?.path)
        assertEquals(
            listOf("Invalid value 'IN_PROGRESS' for parameter 'status'."),
            response.body?.fieldErrors?.get("status")
        )
    }

    @Test
    fun `conflict response has stable code and optional trace`() {
        val response = BaseGlobalExceptionHandler().handleConflict(
            IllegalStateException("Order already completed"),
            request(null)
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("STATE_CONFLICT", response.body?.code)
        assertEquals("Order already completed", response.body?.message)
        assertEquals(null, response.body?.traceId)
    }

    @Test
    fun `generic response does not expose internal exception details`() {
        val response = BaseGlobalExceptionHandler().handleGeneric(
            IllegalStateException("database-password-secret"),
            request()
        )

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.code)
        assertEquals("An unexpected error occurred", response.body?.message)
        assertTrue(response.body?.message?.contains("password") == false)
    }
}
