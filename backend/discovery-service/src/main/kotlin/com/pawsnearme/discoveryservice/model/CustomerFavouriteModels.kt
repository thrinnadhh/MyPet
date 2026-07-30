package com.pawsnearme.discoveryservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "favourites",
    schema = "customer",
    uniqueConstraints = [UniqueConstraint(columnNames = ["customer_id", "target_type", "target_id"])]
)
class CustomerFavourite(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: UUID? = null,

    @Column(name = "customer_id", nullable = false)
    var customerId: UUID,

    @Column(name = "target_type", nullable = false)
    var targetType: String,

    @Column(name = "target_id", nullable = false)
    var targetId: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

data class AddFavouriteRequest(
    val targetType: String,
    val targetId: String
)

data class FavouriteDto(
    val id: UUID,
    val customerId: UUID,
    val targetType: String,
    val targetId: String,
    val createdAt: Instant
)

fun CustomerFavourite.toDto() = FavouriteDto(
    id = id ?: UUID.randomUUID(),
    customerId = customerId,
    targetType = targetType,
    targetId = targetId,
    createdAt = createdAt
)
