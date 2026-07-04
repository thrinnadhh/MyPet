package com.pawsnearme.notificationservice.repository

import com.pawsnearme.notificationservice.model.DevicePushToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DevicePushTokenRepository : JpaRepository<DevicePushToken, UUID> {
    fun findByUserId(userId: UUID): List<DevicePushToken>
    fun findByExpoPushToken(expoPushToken: String): DevicePushToken?
    fun deleteByUserIdAndExpoPushToken(userId: UUID, expoPushToken: String)
}
