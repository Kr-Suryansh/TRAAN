package com.sih.data

import com.sih.data.model.EmergencyType
import com.sih.data.model.SosStatus
import org.junit.Assert.*
import org.junit.Test

/**
 * Contract tests verifying that enum values match the wire format defined in
 * day1-contracts-and-repo-setup.md §1.2.
 */
class EnumContractTest {

    @Test
    fun `EmergencyType apiValue matches contract`() {
        assertEquals("medical", EmergencyType.MEDICAL.apiValue)
        assertEquals("trapped", EmergencyType.TRAPPED.apiValue)
        assertEquals("structural_collapse", EmergencyType.STRUCTURAL_COLLAPSE.apiValue)
        assertEquals("flood_rescue", EmergencyType.FLOOD_RESCUE.apiValue)
        assertEquals("fire", EmergencyType.FIRE.apiValue)
        assertEquals("missing_person", EmergencyType.MISSING_PERSON.apiValue)
        assertEquals("unspecified", EmergencyType.UNSPECIFIED.apiValue)
    }

    @Test
    fun `SosStatus apiValue matches contract`() {
        assertEquals("pending_local", SosStatus.PENDING_LOCAL.apiValue)
        assertEquals("in_relay", SosStatus.IN_RELAY.apiValue)
        assertEquals("uploaded", SosStatus.UPLOADED.apiValue)
    }
