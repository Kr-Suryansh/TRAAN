package com.sih.relay

import com.sih.relay.model.RelayManifest
import com.sih.relay.model.SOSRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Internal wire-format codec for the Day 3 manifest-exchange relay protocol.
 *
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  INTERNAL IMPLEMENTATION DETAIL — NOT part of the public project contract.  │
 * │  This byte-prefix scheme is chosen for simplicity inside :relay only.       │
 * │  It is NOT described in ApiEndpoints.md or day1-contracts-and-repo-setup.md │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * Wire format for every Nearby Connections BYTES payload:
 *   Byte 0    : type tag (see TYPE_* constants below)
 *   Bytes 1…N : UTF-8 JSON body
 *
 * Type tags:
 *   0x01 = [RelayManifest] JSON  — the "here's what I have" handshake
 *   0x02 = [SOSRequest]    JSON  — a single SOS message being relayed
 *
 * Why a 1-byte prefix instead of a JSON wrapper envelope?
 * - Zero schema change — no new model class required.
 * - Zero extra JSON parsing to determine type.
 * - Overhead is exactly 1 byte per payload.
 * - Straightforward to extend with new type tags in future days.
 *
 * Extracted from [RelayManager] so it can be unit-tested independently on the JVM
 * without any Android or Nearby Connections runtime dependencies.
 */
internal object RelayPayloadCodec {

    /** Type tag for a [RelayManifest] payload. */
    const val TYPE_MANIFEST: Byte = 0x01

    /** Type tag for a [SOSRequest] payload. */
    const val TYPE_SOS: Byte = 0x02

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ── Encoding ──────────────────────────────────────────────────────────────

    /**
     * Encodes a [RelayManifest] to a byte array with a [TYPE_MANIFEST] prefix byte.
     * The result is ready to pass to [com.google.android.gms.nearby.connection.Payload.fromBytes].
     */
    fun encodeManifest(manifest: RelayManifest): ByteArray {
        val body = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        return prependType(TYPE_MANIFEST, body)
    }

    /**
     * Encodes a [SOSRequest] to a byte array with a [TYPE_SOS] prefix byte.
     */
    fun encodeSos(sos: SOSRequest): ByteArray {
        val body = json.encodeToString(sos).toByteArray(Charsets.UTF_8)
        return prependType(TYPE_SOS, body)
    }

    // ── Decoding ──────────────────────────────────────────────────────────────

    /**
     * Reads the type tag from byte 0 of a received payload.
     * Returns `null` if the byte array is empty.
     */
    fun peekType(bytes: ByteArray): Byte? = if (bytes.isEmpty()) null else bytes[0]

    /**
     * Returns the JSON body portion of a received payload (everything after byte 0).
     * Returns an empty array if the input has only the type tag byte or is empty.
     */
    fun body(bytes: ByteArray): ByteArray =
        if (bytes.size <= 1) ByteArray(0) else bytes.copyOfRange(1, bytes.size)

    /**
     * Deserializes a [RelayManifest] from the raw JSON body bytes (after stripping the type tag).
     * Call [body] first to extract the body from the full payload.
     */
    fun decodeManifest(bodyBytes: ByteArray): RelayManifest =
        json.decodeFromString(String(bodyBytes, Charsets.UTF_8))

    /**
     * Deserializes a [SOSRequest] from the raw JSON body bytes (after stripping the type tag).
     * Call [body] first to extract the body from the full payload.
     */
    fun decodeSos(bodyBytes: ByteArray): SOSRequest =
        json.decodeFromString(String(bodyBytes, Charsets.UTF_8))

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun prependType(type: Byte, body: ByteArray): ByteArray {
        val result = ByteArray(1 + body.size)
        result[0] = type
        body.copyInto(result, destinationOffset = 1)
        return result
    }
}
