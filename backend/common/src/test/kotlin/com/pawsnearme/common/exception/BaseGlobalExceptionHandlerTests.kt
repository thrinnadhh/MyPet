package com.pawsnearme.common.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class BaseGlobalExceptionHandlerTests {
    @Test
    fun `invalid request parameter type returns bad request`() {
        val exception = mock<MethodArgumentTypeMismatchException>()
        whenever(exception.value).thenReturn("IN_PROGRESS")
        whenever(exception.name).thenReturn("status")

        val response = BaseGlobalExceptionHandler().handleTypeMismatch(exception)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(
            "Invalid value 'IN_PROGRESS' for parameter 'status'.",
            response.body?.error
        )
    }
}
