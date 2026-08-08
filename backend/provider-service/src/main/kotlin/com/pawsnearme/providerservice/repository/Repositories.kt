package com.pawsnearme.providerservice.repository

import com.pawsnearme.providerservice.model.*
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ProfileRepository : JpaRepository<Profile, UUID>

@Repository
interface AddressRepository : JpaRepository<Address, UUID> {
    fun findByUserId(userId: UUID): List<Address>
    fun findFirstByUserIdAndIsDefaultTrue(userId: UUID): Address?
}

@Repository
interface ProviderRepository : JpaRepository<Provider, UUID> {
    fun findByOwnerUserId(ownerUserId: UUID): List<Provider>
    fun findByStatus(status: ProviderStatus, pageable: Pageable): Page<Provider>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProviderAggregate p where p.providerId = :providerId")
    fun findByIdForUpdate(@Param("providerId") providerId: UUID): Optional<Provider>
}

@Repository
interface ProviderDocumentRepository : JpaRepository<ProviderDocument, UUID> {
    fun findByProviderId(providerId: UUID): List<ProviderDocument>
}

@Repository
interface UserRoleJoinRepository : JpaRepository<UserRoleJoin, com.pawsnearme.providerservice.model.UserRoleKey>

@Repository
interface PetRepository : JpaRepository<Pet, UUID> {
    fun findByOwnerId(ownerId: UUID): List<Pet>
}

@Repository
interface VaccinationReminderRepository : JpaRepository<VaccinationReminder, UUID> {
    fun findByOwnerId(ownerId: UUID): List<VaccinationReminder>
    fun findByEnabledTrue(): List<VaccinationReminder>
}
