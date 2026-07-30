package com.pawsnearme.providerservice.repository

import com.pawsnearme.providerservice.model.MedicalReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MedicalReportRepository : JpaRepository<MedicalReport, UUID> {
    fun findAllByPetIdOrderByCreatedAtDesc(petId: UUID): List<MedicalReport>
    fun findAllByOwnerIdOrderByCreatedAtDesc(ownerId: UUID): List<MedicalReport>
}
