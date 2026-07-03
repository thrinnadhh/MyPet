package com.pawsnearme.reviewservice.controller

import com.pawsnearme.common.exception.BaseGlobalExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler : BaseGlobalExceptionHandler()
