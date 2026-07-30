package com.pawsnearme.discoveryservice.service

import com.pawsnearme.discoveryservice.model.*
import com.pawsnearme.discoveryservice.repository.CustomerFavouriteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CustomerFavouriteService(
    private val favouriteRepository: CustomerFavouriteRepository
) {
    fun getCustomerFavourites(customerId: UUID): List<FavouriteDto> {
        return favouriteRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId).map { it.toDto() }
    }

    @Transactional
    fun addFavourite(customerId: UUID, req: AddFavouriteRequest): FavouriteDto {
        val existing = favouriteRepository.findByCustomerIdAndTargetTypeAndTargetId(
            customerId, req.targetType.uppercase(), req.targetId
        )
        if (existing.isPresent) {
            return existing.get().toDto()
        }
        val entity = CustomerFavourite(
            customerId = customerId,
            targetType = req.targetType.uppercase(),
            targetId = req.targetId
        )
        return favouriteRepository.save(entity).toDto()
    }

    @Transactional
    fun removeFavourite(customerId: UUID, targetType: String, targetId: String) {
        favouriteRepository.deleteByCustomerIdAndTargetTypeAndTargetId(
            customerId, targetType.uppercase(), targetId
        )
    }
}
