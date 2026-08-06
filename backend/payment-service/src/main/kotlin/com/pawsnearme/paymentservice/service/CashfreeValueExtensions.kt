package com.pawsnearme.paymentservice.service

/** Converts optional untyped webhook values to stable idempotency-key text. */
internal fun Any?.orEmpty(): String = this?.toString().orEmpty()
