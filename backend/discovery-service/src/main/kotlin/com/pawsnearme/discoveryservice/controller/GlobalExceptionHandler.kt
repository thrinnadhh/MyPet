package com.pawsnearme.discoveryservice.controller

import com.pawsnearme.common.exception.BaseGlobalExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler : BaseGlobalExceptionHandler()
