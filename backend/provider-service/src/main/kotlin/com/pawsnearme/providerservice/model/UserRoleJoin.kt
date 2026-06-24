package com.pawsnearme.providerservice.model

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class UserRoleKey(
    @Column(name = "user_id")
    var userId: UUID,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    var role: UserRole
) : Serializable

@Entity
@Table(name = "user_roles", schema = "identity")
class UserRoleJoin(
    @EmbeddedId
    var id: UserRoleKey,
    
    @Column(name = "granted_at", nullable = false, updatable = false)
    var grantedAt: Instant = Instant.now()
)
