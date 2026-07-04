package com.pawsnearme.contentservice.migration

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BannerAuctionMigrationTest {

    @Test
    fun `V3 migration defines auction schema`() {
        val sql = this::class.java.getResourceAsStream("/db/migration/V3__banner_auction.sql")!!
            .bufferedReader()
            .readText()

        assertTrue(sql.contains("provider_id"))
        assertTrue(sql.contains("bid_amount"))
        assertTrue(sql.contains("PENDING_BID"))
        assertTrue(sql.contains("banner_bids"))
        assertTrue(sql.contains("owner_user_id"))
        assertTrue(sql.contains("window_ends_at"))
    }
}
