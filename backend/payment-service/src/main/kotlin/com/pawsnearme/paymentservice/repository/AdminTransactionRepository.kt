package com.pawsnearme.paymentservice.repository

import com.pawsnearme.paymentservice.model.Transaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface AdminTransactionRepository : JpaRepository<Transaction, UUID> {
    @Query(
        """
        select t from Transaction t
        where (:referenceId is null or t.referenceId = :referenceId)
          and (:status is null or upper(t.status) = upper(:status))
          and (:fromTime is null or t.createdAt >= :fromTime)
          and (:toTime is null or t.createdAt < :toTime)
        order by t.createdAt desc
        """
    )
    fun search(
        @Param("referenceId") referenceId: UUID?,
        @Param("status") status: String?,
        @Param("fromTime") fromTime: Instant?,
        @Param("toTime") toTime: Instant?,
        pageable: Pageable
    ): Page<Transaction>

    fun findByGatewayTransactionId(gatewayTransactionId: String): Transaction?
}
