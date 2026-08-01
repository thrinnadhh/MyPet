package com.pawsnearme.common.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

/**
 * Shared exception boundary for Spring WebMVC modules.
 *
 * The response keeps the legacy `error` alias while exposing the typed client
 * contract used by every MyPet application: code, message, traceId and fieldErrors.
 */
open class BaseGlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request: {}", ex.message, ex)
        return response(
            HttpStatus.BAD_REQUEST,
            "BAD_REQUEST",
            ex.message ?: "Bad request",
            request
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val value = ex.value?.toString() ?: "null"
        val message = "Invalid value '$value' for parameter '${ex.name}'."
        logger.warn("Bad request parameter: {}", message)
        return response(
            HttpStatus.BAD_REQUEST,
            "INVALID_PARAMETER",
            message,
            request,
            mapOf(ex.name to listOf(message))
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors
            .groupBy({ it.field }, { it.defaultMessage ?: "Invalid value" })
        logger.warn("Request validation failed for fields: {}", fieldErrors.keys)
        return response(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "VALIDATION_FAILED",
            "One or more fields are invalid.",
            request,
            fieldErrors
        )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(
        ex: NoSuchElementException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Resource not found: {}", ex.message, ex)
        return response(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            ex.message ?: "Resource not found",
            request
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(
        ex: IllegalStateException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Conflict/invalid state: {}", ex.message, ex)
        return response(
            HttpStatus.CONFLICT,
            "STATE_CONFLICT",
            ex.message ?: "Conflict / invalid state",
            request
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Internal server error: {}", ex.message, ex)
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            request
        )
    }

    private fun response(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
        fieldErrors: Map<String, List<String>> = emptyMap()
    ): ResponseEntity<ErrorResponse> = ResponseEntity
        .status(status)
        .body(
            ErrorResponse(
                code = code,
                message = message,
                traceId = traceId(request),
                fieldErrors = fieldErrors,
                path = request.requestURI
            )
        )

    private fun traceId(request: HttpServletRequest): String? =
        (request.getHeader("X-Request-Id") ?: request.getHeader("X-Trace-Id"))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(128)
}

/**
 * Standard error response payload.
 *
 * `error` remains for backward compatibility with older clients and should be
 * removed only after every external consumer has migrated to `message`.
 */
data class ErrorResponse(
    val code: String,
    val message: String,
    val traceId: String? = null,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val timestamp: Instant = Instant.now(),
    val path: String? = null,
    val error: String = message
) {
    /** Compatibility constructor for service-specific handlers not yet migrated. */
    constructor(error: String) : this(
        code = "ERROR",
        message = error,
        error = error
    )
}
