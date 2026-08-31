package com.sih.relay

import com.sih.relay.model.EmergencyType
import com.sih.relay.model.RelayManifest
import com.sih.relay.model.SOSRequest
import com.sih.relay.model.SOSStatus
import com.sih.relay.model.SosLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [RelayPayloadCodec] — the internal Day 3 wire-format encoder/decoder.
 *
 * These tests run on the JVM (no Android device or emulator needed).
 * Run with: ./gradlew :relay:test
 *
 * Coverage:
 *   - Type-tag prefix encoding for both payload types
 *   - [RelayManifest] encode → decode round-trip (all fields)
 *   - [SOSRequest] encode → decode round-trip (key fields)
 *   - Edge cases: empty byte array, single-byte array, empty UUID list
 *   - UUID diff logic (set-subtraction) that underpins [RelayManager.sendMissingSos]
 */
class RelayPayloadCodecTest {

    // ── Type-tag prefix ───────────────────────────────────────────────────────

    @Test
    fun `encodeManifest - first byte is TYPE_MANIFEST`() {
        val encoded = RelayPayloadCodec.encodeManifest(buildMinimalManifest())
        assertEquals(RelayPayloadCodec.TYPE_MANIFEST, RelayPayloadCodec.peekType(encoded))
    }

    @Test
    fun `encodeSos - first byte is TYPE_SOS`() {
        val encoded = RelayPayloadCodec.encodeSos(buildMinimalSos())
        assertEquals(RelayPayloadCodec.TYPE_SOS, RelayPayloadCodec.peekType(encoded))
    }

    @Test
    fun `TYPE_MANIFEST and TYPE_SOS are distinct`() {
        assert(RelayPayloadCodec.TYPE_MANIFEST != RelayPayloadCodec.TYPE_SOS)
    }

    @Test
    fun `peekType - returns null for empty byte array`() {
        assertNull(RelayPayloadCodec.peekType(ByteArray(0)))
    }

    @Test
    fun `body - returns empty array for empty input`() {
        assertEquals(0, RelayPayloadCodec.body(ByteArray(0)).size)
    }

    @Test
    fun `body - returns empty array for single-byte input (type tag only)`() {
        assertEquals(0, RelayPayloadCodec.body(ByteArray(1) { 0x01 }).size)
    }

    @Test
    fun `body - strips exactly one byte from a multi-byte payload`() {
        val encoded = RelayPayloadCodec.encodeManifest(buildMinimalManifest())
        // body() should be exactly one byte shorter than the full encoded array
        assertEquals(encoded.size - 1, RelayPayloadCodec.body(encoded).size)
    }

    // ── RelayManifest round-trip ──────────────────────────────────────────────

    @Test
    fun `RelayManifest round-trip - deviceId preserved`() {
        val original = buildMinimalManifest(deviceId = "relay-device-xyz")
        val decoded = decodeManifest(RelayPayloadCodec.encodeManifest(original))
        assertEquals("relay-device-xyz", decoded.deviceId)
    }

    @Test
    fun `RelayManifest round-trip - knownUuids preserved`() {
        val uuids = listOf("uuid-alpha", "uuid-beta", "uuid-gamma")
        val original = buildMinimalManifest(knownUuids = uuids)
        val decoded = decodeManifest(RelayPayloadCodec.encodeManifest(original))
        assertEquals(uuids, decoded.knownUuids)
    }

    @Test
    fun `RelayManifest round-trip - empty knownUuids is valid`() {
        val original = buildMinimalManifest(knownUuids = emptyList())
        val decoded = decodeManifest(RelayPayloadCodec.encodeManifest(original))
        assertEquals(0, decoded.knownUuids.size)
    }

    @Test
    fun `RelayManifest round-trip - timestamp preserved`() {
        val ts = "2026-08-13T14:30:00Z"
        val original = buildMinimalManifest(timestamp = ts)
        val decoded = decodeManifest(RelayPayloadCodec.encodeManifest(original))
        assertEquals(ts, decoded.timestamp)
    }

    @Test
    fun `RelayManifest round-trip - large UUID list preserved`() {
        val uuids = (1..50).map { "uuid-$it" }
        val original = buildMinimalManifest(knownUuids = uuids)
        val decoded = decodeManifest(RelayPayloadCodec.encodeManifest(original))
        assertEquals(50, decoded.knownUuids.size)
        assertEquals("uuid-25", decoded.knownUuids[24])
    }

    // ── SOSRequest round-trip ─────────────────────────────────────────────────

    @Test
    fun `SOSRequest round-trip - uuid preserved`() {
        val original = buildMinimalSos(uuid = "sos-uuid-abc-999")
        val decoded = decodeSos(RelayPayloadCodec.encodeSos(original))
        assertEquals("sos-uuid-abc-999", decoded.uuid)
    }

    @Test
    fun `SOSRequest round-trip - deviceId preserved`() {
        val original = buildMinimalSos()
        val decoded = decodeSos(RelayPayloadCodec.encodeSos(original))
        assertEquals("test-device-001", decoded.deviceId)
    }

    @Test
    fun `SOSRequest round-trip - location lat and lng preserved`() {
        val original = buildMinimalSos()
        val decoded = decodeSos(RelayPayloadCodec.encodeSos(original))
        assertEquals(28.6139f, decoded.location.lat, 0.0001f)
        assertEquals(77.2090f, decoded.location.lng, 0.0001f)
    }

    @Test
    fun `SOSRequest round-trip - relayHopCount preserved`() {
        val original = buildMinimalSos().copy(relayHopCount = 5)
        val decoded = decodeSos(RelayPayloadCodec.encodeSos(original))
        assertEquals(5, decoded.relayHopCount)
    }

    @Test
    fun `SOSRequest round-trip - status preserved`() {
        val original = buildMinimalSos().copy(status = SOSStatus.IN_RELAY)
        val decoded = decodeSos(RelayPayloadCodec.encodeSos(original))
        assertEquals(SOSStatus.IN_RELAY, decoded.status)
    }

    @Test
    fun `SOSRequest round-trip - null optional fields remain null`() {
        val original = buildMinimalSos()
        val decoded = decodeSos(RelayPayloadCodec.encodeSos(original))
        assertNull(decoded.peopleCount)
        assertNull(decoded.medicalSnapshot)
        assertNull(decoded.customMessage)
        assertNull(decoded.contactNumber)
    }

    // ── UUID diff logic ───────────────────────────────────────────────────────
    //
    // This tests the pure set-subtraction logic that determines which SOSRequests to send.
    // At runtime this is implemented by RelayDataSource.getMissingSos(peerKnownUuids).
    // These tests verify the expected protocol behavior: "send only what the peer is missing."

    @Test
    fun `uuid diff - peer with no UUIDs should receive all local UUIDs`() {
        val localUuids = listOf("a", "b", "c")
        val peerKnownUuids = emptyList<String>()
        val toSend = localUuids.filter { it !in peerKnownUuids }
        assertEquals(listOf("a", "b", "c"), toSend)
    }

    @Test
    fun `uuid diff - peer with all UUIDs receives nothing`() {
        val localUuids = listOf("a", "b", "c")
        val peerKnownUuids = listOf("a", "b", "c")
        val toSend = localUuids.filter { it !in peerKnownUuids }
        assertEquals(emptyList<String>(), toSend)
    }

    @Test
    fun `uuid diff - peer with partial UUIDs receives only the missing ones`() {
        val localUuids = listOf("a", "b", "c", "d")
        val peerKnownUuids = listOf("b", "d")
        val toSend = localUuids.filter { it !in peerKnownUuids }
        assertEquals(listOf("a", "c"), toSend)
    }

    @Test
    fun `uuid diff - both stores empty produces empty diff`() {
        val toSend = emptyList<String>().filter { it !in emptyList<String>() }
        assertEquals(emptyList<String>(), toSend)
    }

    @Test
    fun `uuid diff - peer with superset of local UUIDs receives nothing`() {
        // Peer has everything we have, plus more — we have nothing to send.
        val localUuids = listOf("a", "b")
        val peerKnownUuids = listOf("a", "b", "c", "d")
        val toSend = localUuids.filter { it !in peerKnownUuids }
        assertEquals(emptyList<String>(), toSend)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildMinimalManifest(
        deviceId: String = "test-device",
        knownUuids: List<String> = listOf("uuid-1", "uuid-2"),
        timestamp: String = "2026-08-13T00:00:00Z"
    ) = RelayManifest(deviceId = deviceId, knownUuids = knownUuids, timestamp = timestamp)

    private fun buildMinimalSos(uuid: String = "test-sos-uuid") = SOSRequest(
        uuid = uuid,
        deviceId = "test-device-001",
        createdAt = "2026-08-13T00:00:00Z",
        location = SosLocation(lat = 28.6139f, lng = 77.2090f),
        isQuickSos = true,
        emergencyType = EmergencyType.UNSPECIFIED,
        lastRelayedAt = "2026-08-13T00:00:00Z",
        status = SOSStatus.PENDING_LOCAL
    )

    /** Convenience: encode a RelayManifest and immediately decode it back. */
    private fun decodeManifest(encoded: ByteArray): RelayManifest =
        RelayPayloadCodec.decodeManifest(RelayPayloadCodec.body(encoded))

    /** Convenience: encode a SOSRequest and immediately decode it back. */
    private fun decodeSos(encoded: ByteArray): SOSRequest =
        RelayPayloadCodec.decodeSos(RelayPayloadCodec.body(encoded))
}
