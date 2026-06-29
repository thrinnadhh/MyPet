package com.pawsnearme.paymentservice.repository

import com.pawsnearme.paymentservice.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface TransactionRepository : JpaRepository<Transaction, UUID> {
    fun findByReferenceId(referenceId: UUID): Transaction?
}

@Repository
interface PayoutRepository : JpaRepository<Payout, UUID> {
    fun findByPayeeUserId(payeeUserId: UUID): List<Payout>
}

@Repository
interface PromotionRepository : JpaRepository<Promotion, UUID> {
    fun findByCode(code: String): Promotion?
    fun findByProviderId(providerId: UUID): List<Promotion>
    fun findByProviderIdIsNull(): List<Promotion>
    fun existsByCode(code: String): Boolean
}

@Repository
interface OrderRefRepository : JpaRepository<OrderRef, UUID> {
    fun findByStatusAndDeliveredAtBetween(status: String, start: Instant, end: Instant): List<OrderRef>
}

@Repository
interface AppointmentRefRepository : JpaRepository<AppointmentRef, UUID> {
    fun findByStatusAndCompletedAtBetween(status: String, start: Instant, end: Instant): List<AppointmentRef>
}

@Repository
interface CaptainEarningRefRepository : JpaRepository<CaptainEarningRef, UUID> {
    fun findByPayoutIdIsNullAndEarnedAtBetween(start: Instant, end: Instant): List<CaptainEarningRef>
}

@Repository
interface ProviderRefRepository : JpaRepository<ProviderRef, UUID>
