package com.pawsnearme.catalogservice.service

import com.pawsnearme.catalogservice.model.InternalStockMutation
import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.repository.InternalStockMutationRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
            // will roll back this attempt, preventing a double mutation.
            throw duplicate
        }
        return updated
    }
}
