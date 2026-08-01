package com.pawsnearme.contentservice.service

import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.contentservice.model.BannerBid
import com.pawsnearme.contentservice.module.RemoteProviderModuleApi
import com.pawsnearme.contentservice.repository.BannerBidRepository
import com.pawsnearme.contentservice.repository.PromoBannerRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestOperations
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class BannerBidAccessDeniedException(message: String) : RuntimeException(message)

object BannerAuctionSlots {
    val SLOT_DURATIONS: Map<Int, Int> = mapOf(
        0 to 5,
        1 to 4,
        2 to 3,
        3 to 2,
        4 to 1,
    )
}

@Service
@Transactional
class BannerAuctionService @Autowired constructor(
    private val bidRepo: BannerBidRepository,
    private val bannerRepo: PromoBannerRepository,
    private val providerModule: ProviderModuleApi
) {
    /** Compatibility constructor for focused legacy tests. */
    constructor(
        bidRepo: BannerBidRepository,
        bannerRepo: PromoBannerRepository,
        restTemplate: RestOperations,
        providerServiceUrl: String,
        gatewayTrustSecret: String
    ) : this(
        bidRepo,
        bannerRepo,
        RemoteProviderModuleApi(restTemplate, providerServiceUrl, gatewayTrustSecret)
    )

    private val logger = LoggerFactory.getLogger(BannerAuctionService::class.java)

    fun submitBid(
        providerId: UUID,
        slotOrder: Int,
        bidAmount: BigDecimal,
        windowEndsAt: Instant,
        callerId: UUID,
        callerRole: String?,
    ): BannerBid {
        if (slotOrder !in 0..4) {
            throw IllegalArgumentException("slotOrder must be between 0 and 4.")
        }
        if (bidAmount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("bidAmount must be positive.")
        }
        if (windowEndsAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("windowEndsAt must be in the future.")
        }
        assertProviderOwnership(providerId, callerId, callerRole)

        return bidRepo.save(
            BannerBid(
                providerId = providerId,
                ownerUserId = callerId,
                slotOrder = slotOrder,
                bidAmount = bidAmount,
                windowEndsAt = windowEndsAt,
                status = "PENDING",
            )
        )
    }

    @Transactional(readOnly = true)
    fun listBids(callerId: UUID, callerRole: String?): List<BannerBid> =
        if (callerRole?.uppercase() == "ADMIN") {
            bidRepo.findAll()
        } else {
            bidRepo.findByOwnerUserIdOrderByCreatedAtDesc(callerId)
        }

    fun cancelBid(bidId: UUID, callerId: UUID, callerRole: String?) {
        val bid = bidRepo.findById(bidId).orElseThrow { NoSuchElementException("Bid not found.") }
        assertBidAccess(bid, callerId, callerRole)
        if (bid.status != "PENDING") {
            throw IllegalStateException("Only pending bids can be cancelled.")
        }
        bid.status = "CANCELLED"
        bidRepo.save(bid)
    }

    @Transactional(readOnly = true)
    fun listAuctionOutcomes(): List<Map<String, Any?>> {
        val banners = bannerRepo.findByActiveTrueOrderBySortOrderAsc()
        return banners.map { banner ->
            mapOf(
                "slotOrder" to banner.sortOrder,
                "durationSec" to banner.durationSec,
                "title" to banner.title,
                "providerId" to banner.providerId?.toString(),
                "bidAmount" to banner.bidAmount,
                "status" to banner.status,
                "active" to banner.active,
            )
        }
    }

    @Scheduled(fixedDelay = 10_000)
    @SchedulerLock(
        name = "content-close-expired-banner-auctions",
        lockAtMostFor = "PT2M",
        lockAtLeastFor = "PT5S"
    )
    fun closeExpiredAuctions() {
        val now = Instant.now()
        val expiredWindows = bidRepo.findExpiredWindowEnds(now)
        expiredWindows.forEach { windowEnd -> closeAuctionWindow(windowEnd) }
    }

    fun closeAuctionWindow(windowEndsAt: Instant) {
        BannerAuctionSlots.SLOT_DURATIONS.keys.forEach { slotOrder ->
            val pendingBids = bidRepo.findBySlotOrderAndWindowEndsAtAndStatus(slotOrder, windowEndsAt, "PENDING")
            if (pendingBids.isEmpty()) return@forEach

            val winner = pendingBids.maxWithOrNull(
                compareBy<BannerBid> { it.bidAmount }.thenBy { it.createdAt }
            ) ?: return@forEach

            pendingBids.forEach { bid ->
                bid.status = if (bid.bidId == winner.bidId) "WON" else "LOST"
                bidRepo.save(bid)
            }

            val banner = bannerRepo.findBySortOrder(slotOrder)
            if (banner != null) {
                banner.providerId = winner.providerId
                banner.bidAmount = winner.bidAmount
                banner.status = "ACTIVE"
                banner.durationSec = BannerAuctionSlots.SLOT_DURATIONS[slotOrder] ?: banner.durationSec
                banner.active = true
                banner.updatedAt = Instant.now()
                bannerRepo.save(banner)
                logger.info(
                    "Banner slot {} won by provider {} for {}",
                    slotOrder,
                    winner.providerId,
                    winner.bidAmount,
                )
            }
        }
    }

    private fun assertBidAccess(bid: BannerBid, callerId: UUID, callerRole: String?) {
        when (callerRole?.uppercase()) {
            "ADMIN" -> return
            "MERCHANT" -> {
                if (bid.ownerUserId != callerId) {
                    throw BannerBidAccessDeniedException("Merchant cannot access another merchant's bid.")
                }
            }
            else -> throw BannerBidAccessDeniedException("Role $callerRole cannot access banner bids.")
        }
    }

    private fun assertProviderOwnership(providerId: UUID, callerId: UUID, callerRole: String?) {
        if (callerRole?.uppercase() == "ADMIN") return
        val ownerId = providerModule.ownerUserId(providerId)
            ?: throw BannerBidAccessDeniedException("Provider not found or inaccessible.")
        if (ownerId != callerId) {
            throw BannerBidAccessDeniedException("Merchant cannot bid for another provider's slot.")
        }
    }
}
