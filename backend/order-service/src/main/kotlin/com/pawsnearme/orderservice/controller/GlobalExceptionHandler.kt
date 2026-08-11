package com.pawsnearme.orderservice.controller

import com.pawsnearme.common.exception.BaseGlobalExceptionHandler
import com.pawsnearme.common.exception.ErrorResponse
import com.pawsnearme.orderservice.service.OrderAccessDeniedException
import com.pawsnearme.orderservice.service.OrderTransitionConflictException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler : BaseGlobalExceptionHandler() {

    @ExceptionHandler(OrderAccessDeniedException::class)
    fun handleAccessDenied(ex: OrderAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(ex.message ?: "Access denied"))

    @ExceptionHandler(OrderTransitionConflictException::class)
    fun handleOrderTransitionConflict(ex: OrderTransitionConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(ex.message ?: "Order transition conflict"))
}
