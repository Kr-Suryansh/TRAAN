package com.sih.data

import com.sih.data.model.EmergencyType
import com.sih.data.model.SosStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for domain enums — verifies contract values exactly match §1.2.
 */
class EnumContractTest {

    @Test
    fun `SosStatus api values match contract exactly`() {
        assertEquals("pending_local", SosStatus.PENDING_LOCAL.apiValue)
        assertEquals("in_relay",      SosStatus.IN_RELAY.apiValue)
        assertEquals("uploaded",      SosStatus.UPLOADED.apiValue)
    }

    @Test
    fun `EmergencyType api values match contract exactly`() {
        assertEquals("medical",              EmergencyType.MEDICAL.apiValue)
        assertEquals("trapped",             EmergencyType.TRAPPED.apiValue)
        assertEquals("structural_collapse", EmergencyType.STRUCTURAL_COLLAPSE.apiValue)
        assertEquals("flood_rescue",        EmergencyType.FLOOD_RESCUE.apiValue)
        assertEquals("fire",                EmergencyType.FIRE.apiValue)
        assertEquals("missing_person",      EmergencyType.MISSING_PERSON.apiValue)
        assertEquals("unspecified",         EmergencyType.UNSPECIFIED.apiValue)
    }

    @Test
    fun `EmergencyType has exactly 7 values`() {
        assertEquals(7, EmergencyType.entries.size)
    }

    @Test
    fun `SosStatus fromApiValue round-trips correctly`() {
        SosStatus.entries.forEach { status ->
            assertEquals(status, SosStatus.fromApiValue(status.apiValue))
        }
    }

    @Test
    fun `SosStatus fromApiValue unknown string returns PENDING_LOCAL`() {
        assertEquals(SosStatus.PENDING_LOCAL, SosStatus.fromApiValue("unknown_value"))
    }
}
