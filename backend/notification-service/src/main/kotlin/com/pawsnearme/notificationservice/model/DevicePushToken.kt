package com.pawsnearme.notificationservice.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(schema = "notifications", name = "device_push_tokens")
class DevicePushToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id")
    var tokenId: UUID? = null,

    @Column(name = "user_id", nullable = false)
    var userId: UUID,

    @Column(name = "expo_push_token", nullable = false)
    var expoPushToken: String,

    @Column(name = "platform", nullable = false)
    var platform: String,

    @Column(name = "app_role")
    var appRole: String? = null,

    @Column(name = "sound_profile", nullable = false)
    var soundProfile: String = "default",

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
