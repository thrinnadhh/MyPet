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

    @GetMapping("/search")
    fun universalSearch(
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) type: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<UniversalSearchResponse> {
        val result = discoveryService.universalSearch(
            query = q,
            city = city,
            latitude = latitude,
            longitude = longitude,
            typeFilter = type,
            page = page,
            size = size
        )
        return ResponseEntity.ok(result)
    }
}

