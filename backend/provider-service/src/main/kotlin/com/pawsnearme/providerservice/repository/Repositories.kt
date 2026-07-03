package com.pawsnearme.providerservice.repository

import com.pawsnearme.providerservice.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
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
}
