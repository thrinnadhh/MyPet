package com.pawsnearme.dispatchservice.repository

import com.pawsnearme.dispatchservice.model.DispatchJob
import com.pawsnearme.dispatchservice.model.DispatchOffer
import com.pawsnearme.dispatchservice.model.JobStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DispatchJobRepository : JpaRepository<DispatchJob, UUID> {
    fun findByOrderId(orderId: UUID): DispatchJob?
    fun findByStatus(status: JobStatus): List<DispatchJob>
}

interface DispatchOfferRepository : JpaRepository<DispatchOffer, UUID> {
    fun findByJobId(jobId: UUID): List<DispatchOffer>
    fun findByJobIdAndResponseIsNull(jobId: UUID): DispatchOffer?
    fun findByJobIdAndCaptainId(jobId: UUID, captainId: UUID): DispatchOffer?
    fun findByCaptainIdAndResponseIsNull(captainId: UUID): List<DispatchOffer>
    fun findByCaptainIdAndResponseOrderByRespondedAtDesc(captainId: UUID, response: String): List<DispatchOffer>
}