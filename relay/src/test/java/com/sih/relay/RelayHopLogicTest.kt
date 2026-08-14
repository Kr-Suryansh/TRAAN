package com.sih.relay

import com.sih.relay.model.EmergencyType
import com.sih.relay.model.SOSRequest
import com.sih.relay.model.SOSStatus
import com.sih.relay.model.SosLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Unit tests for [RelayHopLogic] — the pure Day 4 hop-accounting logic.
 *
 * Verifies the required physical progression:
 *   Phone A (origin):  relayHopCount = 0, status = PENDING_LOCAL
 *   Phone B (relay 1): relayHopCount = 1, status = IN_RELAY
 *   Phone C (relay 2): relayHopCount = 2, status = IN_RELAY
 *
 * Run with: ./gradlew :relay:test
 */
class RelayHopLogicTest {

    // ── Hop count progression (A=0 → B=1 → C=2) ──────────────────────────────

    @Test
    fun `origin SOS has hop count 0 and PENDING_LOCAL`() {
        val origin = buildOriginSos()
        assertEquals(0, origin.relayHopCount)
        assertEquals(SOSStatus.PENDING_LOCAL, origin.status)
    }

    @Test
    fun `first relay hop increments 0 to 1`() {
        val origin = buildOriginSos() // hopCount = 0
        val atB = RelayHopLogic.onRelayReceive(origin, "2026-08-14T10:00:01Z")
        assertEquals(1, atB.relayHopCount)
    }

    @Test
    fun `second relay hop increments 1 to 2`() {
        val atB = RelayHopLogic.onRelayReceive(buildOriginSos(), "2026-08-14T10:00:01Z")
        val atC = RelayHopLogic.onRelayReceive(atB, "2026-08-14T10:00:02Z")
        assertEquals(2, atC.relayHopCount)
    }

    @Test
    fun `hop count always increments on every hop even when already IN_RELAY`() {
        // Simulate a message that has already made several hops (IN_RELAY).
        var current = buildOriginSos()
        for (expectedHop in 1..10) {
            current = RelayHopLogic.onRelayReceive(current, "2026-08-14T10:00:0${expectedHop}Z")
            assertEquals(expectedHop, current.relayHopCount)
            assertEquals(SOSStatus.IN_RELAY, current.status)
        }
    }

    // ── Status transition ─────────────────────────────────────────────────────

    @Test
    fun `PENDING_LOCAL becomes IN_RELAY on first relay hop`() {
        val atB = RelayHopLogic.onRelayReceive(buildOriginSos(), "2026-08-14T10:00:01Z")
        assertEquals(SOSStatus.IN_RELAY, atB.status)
    }

    @Test
    fun `status remains IN_RELAY on subsequent hops`() {
        val atB = RelayHopLogic.onRelayReceive(buildOriginSos(), "2026-08-14T10:00:01Z")
        val atC = RelayHopLogic.onRelayReceive(atB, "2026-08-14T10:00:02Z")
        assertEquals(SOSStatus.IN_RELAY, atB.status)
        assertEquals(SOSStatus.IN_RELAY, atC.status)
    }

    @Test
    fun `UPLOADED status is preserved but hop count still increments`() {
        // A gateway-confirmed message that is still moving through the mesh
        // must keep its device-side UPLOADED status while hop accounting continues.
        val uploaded = buildOriginSos().copy(status = SOSStatus.UPLOADED, relayHopCount = 5)
        val next = RelayHopLogic.onRelayReceive(uploaded, "2026-08-14T10:00:06Z")
        assertEquals(SOSStatus.UPLOADED, next.status)
        assertEquals(6, next.relayHopCount)
    }

    // ── lastRelayedAt stamping ────────────────────────────────────────────────

    @Test
    fun `lastRelayedAt is updated on every hop`() {
        val atB = RelayHopLogic.onRelayReceive(buildOriginSos(), "2026-08-14T10:00:01Z")
        assertEquals("2026-08-14T10:00:01Z", atB.lastRelayedAt)

        val atC = RelayHopLogic.onRelayReceive(atB, "2026-08-14T10:00:02Z")
        assertEquals("2026-08-14T10:00:02Z", atC.lastRelayedAt)
    }

    @Test
    fun `lastRelayedAt differs across consecutive hops`() {
        val atB = RelayHopLogic.onRelayReceive(buildOriginSos(), "2026-08-14T10:00:01Z")
        val atC = RelayHopLogic.onRelayReceive(atB, "2026-08-14T10:00:02Z")
        assertNotEquals(atB.lastRelayedAt, atC.lastRelayedAt)
    }

    // ── Field preservation (no unintended mutation) ───────────────────────────

    @Test
    fun `immutable identity fields are preserved across hops`() {
        val origin = buildOriginSos(
            uuid = "sos-uuid-hop-test",
            deviceId = "device-origin-A",
            createdAt = "2026-08-14T09:00:00Z"
        )
        val atC = RelayHopLogic
            .onRelayReceive(
                RelayHopLogic.onRelayReceive(origin, "2026-08-14T10:00:01Z"),
                "2026-08-14T10:00:02Z"
            )
        // uuid / deviceId / createdAt are origin-fixed and never rewritten.
        assertEquals("sos-uuid-hop-test", atC.uuid)
        assertEquals("device-origin-A", atC.deviceId)
        assertEquals("2026-08-14T09:00:00Z", atC.createdAt)
    }

    @Test
    fun `relay payload fields are preserved across hops`() {
        val origin = buildOriginSos()
        val atC = RelayHopLogic
            .onRelayReceive(
                RelayHopLogic.onRelayReceive(origin, "2026-08-14T10:00:01Z"),
                "2026-08-14T10:00:02Z"
            )
        assertEquals(origin.location, atC.location)
        assertEquals(origin.isQuickSos, atC.isQuickSos)
        assertEquals(origin.emergencyType, atC.emergencyType)
        assertEquals(origin.severityHint, atC.severityHint)
        assertEquals(origin.peopleCount, atC.peopleCount)
        assertEquals(origin.medicalSnapshot, atC.medicalSnapshot)
        assertEquals(origin.customMessage, atC.customMessage)
        assertEquals(origin.contactNumber, atC.contactNumber)
    }

    @Test
    fun `returns a new instance rather than mutating the input`() {
        val origin = buildOriginSos()
        val relayed = RelayHopLogic.onRelayReceive(origin, "2026-08-14T10:00:01Z")
        // Input must be untouched (immutable data class: original keeps hop 0 / PENDING_LOCAL).
        assertSame(0, origin.relayHopCount)
        assertEquals(SOSStatus.PENDING_LOCAL, origin.status)
        assertNotEquals(origin, relayed)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildOriginSos(
        uuid: String = "origin-sos-uuid",
        deviceId: String = "device-origin-A",
        createdAt: String = "2026-08-14T09:00:00Z"
    ) = SOSRequest(
        uuid = uuid,
        deviceId = deviceId,
        createdAt = createdAt,
        location = SosLocation(lat = 28.6139f, lng = 77.2090f),
        isQuickSos = true,
        emergencyType = EmergencyType.UNSPECIFIED,
        severityHint = null,
        peopleCount = null,
        medicalSnapshot = null,
        customMessage = null,
        contactNumber = null,
        relayHopCount = 0,
        lastRelayedAt = createdAt,
        status = SOSStatus.PENDING_LOCAL
    )
}