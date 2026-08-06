package com.pawsnearme.catalogservice.model

import jakarta.persistence.*
import java.util.UUID

/**
 * Catalog-owned read projection of the provider table.
 *
 * Its explicit JPA name is distinct from the provider module's authoritative
 * aggregate when both bounded contexts share the monolith persistence unit.
 */
@Entity(name = "CatalogProviderProjection")
@Table(name = "providers", schema = "providers")
class Provider(
    @Id
    @Column(name = "provider_id")
    var providerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false)
    var fulfillmentType: FulfillmentType,

    @Column(name = "owner_user_id")
    var ownerUserId: UUID? = null
)
