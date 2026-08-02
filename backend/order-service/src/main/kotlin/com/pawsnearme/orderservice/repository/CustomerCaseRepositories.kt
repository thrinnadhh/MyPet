package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.CustomerCase
import com.pawsnearme.orderservice.model.CustomerCaseEvidence
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface CustomerCaseRepository : JpaRepository<CustomerCase, UUID> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID): List<CustomerCase>
    fun findAllByOrderByCreatedAtDesc(): List<CustomerCase>
}

@Repository
interface CustomerCaseEvidenceRepository : JpaRepository<CustomerCaseEvidence, UUID> {
    fun findByCaseIdOrderByCreatedAtAsc(caseId: UUID): List<CustomerCaseEvidence>
}
