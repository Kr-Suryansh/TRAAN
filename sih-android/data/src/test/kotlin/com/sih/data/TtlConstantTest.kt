package com.sih.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression test for the centralized TTL constant (P0.4.4).
 *
 * Ensures that the authoritative value is within the 48–72 hour window
 * specified by the Day 1 contract and matches the expected 72-hour policy.
 *
 * If this test fails, it means a magic number was introduced somewhere
 * that disagrees with the constant — find and remove it.
 */
class TtlConstantTest {

    @Test
    fun `TTL_HOURS is within the 48-72 hour contract window`() {
        assertTrue("TTL_HOURS must be >= 48", SosConstants.TTL_HOURS >= 48L)
        assertTrue("TTL_HOURS must be <= 72", SosConstants.TTL_HOURS <= 72L)
    }

    @Test
    fun `TTL_HOURS is exactly 72 per implementation decision`() {
        assertEquals(
            "TTL_HOURS should be 72 (upper bound of 48-72h window)",
            72L, SosConstants.TTL_HOURS
        )
    }

    @Test
    fun `TTL_SECONDS matches TTL_HOURS times 3600`() {
        assertEquals(
            "TTL_SECONDS must equal TTL_HOURS * 3600",
            SosConstants.TTL_HOURS * 3600L, SosConstants.TTL_SECONDS
        )
    }

    @Test
    fun `TTL_SECONDS is never the old 7-day value`() {
        val sevenDaysInSeconds = 7 * 24 * 3600L
        assertNotEquals(
            "TTL must NOT be the incorrect 7-day value",
            sevenDaysInSeconds, SosConstants.TTL_SECONDS
        )
    }
}
