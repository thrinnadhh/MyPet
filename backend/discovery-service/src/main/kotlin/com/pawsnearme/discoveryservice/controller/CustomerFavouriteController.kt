package com.pawsnearme.discoveryservice.controller

import com.pawsnearme.discoveryservice.model.AddFavouriteRequest
import com.pawsnearme.discoveryservice.model.FavouriteDto
import com.pawsnearme.discoveryservice.service.CustomerFavouriteService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/customer/favourites")
class CustomerFavouriteController(
    private val favouriteService: CustomerFavouriteService
) {

    @GetMapping
    fun getFavourites(
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<List<FavouriteDto>> {
        if (authenticatedUserId.isNull_or_blank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val customerId = UUID.fromString(authenticatedUserId)
        val favourites = favouriteService.getCustomerFavourites(customerId)
        return ResponseEntity.ok(favourites)
    }

    @PostMapping
    fun addFavourite(
        @RequestBody request: AddFavouriteRequest,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<FavouriteDto> {
        if (authenticatedUserId.isNull_or_blank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val customerId = UUID.fromString(authenticatedUserId)
        val result = favouriteService.addFavourite(customerId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @DeleteMapping
    fun removeFavourite(
        @RequestParam targetType: String,
        @RequestParam targetId: String,
        @RequestHeader("X-User-Id", required = false) authenticatedUserId: String?
    ): ResponseEntity<Void> {
        if (authenticatedUserId.isNull_or_blank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val customerId = UUID.fromString(authenticatedUserId)
        favouriteService.removeFavourite(customerId, targetType, targetId)
        return ResponseEntity.noContent().build()
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
