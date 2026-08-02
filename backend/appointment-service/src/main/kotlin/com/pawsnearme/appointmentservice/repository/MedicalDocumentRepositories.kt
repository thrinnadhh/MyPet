package com.pawsnearme.appointmentservice.repository

import com.pawsnearme.appointmentservice.model.MedicalDocument
import com.pawsnearme.appointmentservice.model.MedicalDocumentAccessLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MedicalDocumentRepository : JpaRepository<MedicalDocument, UUID> {
    fun findByOwnerUserIdOrderByCreatedAtDesc(ownerUserId: UUID): List<MedicalDocument>
    fun findByAppointmentIdOrderByCreatedAtDesc(appointmentId: UUID): List<MedicalDocument>
}

@Repository
interface MedicalDocumentAccessLogRepository : JpaRepository<MedicalDocumentAccessLog, UUID> {
    fun findByDocumentIdOrderByAccessedAtDesc(documentId: UUID): List<MedicalDocumentAccessLog>
}
