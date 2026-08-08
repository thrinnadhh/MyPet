package com.pawsnearme.catalogservice.repository

import com.pawsnearme.catalogservice.model.*
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ProviderRepository : JpaRepository<Provider, UUID> {
    fun findByOwnerUserId(ownerUserId: UUID): List<Provider>
    fun existsByProviderIdAndOwnerUserId(providerId: UUID, ownerUserId: UUID): Boolean
}

@Repository
interface OfferingRepository : JpaRepository<Offering, UUID> {
    fun findByProviderId(providerId: UUID): List<Offering>
    fun findByProviderIdAndStatusAndAdminDisabledFalse(providerId: UUID, status: OfferingStatus): List<Offering>
    fun findFirstByProviderIdAndBarcodeIn(providerId: UUID, barcodes: Collection<String>): Offering?
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Offering>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offering o where o.offeringId = :offeringId")
    fun findByIdForUpdate(@Param("offeringId") offeringId: UUID): Optional<Offering>

    @Modifying
    @Query(
        """
        UPDATE Offering o
           SET o.stockQuantity = o.stockQuantity - :quantity
         WHERE o.offeringId = :offeringId
           AND o.providerId = :providerId
           AND o.stockQuantity >= :quantity
           AND o.status = com.pawsnearme.catalogservice.model.OfferingStatus.ACTIVE
           AND o.adminDisabled = false
        """
    )
    fun decrementStockIfAvailable(offeringId: UUID, providerId: UUID, quantity: Int): Int

    @Modifying
    @Query(
        """
        UPDATE Offering o
           SET o.stockQuantity = o.stockQuantity + :quantity
         WHERE o.offeringId = :offeringId
           AND o.stockQuantity IS NOT NULL
        """
    )
    fun incrementStockIfTracked(offeringId: UUID, quantity: Int): Int
}

@Repository
interface CatalogModerationAuditLogRepository : JpaRepository<CatalogModerationAuditLog, UUID> {
    fun findByOfferingIdOrderByCreatedAtDesc(offeringId: UUID): List<CatalogModerationAuditLog>
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