package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.CustomerCase
import com.pawsnearme.orderservice.model.CustomerCaseEvidence
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface CustomerCaseRepository : JpaRepository<CustomerCase, UUID> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: UUID): List<CustomerCase>
    fun findAllByOrderByCreatedAtDesc(): List<CustomerCase>
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<CustomerCase>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerCase c where c.caseId = :caseId")
    fun findByIdForUpdate(@Param("caseId") caseId: UUID): Optional<CustomerCase>
}

@Repository
interface CustomerCaseEvidenceRepository : JpaRepository<CustomerCaseEvidence, UUID> {
    fun findByCaseIdOrderByCreatedAtAsc(caseId: UUID): List<CustomerCaseEvidence>
}
