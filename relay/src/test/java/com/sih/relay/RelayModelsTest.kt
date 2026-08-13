package com.sih.relay

import com.sih.relay.model.EmergencyType
import com.sih.relay.model.UserMedicalProfile
import com.sih.relay.model.RelayManifest
import com.sih.relay.model.SOSRequest
import com.sih.relay.model.SOSStatus
import com.sih.relay.model.SeverityHint
import com.sih.relay.model.SosLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the relay model classes.
 *
 * These tests run on the JVM (no Android device or emulator needed).
 * Run with: ./gradlew :relay:test
 *
 * PURPOSE:
 *   Verify that field names, default values, and enum values match
 *   the contract defined in day1-contracts-and-repo-setup.md §1.2 and §1.3.
 *   If a contract field is accidentally renamed or an enum value removed,
 *   a test here will catch it before it breaks other components.
 */
class RelayModelsTest {

    // ── SOSRequest — default values ─────────────────────────────────────────

    @Test
    fun `SOSRequest - relay_hop_count defaults to 0`() {
        val sos = buildMinimalSosRequest()
        assertEquals(0, sos.relayHopCount)
    }

    @Test
    fun `SOSRequest - status defaults to PENDING_LOCAL`() {
        val sos = buildMinimalSosRequest()
        assertEquals(SOSStatus.PENDING_LOCAL, sos.status)
    }

    @Test
    fun `SOSRequest - optional fields default to null`() {
        val sos = buildMinimalSosRequest()
        assertNull(sos.severityHint)
        assertNull(sos.peopleCount)
        assertNull(sos.medicalSnapshot)
        assertNull(sos.customMessage)
        assertNull(sos.contactNumber)
    }

    // ── SOSRequest — field assignment ───────────────────────────────────────

    @Test
    fun `SOSRequest - isQuickSos is stored correctly`() {
        assertEquals(true,  buildMinimalSosRequest(isQuickSos = true).isQuickSos)
        assertEquals(false, buildMinimalSosRequest(isQuickSos = false).isQuickSos)
    }

    @Test
    fun `SOSRequest - all emergency types can be assigned`() {
        EmergencyType.values().forEach { type ->
            val sos = buildMinimalSosRequest(emergencyType = type)
            assertEquals(type, sos.emergencyType)
        }
    }

    @Test
    fun `SOSRequest - all severity hints can be assigned`() {
        SeverityHint.values().forEach { hint ->
            val sos = buildMinimalSosRequest(severityHint = hint)
            assertEquals(hint, sos.severityHint)
        }
    }

    @Test
    fun `SOSRequest - all status values can be assigned`() {
        SOSStatus.values().forEach { status ->
            val sos = buildMinimalSosRequest(status = status)
            assertEquals(status, sos.status)
        }
    }

    @Test
    fun `SOSRequest - relay_hop_count increments correctly via copy`() {
        val sos = buildMinimalSosRequest()
        val afterOneHop = sos.copy(relayHopCount = sos.relayHopCount + 1)
        assertEquals(1, afterOneHop.relayHopCount)
        val afterTwoHops = afterOneHop.copy(relayHopCount = afterOneHop.relayHopCount + 1)
        assertEquals(2, afterTwoHops.relayHopCount)
    }

    @Test
    fun `SOSRequest - medical snapshot is stored and readable`() {
        val snapshot = UserMedicalProfile(
            name = "Ramesh Kumar",
            age = 45,
            bloodType = "B+",
            medicalConditions = listOf("diabetic", "hypertensive"),
            medications = listOf("metformin 500mg"),
            allergies = listOf("penicillin"),
            emergencyContactName = "Priya Kumar",
            emergencyContactNumber = "9876543210"
        )
        val sos = buildMinimalSosRequest(medicalSnapshot = snapshot)
        assertEquals("B+", sos.medicalSnapshot?.bloodType)
        assertEquals(2, sos.medicalSnapshot?.medicalConditions?.size)
        assertEquals("Priya Kumar", sos.medicalSnapshot?.emergencyContactName)
    }

    // ── SOSRequest — uuid uniqueness (contract requires UUIDv4) ─────────────

    @Test
    fun `SOSRequest - two SOSes with different UUIDs are not equal`() {
        val sos1 = buildMinimalSosRequest(uuid = "uuid-001")
        val sos2 = buildMinimalSosRequest(uuid = "uuid-002")
        assert(sos1 != sos2)
        assert(sos1.uuid != sos2.uuid)
    }

    // ── SosLocation ─────────────────────────────────────────────────────────

    @Test
    fun `SosLocation - accuracy_m is optional (defaults to null)`() {
        val loc = SosLocation(lat = 28.6139f, lng = 77.2090f)
        assertNull(loc.accuracyMeters)
    }

    @Test
    fun `SosLocation - stores lat and lng precisely`() {
        val loc = SosLocation(lat = 19.07600f, lng = 72.87770f, accuracyMeters = 10.5f)
        assertEquals(19.07600f, loc.lat,           0.000001f)
        assertEquals(72.87770f, loc.lng,           0.000001f)
        assertEquals(10.5f,     loc.accuracyMeters!!, 0.001f)
    }

    // ── RelayManifest ────────────────────────────────────────────────────────

    @Test
    fun `RelayManifest - stores uuid list correctly`() {
        val manifest = RelayManifest(
            deviceId    = "device-abc",
            knownUuids  = listOf("uuid-1", "uuid-2", "uuid-3"),
            timestamp   = "2026-08-13T00:00:00Z"
        )
        assertEquals("device-abc", manifest.deviceId)
        assertEquals(3, manifest.knownUuids.size)
        assertEquals("uuid-2", manifest.knownUuids[1])
    }

    @Test
    fun `RelayManifest - empty knownUuids is valid (new device)`() {
        val manifest = RelayManifest(
            deviceId   = "brand-new-device",
            knownUuids = emptyList(),
            timestamp  = "2026-08-13T00:00:00Z"
        )
        assertEquals(0, manifest.knownUuids.size)
    }

    // ── Enum completeness — contract value counts ────────────────────────────
    // If someone accidentally removes an enum value, these tests fail immediately.

    @Test
    fun `EmergencyType - has exactly 7 values per contract`() {
        // Contract: medical | trapped | structural_collapse | flood_rescue | fire | missing_person | unspecified
        assertEquals(7, EmergencyType.values().size)
    }

    @Test
    fun `EmergencyType - has exactly the values defined in the contract`() {
        val contractNames = setOf(
            "MEDICAL", "TRAPPED", "STRUCTURAL_COLLAPSE",
            "FLOOD_RESCUE", "FIRE", "MISSING_PERSON", "UNSPECIFIED"
        )
        assertEquals(contractNames, EmergencyType.values().map { it.name }.toSet())
    }

    @Test
    fun `SeverityHint - has exactly 4 values per contract`() {
        // Contract: critical | high | medium | low
        assertEquals(4, SeverityHint.values().size)
    }

    @Test
    fun `SeverityHint - has exactly the values defined in the contract`() {
        val contractNames = setOf("CRITICAL", "HIGH", "MEDIUM", "LOW")
        assertEquals(contractNames, SeverityHint.values().map { it.name }.toSet())
    }

    @Test
    fun `SOSStatus - has exactly 3 values per contract`() {
        // Contract: pending_local | in_relay | uploaded
        assertEquals(3, SOSStatus.values().size)
    }

    @Test
    fun `SOSStatus - has exactly the values defined in the contract`() {
        val contractNames = setOf("PENDING_LOCAL", "IN_RELAY", "UPLOADED")
        assertEquals(contractNames, SOSStatus.values().map { it.name }.toSet())
    }

    // ── UserMedicalProfile ───────────────────────────────────────────────────

    @Test
    fun `UserMedicalProfile - all fields default to null or empty`() {
        val snap = UserMedicalProfile()
        assertNull(snap.name)
        assertNull(snap.age)
        assertNull(snap.bloodType)
        assertEquals(emptyList<String>(), snap.medicalConditions)
        assertEquals(emptyList<String>(), snap.medications)
        assertEquals(emptyList<String>(), snap.allergies)
        assertNull(snap.emergencyContactName)
        assertNull(snap.emergencyContactNumber)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun buildMinimalSosRequest(
        uuid: String             = "550e8400-e29b-41d4-a716-446655440000",
        deviceId: String         = "test-device-001",
        isQuickSos: Boolean      = true,
        emergencyType: EmergencyType = EmergencyType.UNSPECIFIED,
        severityHint: SeverityHint?  = null,
        medicalSnapshot: UserMedicalProfile? = null,
        status: SOSStatus        = SOSStatus.PENDING_LOCAL
    ) = SOSRequest(
        uuid          = uuid,
        deviceId      = deviceId,
        createdAt     = "2026-08-13T00:00:00Z",
        location      = SosLocation(lat = 28.6139f, lng = 77.2090f),
        isQuickSos    = isQuickSos,
        emergencyType = emergencyType,
        severityHint  = severityHint,
        medicalSnapshot = medicalSnapshot,
        status        = status,
        lastRelayedAt = "2026-08-13T00:00:00Z"
    )
}
