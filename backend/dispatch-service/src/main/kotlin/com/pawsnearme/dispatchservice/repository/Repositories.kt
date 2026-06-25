package com.pawsnearme.dispatchservice.repository

import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.DispatchOffer
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DispatchJobRepository : JpaRepository<DispatchJob, UUID> {
    fun findByOrderId(orderId: UUID): DispatchJob?
}

interface DispatchOfferRepository : JpaRepository<DispatchOffer, UUID> {
    fun findByJobId(jobId: UUID): List<DispatchOffer>
    fun findByJobIdAndResponseIsNull(jobId: UUID): DispatchOffer?
    fun findByCaptainIdAndResponseIsNull(captainId: UUID): List<DispatchOffer>
}
