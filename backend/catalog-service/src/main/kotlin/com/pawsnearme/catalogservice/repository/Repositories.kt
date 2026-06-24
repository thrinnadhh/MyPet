package com.pawsnearme.catalogservice.repository

import com.pawsnearme.catalogservice.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProviderRepository : JpaRepository<Provider, UUID>

@Repository
interface OfferingRepository : JpaRepository<Offering, UUID> {
    fun findByProviderId(providerId: UUID): List<Offering>
}

@Repository
interface SlotRepository : JpaRepository<Slot, UUID> {
    fun findByOfferingId(offeringId: UUID): List<Slot>
}
