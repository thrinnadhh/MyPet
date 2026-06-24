package com.pawsnearme.catalogservice.model

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "providers", schema = "providers")
class Provider(
    @Id
    @Column(name = "provider_id")
    var providerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false)
    var fulfillmentType: FulfillmentType
)

