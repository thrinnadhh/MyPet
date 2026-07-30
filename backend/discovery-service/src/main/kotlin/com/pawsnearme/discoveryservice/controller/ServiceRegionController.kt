package com.pawsnearme.discoveryservice.controller

import com.pawsnearme.discoveryservice.model.*
import com.pawsnearme.discoveryservice.service.ServiceRegionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class ServiceRegionController(
    private val serviceRegionService: ServiceRegionService
) {

    @GetMapping("/api/v1/service-regions/active")
    fun getActiveRegions(): ResponseEntity<List<ServiceRegionDto>> {
        return ResponseEntity.ok(serviceRegionService.getActiveRegions())
    }

    @GetMapping("/api/v1/service-regions/check")
    fun checkServiceability(
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) pincode: String?
    ): ResponseEntity<ServiceabilityCheckResult> {
        val result = serviceRegionService.checkServiceability(latitude, longitude, city, pincode)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/api/v1/admin/service-regions")
    fun getAdminRegions(): ResponseEntity<List<ServiceRegionDto>> {
        return ResponseEntity.ok(serviceRegionService.getAllAdminRegions())
    }

    @PostMapping("/api/v1/admin/service-regions")
    fun createRegion(@RequestBody request: CreateServiceRegionRequest): ResponseEntity<ServiceRegionDto> {
        val created = serviceRegionService.createRegion(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @PutMapping("/api/v1/admin/service-regions/{id}")
    fun updateRegion(
        @PathVariable id: UUID,
        @RequestBody request: UpdateServiceRegionRequest
    ): ResponseEntity<ServiceRegionDto> {
        val updated = serviceRegionService.updateRegion(id, request)
        return ResponseEntity.ok(updated)
    }
}
