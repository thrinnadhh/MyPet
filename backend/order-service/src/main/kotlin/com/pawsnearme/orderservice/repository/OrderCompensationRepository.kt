package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.OrderCompensation
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface OrderCompensationRepository : JpaRepository<OrderCompensation, UUID> {
    fun findTop50ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(statuses: Collection<String>, now: Instant): List<OrderCompensation>
}
