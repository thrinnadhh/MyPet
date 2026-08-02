package com.pawsnearme.orderservice.repository

import com.pawsnearme.orderservice.model.AdminAuditLog
import com.pawsnearme.orderservice.model.ServiceAreaConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, UUID> {
    fun findTop100ByOrderByCreatedAtDesc(): List<AdminAuditLog>
}

@Repository
interface ServiceAreaConfigRepository : JpaRepository<ServiceAreaConfig, String> {
    fun findAllByOrderByCityAscPincodeAsc(): List<ServiceAreaConfig>
}
