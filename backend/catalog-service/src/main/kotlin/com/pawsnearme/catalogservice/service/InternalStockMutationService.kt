package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.InternalStockMutation
import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.repository.InternalStockMutationRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class InternalStockMutationService(
    private val catalogService: CatalogService,
    private val mutationRepository: InternalStockMutationRepository
) {
    @Transactional
    fun mutate(idempotencyKey: UUID, offeringId: UUID, quantity: Int, operation: String): Offering {
        require(quantity in 1..999) { "Quantity must be between 1 and 999" }
        require(operation == "DECREMENT" || operation == "RESTORE") { "Unsupported stock operation" }

        // Sprint 1 used RESTORE:{offeringId}:{quantity}. That identity collapses
        // independent orders for the same SKU. The order lifecycle now restores via
        // RESTORE:{orderId}:{orderItemId}; ignore the unsafe legacy restoration so it
        // cannot double-credit stock while the canonical lifecycle reconciler runs.
        if (operation == "RESTORE" && idempotencyKey == legacyRestoreKey(offeringId, quantity)) {
            return catalogService.getOfferingById(offeringId)
        }

        val existing = mutationRepository.findById(idempotencyKey).orElse(null)
        if (existing != null) {
            require(existing.offeringId == offeringId && existing.quantity == quantity && existing.operation == operation) {
                "Idempotency key was already used with different stock mutation parameters"
            }
            return catalogService.getOfferingById(offeringId)
        }

        val updated = if (operation == "DECREMENT") {
            catalogService.decrementStock(offeringId, quantity)
        } else {
            catalogService.restoreStock(offeringId, quantity)
        }

        try {
            mutationRepository.saveAndFlush(
                InternalStockMutation(idempotencyKey, offeringId, operation, quantity)
            )
        } catch (duplicate: DataIntegrityViolationException) {
            // A concurrent duplicate completed the same mutation. The transaction
            // rolls back this attempt, preventing a double mutation.
            throw duplicate
        }
        return updated
    }

    private fun legacyRestoreKey(offeringId: UUID, quantity: Int): UUID = UUID.nameUUIDFromBytes(
        "restore:$offeringId:$quantity".toByteArray(StandardCharsets.UTF_8)
    )
}