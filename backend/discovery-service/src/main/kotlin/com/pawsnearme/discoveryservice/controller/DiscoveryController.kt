package com.pawsnearme.discoveryservice.controller

import com.pawsnearme.discoveryservice.model.*
import com.pawsnearme.discoveryservice.service.DiscoveryService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/discovery")
class DiscoveryController(private val discoveryService: DiscoveryService) {

    @GetMapping("/providers")
    fun searchProviders(
        @RequestParam longitude: Double,
        @RequestParam latitude: Double,
        @RequestParam(defaultValue = "5.0") radius: Double,
        @RequestParam(required = false) type: ProviderType?
    ): ResponseEntity<List<ProviderSearchResult>> {
        val providers = discoveryService.searchNearbyProviders(
            longitude = longitude,
            latitude = latitude,
            radiusKm = radius,
            providerType = type
        )
        return ResponseEntity.ok(providers)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (ex.message ?: "Bad Request")))
    }
}
