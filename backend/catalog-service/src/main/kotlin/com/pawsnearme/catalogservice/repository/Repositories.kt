package com.pawsnearme.catalogservice.repository

import com.pawsnearme.catalogservice.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ProviderRepository : JpaRepository<Provider, UUID> {
    fun findByOwnerUserId(ownerUserId: UUID): List<Provider>
    fun existsByProviderIdAndOwnerUserId(providerId: UUID, ownerUserId: UUID): Boolean
}

@Repository
interface OfferingRepository : JpaRepository<Offering, UUID> {
    fun findByProviderId(providerId: UUID): List<Offering>
    fun findByProviderIdAndBarcode(providerId: UUID, barcode: String): Offering?
}

@Repository
interface SlotRepository : JpaRepository<Slot, UUID> {
    fun findByOfferingId(offeringId: UUID): List<Slot>
}

@Repository
interface BillRepository : JpaRepository<Bill, UUID> {
    fun findByStoreId(storeId: UUID): List<Bill>
    fun existsByIdempotencyKey(idempotencyKey: String): Boolean
    fun findByIdempotencyKey(idempotencyKey: String): Bill?
}

@Repository
interface BillItemRepository : JpaRepository<BillItem, UUID> {
    fun findByBillId(billId: UUID): List<BillItem>
}
