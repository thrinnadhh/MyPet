package com.pawsnearme.discoveryservice.service

import com.pawsnearme.discoveryservice.model.*
import com.pawsnearme.discoveryservice.repository.ServiceRegionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ServiceRegionService(
    private val serviceRegionRepository: ServiceRegionRepository
) {

    @Transactional(readOnly = true)
    fun getActiveRegions(): List<ServiceRegionDto> {
        return serviceRegionRepository.findAllByStatusOrderBySortOrderAsc(RegionStatus.ENABLED)
            .map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getAllAdminRegions(): List<ServiceRegionDto> {
        return serviceRegionRepository.findAllByOrderBySortOrderAsc()
            .map { it.toDto() }
    }

    @Transactional
    fun createRegion(request: CreateServiceRegionRequest): ServiceRegionDto {
        val region = ServiceRegion(
            cityIdentity = request.cityIdentity.lowercase().trim(),
            displayName = request.displayName.trim(),
            state = request.state.trim(),
            country = request.country.trim(),
            centerLatitude = request.centerLatitude,
            centerLongitude = request.centerLongitude,
            radiusKm = request.radiusKm,
            pincodes = request.pincodes,
            status = request.status,
            sortOrder = request.sortOrder,
            allowProducts = request.allowProducts,
            allowGrooming = request.allowGrooming,
            allowVet = request.allowVet,
            allowOwnDelivery = request.allowOwnDelivery,
            allow3pDelivery = request.allow3pDelivery,
            allowCod = request.allowCod,
            allowOnlinePayment = request.allowOnlinePayment,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        return serviceRegionRepository.save(region).toDto()
    }

    @Transactional
    fun updateRegion(id: UUID, request: UpdateServiceRegionRequest): ServiceRegionDto {
        val region = serviceRegionRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Service region with id $id not found") }

        request.displayName?.let { region.displayName = it.trim() }
        request.state?.let { region.state = it.trim() }
        request.country?.let { region.country = it.trim() }
        request.centerLatitude?.let { region.centerLatitude = it }
        request.centerLongitude?.let { region.centerLongitude = it }
        request.radiusKm?.let { region.radiusKm = it }
        request.pincodes?.let { region.pincodes = it }
        request.status?.let { region.status = it }
        request.sortOrder?.let { region.sortOrder = it }
        request.allowProducts?.let { region.allowProducts = it }
        request.allowGrooming?.let { region.allowGrooming = it }
        request.allowVet?.let { region.allowVet = it }
        request.allowOwnDelivery?.let { region.allowOwnDelivery = it }
        request.allow3pDelivery?.let { region.allow3pDelivery = it }
        request.allowCod?.let { region.allowCod = it }
        request.allowOnlinePayment?.let { region.allowOnlinePayment = it }
        region.updatedAt = Instant.now()

        return serviceRegionRepository.save(region).toDto()
    }

    @Transactional(readOnly = true)
    fun checkServiceability(
        latitude: Double?,
        longitude: Double?,
        cityIdentity: String?,
        pincode: String?
    ): ServiceabilityCheckResult {
        val activeRegions = serviceRegionRepository.findAllByStatusOrderBySortOrderAsc(RegionStatus.ENABLED)

        if (activeRegions.isEmpty()) {
            return ServiceabilityCheckResult(false, null, "No active service regions currently available")
        }

        // 1. Match by cityIdentity
        if (!cityIdentity.isNullOrBlank()) {
            val matched = activeRegions.firstOrNull { it.cityIdentity.equals(cityIdentity.trim(), ignoreCase = true) }
            if (matched != null) {
                return ServiceabilityCheckResult(true, matched.toDto(), null)
            }
        }

        // 2. Match by pincode
        if (!pincode.isNullOrBlank()) {
            val cleanPincode = pincode.trim()
            val matched = activeRegions.firstOrNull { region ->
                region.pincodes?.split(",")?.map { it.trim() }?.contains(cleanPincode) == true
            }
            if (matched != null) {
                return ServiceabilityCheckResult(true, matched.toDto(), null)
            }
        }


        // 3. Match by coordinates distance
        if (latitude != null && longitude != null) {
            for (region in activeRegions) {
                val distance = haversineKm(latitude, longitude, region.centerLatitude, region.centerLongitude)
                if (distance <= region.radiusKm) {
                    return ServiceabilityCheckResult(true, region.toDto(), null)
                }
            }
        }

        return ServiceabilityCheckResult(false, null, "Location is outside current active service regions")
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}

