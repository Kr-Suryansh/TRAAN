package com.sih.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies that the TTL constants match the contract requirements.
 *
 * P0.4.4: TTL must be 72 hours (not 7 days).
 */
class TtlConstantTest {

    @Test
    fun `TTL_HOURS is 72`() {
        assertEquals(72L, SosConstants.TTL_HOURS)
    }

    @Test
    fun `TTL_SECONDS matches TTL_HOURS`() {
        assertEquals(SosConstants.TTL_HOURS * 3600L, SosConstants.TTL_SECONDS)
    }

    @Test
    fun `TTL is NOT 7 days (672 hours)`() {
        assertNotEquals(672L, SosConstants.TTL_HOURS)
    }
}
