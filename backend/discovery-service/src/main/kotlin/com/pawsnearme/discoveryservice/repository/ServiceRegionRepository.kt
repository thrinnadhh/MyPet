package com.pawsnearme.discoveryservice.repository

import com.pawsnearme.discoveryservice.model.RegionStatus
import com.pawsnearme.discoveryservice.model.ServiceRegion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ServiceRegionRepository : JpaRepository<ServiceRegion, UUID> {
    fun findByCityIdentityIgnoreCase(cityIdentity: String): Optional<ServiceRegion>
    fun findAllByStatusOrderBySortOrderAsc(status: RegionStatus): List<ServiceRegion>
    fun findAllByOrderBySortOrderAsc(): List<ServiceRegion>
}
