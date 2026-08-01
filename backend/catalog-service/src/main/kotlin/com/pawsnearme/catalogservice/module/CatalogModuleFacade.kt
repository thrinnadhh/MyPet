package com.pawsnearme.catalogservice.module

import com.pawsnearme.catalogservice.model.Offering
import com.pawsnearme.catalogservice.model.Slot
import com.pawsnearme.catalogservice.model.SlotStatus
import com.pawsnearme.catalogservice.service.CatalogService
import com.pawsnearme.catalogservice.service.InternalStockMutationService
import com.pawsnearme.common.module.CatalogModuleApi
import com.pawsnearme.common.module.CatalogOfferingSnapshot
import com.pawsnearme.common.module.CatalogSlotSnapshot
import com.pawsnearme.common.module.StockMutationCommand
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CatalogModuleFacade(
    private val catalogService: CatalogService,
    private val stockMutationService: InternalStockMutationService
) : CatalogModuleApi {

    override fun offering(offeringId: UUID): CatalogOfferingSnapshot =
        catalogService.getOfferingById(offeringId).toSnapshot()

    override fun reserveStock(command: StockMutationCommand): CatalogOfferingSnapshot =
        stockMutationService.mutate(
            command.idempotencyKey,
            command.offeringId,
            command.quantity,
            "DECREMENT"
        ).toSnapshot()

    override fun restoreStock(command: StockMutationCommand): CatalogOfferingSnapshot =
        stockMutationService.mutate(
            command.idempotencyKey,
            command.offeringId,
            command.quantity,
            "RESTORE"
        ).toSnapshot()

    override fun slot(slotId: UUID): CatalogSlotSnapshot? =
        runCatching { catalogService.getSlotById(slotId).toSnapshot() }.getOrNull()

    override fun updateSlotStatus(slotId: UUID, status: String): CatalogSlotSnapshot =
        catalogService.updateSlotStatus(slotId, SlotStatus.valueOf(status.trim().uppercase())).toSnapshot()

    private fun Offering.toSnapshot() = CatalogOfferingSnapshot(
        offeringId = requireNotNull(offeringId) { "Catalog offering is missing its identifier" },
        providerId = providerId,
        name = name,
        price = price,
        status = status.name,
        stockQuantity = stockQuantity
    )

    private fun Slot.toSnapshot() = CatalogSlotSnapshot(
        slotId = requireNotNull(slotId) { "Catalog slot is missing its identifier" },
        slotStart = slotStart,
        slotEnd = slotEnd,
        status = status.name
    )
}
