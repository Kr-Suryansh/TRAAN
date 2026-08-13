package com.sih.relay

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.sih.relay.api.RelayApi
import com.sih.relay.api.RelayDataSource
import com.sih.relay.model.RelayManifest
import com.sih.relay.model.SOSRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The implementation of [RelayApi] — the single public entry point for :app into the relay module.
 *
 * Day 3 Implementation — Manifest Exchange & UUID Diffing:
 *   Connection is established (P2P_CLUSTER, bidirectional)
 *     → BOTH devices immediately send their [RelayManifest] (list of SOS UUIDs they hold)
 *     → BOTH devices receive the peer's [RelayManifest]
 *     → EACH device computes the diff via [RelayDataSource.getMissingSos]
 *     → EACH device sends only the [SOSRequest]s the peer does not yet have
 *     → Received [SOSRequest]s are passed to [RelayDataSource.saveSosMessages]
 *
 * Wire format is handled entirely by [RelayPayloadCodec] (1-byte type prefix + UTF-8 JSON body).
 * This is an internal :relay implementation detail, not part of the public project contract.
 *
 * Day 2 temporary test path (sendDay2TestPayload) has been removed and replaced by
 * the manifest exchange handshake. The connection lifecycle callbacks themselves
 * (advertising, discovery, accept, disconnect) are unchanged from Day 2.
 *
 * @param context     Application or Activity context required by the Nearby Connections SDK.
 * @param dataSource  Injected by :app at startup. Returns empty collections in the current stub.
 *                    Will be backed by a Room DAO in :data (Component C) in a later day.
 */
class RelayManager(
    private val context: Context,
    private val dataSource: RelayDataSource
) : RelayApi {

    companion object {
        private const val TAG = "RelayManager_Day3"
        private const val SERVICE_ID = "com.sih.relay.SERVICE"
        private val STRATEGY = Strategy.P2P_CLUSTER

        /** Name broadcast to peers via Nearby Connections advertising. */
        private const val LOCAL_ENDPOINT_NAME = "SIH-Relay-Node"

        /** ISO 8601 UTC date-format string compatible with minSdk 23 (no java.time required). */
        private const val ISO_8601_UTC = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    /**
     * Coroutine scope for all suspend relay work — manifest building, DataSource reads/writes.
     *
     * Uses [SupervisorJob] so a failure in one child (e.g. a bad payload from one peer)
     * does not cancel work in progress for other peers.
     *
     * Cancelled in [stopRelay] and recreated immediately so the relay can be restarted.
     */
    private var relayScope = newRelayScope()

    private fun newRelayScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Connection lifecycle callbacks ────────────────────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {

        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with $endpointId (${info.endpointName}). Auto-accepting...")
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.d(TAG, "Connection accepted for endpoint: $endpointId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to accept connection for endpoint: $endpointId", e)
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    // Day 3: start the manifest exchange handshake immediately on both sides.
                    Log.i(TAG, "Connection established with endpoint: $endpointId. Initiating manifest exchange...")
                    relayScope.launch {
                        initiateManifestExchange(endpointId)
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected by endpoint: $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error with endpoint: $endpointId (status: ${result.status.statusCode})")
                }
                else -> {
                    Log.w(TAG, "Connection result ${result.status.statusCode} for endpoint: $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from endpoint: $endpointId")
        }
    }

    // ── Endpoint discovery callback ───────────────────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {

        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName}). Requesting connection...")
            connectionsClient.requestConnection(
                LOCAL_ENDPOINT_NAME,
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.d(TAG, "Connection request sent to endpoint: $endpointId")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to request connection to endpoint: $endpointId", e)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    // ── Payload callback ──────────────────────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {

        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) {
                Log.w(TAG, "Ignoring unsupported payload type ${payload.type} from $endpointId")
                return
            }
            val raw = payload.asBytes()
            if (raw == null || raw.isEmpty()) {
                Log.w(TAG, "Received empty or null payload from endpoint: $endpointId")
                return
            }

            when (val typeTag = RelayPayloadCodec.peekType(raw)) {
                RelayPayloadCodec.TYPE_MANIFEST -> {
                    Log.d(TAG, "Manifest payload received from endpoint: $endpointId (${raw.size} bytes)")
                    handleIncomingManifest(endpointId, RelayPayloadCodec.body(raw))
                }
                RelayPayloadCodec.TYPE_SOS -> {
                    Log.d(TAG, "SOS payload received from endpoint: $endpointId (${raw.size} bytes)")
                    handleIncomingSos(endpointId, RelayPayloadCodec.body(raw))
                }
                else -> {
                    Log.w(TAG, "Unknown payload type tag 0x${typeTag?.toString(16)} from $endpointId. Ignoring.")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS ->
                    Log.d(TAG, "Payload transfer to/from $endpointId: completed successfully")
                PayloadTransferUpdate.Status.FAILURE ->
                    Log.e(TAG, "Payload transfer to/from $endpointId: FAILED")
                PayloadTransferUpdate.Status.IN_PROGRESS ->
                    Log.d(TAG, "Payload transfer to/from $endpointId: in progress " +
                            "(${update.bytesTransferred}/${update.totalBytes} bytes)")
            }
        }
    }

    // ── Day 3 Protocol: Manifest Exchange ─────────────────────────────────────

    /**
     * Step 1 of the Day 3 relay protocol. Called on BOTH devices immediately when
     * [ConnectionsStatusCodes.STATUS_OK] fires for a new connection.
     *
     * Reads the local SOS UUID list from [RelayDataSource.getAllSosUuids], constructs a
     * [RelayManifest], and sends it to [endpointId].
     *
     * With the current stub [RelayDataSource], [knownUuids] will be empty.
     * The protocol is correct regardless — an empty manifest is valid and means
     * "I have no SOS messages; please send me everything you have."
     */
    private suspend fun initiateManifestExchange(endpointId: String) {
        try {
            val knownUuids = dataSource.getAllSosUuids()
            val manifest = RelayManifest(
                deviceId = LOCAL_ENDPOINT_NAME,
                knownUuids = knownUuids,
                timestamp = isoNow()
            )
            val encoded = RelayPayloadCodec.encodeManifest(manifest)
            val payload = Payload.fromBytes(encoded)

            Log.i(TAG, "Sending RelayManifest to endpoint $endpointId " +
                    "(${knownUuids.size} known UUID${if (knownUuids.size == 1) "" else "s"}, " +
                    "${encoded.size} bytes total)")

            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.d(TAG, "RelayManifest sent successfully to endpoint: $endpointId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send RelayManifest to endpoint: $endpointId", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error building or sending RelayManifest to endpoint: $endpointId", e)
        }
    }

    /**
     * Called when a [RelayPayloadCodec.TYPE_MANIFEST] payload is received from a peer.
     *
     * Deserializes the peer's [RelayManifest], then launches [sendMissingSos] to compute
     * the UUID diff and send back whatever the peer is missing.
     */
    private fun handleIncomingManifest(endpointId: String, bodyBytes: ByteArray) {
        relayScope.launch {
            try {
                val peerManifest = RelayPayloadCodec.decodeManifest(bodyBytes)
                Log.i(TAG, "RelayManifest received from endpoint $endpointId: " +
                        "deviceId=${peerManifest.deviceId}, " +
                        "${peerManifest.knownUuids.size} known UUID${if (peerManifest.knownUuids.size == 1) "" else "s"}")
                sendMissingSos(endpointId, peerManifest.knownUuids)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming RelayManifest from endpoint: $endpointId", e)
            }
        }
    }

    /**
     * Called when a [RelayPayloadCodec.TYPE_SOS] payload is received from a peer.
     *
     * Deserializes the [SOSRequest] and passes it to [RelayDataSource.saveSosMessages].
     * With the current stub DataSource the save is a no-op; the received SOS is logged
     * so the physical test is interpretable from Logcat.
     */
    private fun handleIncomingSos(endpointId: String, bodyBytes: ByteArray) {
        relayScope.launch {
            try {
                val sos = RelayPayloadCodec.decodeSos(bodyBytes)
                Log.i(TAG, "SOSRequest received from endpoint $endpointId: " +
                        "uuid=${sos.uuid}, deviceId=${sos.deviceId}, hopCount=${sos.relayHopCount}")
                dataSource.saveSosMessages(listOf(sos))
                Log.d(TAG, "SOSRequest ${sos.uuid} forwarded to DataSource.saveSosMessages()")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming SOSRequest from endpoint: $endpointId", e)
            }
        }
    }

    /**
     * Step 2 of the Day 3 relay protocol. Called after receiving a peer's [RelayManifest].
     *
     * Calls [RelayDataSource.getMissingSos] with the peer's known UUID list to obtain the
     * set of [SOSRequest]s the peer does not yet hold. Sends each missing SOS as a
     * separate byte payload using [RelayPayloadCodec.TYPE_SOS].
     *
     * One payload per SOS — simpler than batching at this stage.
     * Batching can be introduced in a later day if needed.
     *
     * With the stub DataSource, [getMissingSos] always returns an empty list.
     * Zero payloads are sent; the protocol is still correct.
     */
    private suspend fun sendMissingSos(endpointId: String, peerKnownUuids: List<String>) {
        try {
            val missingSos = dataSource.getMissingSos(peerKnownUuids)
            Log.i(TAG, "UUID diff for endpoint $endpointId: " +
                    "peer has ${peerKnownUuids.size} UUID(s), " +
                    "${missingSos.size} SOSRequest(s) to send")

            if (missingSos.isEmpty()) {
                Log.i(TAG, "No missing SOSRequests to send to endpoint $endpointId — peer is up to date")
                return
            }

            for (sos in missingSos) {
                try {
                    val encoded = RelayPayloadCodec.encodeSos(sos)
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(encoded))
                        .addOnSuccessListener {
                            Log.i(TAG, "SOSRequest ${sos.uuid} sent to endpoint: $endpointId (${encoded.size} bytes)")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to send SOSRequest ${sos.uuid} to endpoint: $endpointId", e)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error encoding/sending SOSRequest ${sos.uuid} to endpoint: $endpointId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing or sending missing SOSRequests to endpoint: $endpointId", e)
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Returns the current UTC time as an ISO 8601 string.
     * Uses [SimpleDateFormat] instead of java.time.Instant to remain compatible with minSdk 23.
     */
    private fun isoNow(): String {
        val sdf = SimpleDateFormat(ISO_8601_UTC, Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    // ── Public RelayApi Implementation ────────────────────────────────────────

    override fun startRelay() {
        Log.i(TAG, "startRelay() invoked. Starting Nearby Connections advertising & discovery (P2P_CLUSTER)...")

        connectionsClient.startAdvertising(
            LOCAL_ENDPOINT_NAME,
            SERVICE_ID,
            connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            Log.i(TAG, "Advertising started successfully (serviceId=$SERVICE_ID)")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start advertising", e)
        }

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            Log.i(TAG, "Discovery started successfully (serviceId=$SERVICE_ID)")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start discovery", e)
        }
    }

    override fun stopRelay() {
        Log.i(TAG, "stopRelay() invoked. Cancelling relay coroutine scope...")
        relayScope.cancel()
        // Recreate immediately so startRelay() can be called again without constructing a new RelayManager.
        relayScope = newRelayScope()

        Log.i(TAG, "Stopping Nearby Connections advertising, discovery, and endpoints...")
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
            Log.i(TAG, "Nearby Connections stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Nearby Connections", e)
        }
    }

    override fun getRelayStore(): Flow<List<SOSRequest>> {
        return dataSource.observeAllSos()
    }
}
