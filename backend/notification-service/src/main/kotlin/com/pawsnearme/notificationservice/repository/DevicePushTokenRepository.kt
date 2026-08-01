package com.pawsnearme.notificationservice.repository

import com.pawsnearme.notificationservice.model.DevicePushToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
interface DevicePushTokenRepository : JpaRepository<DevicePushToken, UUID> {
    fun findByUserId(userId: UUID): List<DevicePushToken>
    fun findByExpoPushToken(expoPushToken: String): DevicePushToken?

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        DELETE FROM DevicePushToken token
        WHERE token.userId = :userId
          AND token.expoPushToken = :expoPushToken
        """
    )
    fun deleteByUserIdAndExpoPushToken(
        @Param("userId") userId: UUID,
        @Param("expoPushToken") expoPushToken: String
    ): Int
}
