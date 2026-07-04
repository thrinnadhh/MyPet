package com.pawsnearme.contentservice.service

import com.pawsnearme.contentservice.model.BannerBid
import com.pawsnearme.contentservice.repository.BannerBidRepository
import com.pawsnearme.contentservice.repository.PromoBannerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.web.client.RestOperations
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class BannerAuctionServiceTests {

    private val bidRepo: BannerBidRepository = mock()
    private val bannerRepo: PromoBannerRepository = mock()
    private val restTemplate: RestOperations = mock()
    private val merchantId = UUID.randomUUID()
    private val otherMerchantId = UUID.randomUUID()
    private val providerId = UUID.randomUUID()
    private val windowEnd = Instant.now().plus(1, ChronoUnit.HOURS)

    private val service = BannerAuctionService(
        bidRepo = bidRepo,
        bannerRepo = bannerRepo,
        restTemplate = restTemplate,
        providerServiceUrl = "http://localhost:8081",
        gatewayTrustSecret = "",
    )

    @Test
    fun `closeAuctionWindow - highest bid wins slot and losers marked LOST`() {
        val windowEndsAt = Instant.parse("2026-07-01T12:00:00Z")
        val lowBid = BannerBid(
            bidId = UUID.randomUUID(),
            providerId = providerId,
            ownerUserId = merchantId,
            slotOrder = 0,
            bidAmount = BigDecimal("100.00"),
            windowEndsAt = windowEndsAt,
            status = "PENDING",
        )
        val highBid = BannerBid(
            bidId = UUID.randomUUID(),
            providerId = providerId,
            ownerUserId = merchantId,
            slotOrder = 0,
            bidAmount = BigDecimal("250.00"),
            windowEndsAt = windowEndsAt,
            status = "PENDING",
        )
        val banner = com.pawsnearme.contentservice.model.PromoBanner(
            bannerId = UUID.randomUUID(),
            title = "Slot 1",
            subtitle = "Test",
            sortOrder = 0,
            durationSec = 5,
        )

        whenever(bidRepo.findBySlotOrderAndWindowEndsAtAndStatus(0, windowEndsAt, "PENDING"))
            .thenReturn(listOf(lowBid, highBid))
        whenever(bannerRepo.findBySortOrder(0)).thenReturn(banner)
        whenever(bidRepo.save(any())).thenAnswer { it.getArgument(0) }

        service.closeAuctionWindow(windowEndsAt)

        assertEquals("LOST", lowBid.status)
        assertEquals("WON", highBid.status)
        assertEquals(providerId, banner.providerId)
        assertEquals(BigDecimal("250.00"), banner.bidAmount)
        assertEquals("ACTIVE", banner.status)
        assertEquals(5, banner.durationSec)
    }

    @Test
    fun `cancelBid - merchant cannot cancel another merchant bid`() {
        val bid = BannerBid(
            bidId = UUID.randomUUID(),
            providerId = providerId,
            ownerUserId = otherMerchantId,
            slotOrder = 1,
            bidAmount = BigDecimal("50.00"),
            windowEndsAt = windowEnd,
            status = "PENDING",
        )
        whenever(bidRepo.findById(bid.bidId!!)).thenReturn(java.util.Optional.of(bid))

        assertThrows<BannerBidAccessDeniedException> {
            service.cancelBid(bid.bidId!!, merchantId, "MERCHANT")
        }
    }

    @Test
    fun `listBids - merchant only sees own bids`() {
        val ownBid = BannerBid(
            bidId = UUID.randomUUID(),
            providerId = providerId,
            ownerUserId = merchantId,
            slotOrder = 2,
            bidAmount = BigDecimal("75.00"),
            windowEndsAt = windowEnd,
        )
        whenever(bidRepo.findByOwnerUserIdOrderByCreatedAtDesc(merchantId)).thenReturn(listOf(ownBid))

        val bids = service.listBids(merchantId, "MERCHANT")

        assertEquals(1, bids.size)
        assertEquals(merchantId, bids.first().ownerUserId)
        verify(bidRepo, never()).findAll()
    }
}
