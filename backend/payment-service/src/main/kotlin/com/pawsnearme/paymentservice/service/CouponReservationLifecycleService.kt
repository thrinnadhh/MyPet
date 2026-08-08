package com.pawsnearme.paymentservice.service

import com.pawsnearme.paymentservice.repository.CouponReservationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CouponReservationLifecycleService(
    private val couponReservationRepository: CouponReservationRepository,
) {
    /**
     * Cancel/reject must restore coupon eligibility whether the reservation is
     * still HELD or was already REDEEMED by a verified prepaid payment. The
     * operation is idempotent for already released/expired reservations.
     */
    @Transactional
    fun release(code: String, userId: UUID, orderId: UUID) {
        val reservation = couponReservationRepository.findByOrderIdAndStatusIn(
            orderId,
            listOf("HELD", "REDEEMED"),
        ) ?: return

        if (reservation.code != code.trim().uppercase() || reservation.userId != userId) {
            throw IllegalArgumentException("Coupon reservation does not belong to this order/customer")
        }
        reservation.status = "RELEASED"
        couponReservationRepository.save(reservation)
    }
}
