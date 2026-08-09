package com.pawsnearme.paymentservice.controller

import com.pawsnearme.paymentservice.model.Payout
import com.pawsnearme.paymentservice.repository.AppointmentRefRepository
import com.pawsnearme.paymentservice.repository.OrderRefRepository
import com.pawsnearme.paymentservice.repository.PayoutRepository
import com.pawsnearme.paymentservice.repository.PlatformCommissionLedgerRepository
import com.pawsnearme.paymentservice.repository.ProviderRefRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class MerchantFinanceSummary(
    val providerId: UUID,
    val commissionPercent: BigDecimal,
    val deliveredOrderCount: Int,
    val completedAppointmentCount: Int,
    val orderGrossRevenue: BigDecimal,
    val orderCommission: BigDecimal,
    val orderNetRevenue: BigDecimal,
    val appointmentRevenue: BigDecimal,
    val totalGrossRevenue: BigDecimal,
    val totalNetRevenue: BigDecimal,
    val accountPaidOut: BigDecimal,
    val accountPayoutInFlight: BigDecimal,
    val payoutScope: String = "MERCHANT_ACCOUNT",
    val payoutPage: Int,
    val payoutPageSize: Int,
    val payoutTotalRecords: Long,
    val payouts: List<Payout>
)

@Service
class MerchantFinanceService(
    private val providerRefRepository: ProviderRefRepository,
    private val orderRefRepository: OrderRefRepository,
    private val appointmentRefRepository: AppointmentRefRepository,
    private val platformCommissionLedgerRepository: PlatformCommissionLedgerRepository,
    private val payoutRepository: PayoutRepository
) {
    @Transactional(readOnly = true)
    fun summary(
        providerId: UUID,
        requesterId: String?,
        requesterRole: String?,
        payoutPage: Int = 0,
        payoutSize: Int = 50,
    ): MerchantFinanceSummary {
        val provider = providerRefRepository.findById(providerId)
            .orElseThrow { NoSuchElementException("Provider not found: $providerId") }
        if (requesterRole != "ADMIN" && requesterId != provider.ownerUserId.toString()) {
            throw PaymentAccessDeniedException("Access denied to merchant finance")
        }

        val deliveredOrders = orderRefRepository.findByProviderIdAndStatus(providerId, "DELIVERED")
        val completedAppointments = appointmentRefRepository.findByProviderIdAndStatus(providerId, "COMPLETED")
        val ledgerByOrder = platformCommissionLedgerRepository.findByProviderId(providerId)
            .mapNotNull { ledger -> ledger.orderId?.let { it to ledger } }
            .toMap()

        var orderGross = BigDecimal.ZERO
        var orderCommission = BigDecimal.ZERO
        var orderNet = BigDecimal.ZERO
        deliveredOrders.forEach { order ->
            val ledger = ledgerByOrder[order.orderId]
            if (ledger != null) {
                orderGross = orderGross.add(ledger.grossAmount)
                orderCommission = orderCommission.add(ledger.commissionAmount)
                orderNet = orderNet.add(ledger.netMerchantAmount)
            } else {
                val commission = order.totalAmount
                    .multiply(provider.commissionPct)
                    .divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
                orderGross = orderGross.add(order.totalAmount)
                orderCommission = orderCommission.add(commission)
                orderNet = orderNet.add(order.totalAmount.subtract(commission))
            }
        }

        val appointmentRevenue = completedAppointments.fold(BigDecimal.ZERO) { total, appointment ->
            total.add(appointment.priceAmount)
        }
        val boundedPage = payoutPage.coerceAtLeast(0)
        val boundedSize = payoutSize.coerceIn(1, 100)
        val payoutHistory = payoutRepository.findByPayeeUserIdOrderByCreatedAtDesc(
            provider.ownerUserId,
            PageRequest.of(boundedPage, boundedSize),
        )
        val accountPaidOut = payoutRepository.sumPaidAmountByPayeeUserId(provider.ownerUserId)
        val accountPayoutInFlight = payoutRepository.sumInFlightAmountByPayeeUserId(provider.ownerUserId)

        return MerchantFinanceSummary(
            providerId = providerId,
            commissionPercent = provider.commissionPct,
            deliveredOrderCount = deliveredOrders.size,
            completedAppointmentCount = completedAppointments.size,
            orderGrossRevenue = orderGross.money(),
            orderCommission = orderCommission.money(),
            orderNetRevenue = orderNet.money(),
            appointmentRevenue = appointmentRevenue.money(),
            totalGrossRevenue = orderGross.add(appointmentRevenue).money(),
            totalNetRevenue = orderNet.add(appointmentRevenue).money(),
            accountPaidOut = accountPaidOut.money(),
            accountPayoutInFlight = accountPayoutInFlight.money(),
            payoutPage = boundedPage,
            payoutPageSize = boundedSize,
            payoutTotalRecords = payoutHistory.totalElements,
            payouts = payoutHistory.content,
        )
    }

    private fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
}

@RestController
@RequestMapping("/api/v1/payments/merchant-finance")
class MerchantFinanceController(private val merchantFinanceService: MerchantFinanceService) {
    @GetMapping("/providers/{providerId}")
    fun providerSummary(
        @PathVariable providerId: UUID,
        @RequestHeader("X-User-Id", required = false) xUserId: String?,
        @RequestHeader("X-User-Role", required = false) xUserRole: String?,
        @RequestParam(defaultValue = "0") payoutPage: Int,
        @RequestParam(defaultValue = "50") payoutSize: Int,
    ): ResponseEntity<MerchantFinanceSummary> = ResponseEntity.ok(
        merchantFinanceService.summary(providerId, xUserId, xUserRole, payoutPage, payoutSize)
    )
}
