package com.pawsnearme.paymentservice.repository

import com.pawsnearme.paymentservice.model.CodConfig
import com.pawsnearme.paymentservice.model.CouponReservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CouponReservationRepository : JpaRepository<CouponReservation, UUID> {
    fun countByPromotionIdAndStatusIn(promotionId: UUID, statuses: List<String>): Long
    fun countByPromotionIdAndUserIdAndStatusIn(promotionId: UUID, userId: UUID, statuses: List<String>): Long
    fun findByOrderIdAndStatus(orderId: UUID, status: String): CouponReservation?
    fun findByCodeAndUserIdAndStatus(code: String, userId: UUID, status: String): List<CouponReservation>
}

@Repository
interface CodConfigRepository : JpaRepository<CodConfig, String>
