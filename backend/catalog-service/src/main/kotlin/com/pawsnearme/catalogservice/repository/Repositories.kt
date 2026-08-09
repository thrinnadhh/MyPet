package com.pawsnearme.catalogservice.repository

import com.pawsnearme.catalogservice.model.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
    fun findFirstByProviderIdAndBarcodeIn(providerId: UUID, barcodes: Collection<String>): Offering?

    @Query(
        """
        SELECT o
          FROM Offering o
         WHERE o.providerId = :providerId
           AND (
                :query = ''
                OR LOWER(o.name) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(o.category, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(o.sku, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(COALESCE(o.barcode, '')) LIKE LOWER(CONCAT('%', :query, '%'))
           )
        """
    )
    fun searchMerchantOfferings(
        @Param("providerId") providerId: UUID,
        @Param("query") query: String,
        pageable: Pageable,
    ): Page<Offering>

    @Modifying
    @Query(
        """
        UPDATE Offering o
           SET o.stockQuantity = o.stockQuantity - :quantity
         WHERE o.offeringId = :offeringId
           AND o.providerId = :providerId
           AND o.stockQuantity >= :quantity
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
