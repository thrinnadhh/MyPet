package com.pawsnearme.captainservice.repository

import com.pawsnearme.captainservice.model.CaptainProfile
import com.pawsnearme.captainservice.model.CaptainEarning
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CaptainProfileRepository : JpaRepository<CaptainProfile, UUID>

interface CaptainEarningRepository : JpaRepository<CaptainEarning, UUID> {
    fun findByCaptainId(captainId: UUID): List<CaptainEarning>
}
