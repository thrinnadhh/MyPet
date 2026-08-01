package com.pawsnearme.discoveryservice.module

import com.pawsnearme.common.module.DiscoveryModuleApi
import com.pawsnearme.common.module.ServiceabilityDecision
import com.pawsnearme.discoveryservice.service.ServiceRegionService
import org.springframework.stereotype.Service

@Service
class DiscoveryModuleFacade(
    private val serviceRegionService: ServiceRegionService
) : DiscoveryModuleApi {
    override fun checkServiceability(
        city: String?,
        latitude: Double?,
        longitude: Double?,
        pincode: String?
    ): ServiceabilityDecision = serviceRegionService.checkServiceability(
        latitude = latitude,
        longitude = longitude,
        cityIdentity = city,
        pincode = pincode
    ).let { result ->
        ServiceabilityDecision(
            serviceable = result.serviceable,
            reason = result.reason
        )
    }
}
