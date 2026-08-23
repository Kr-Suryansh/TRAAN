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
import com.sih.relay.service.RelayTestConfigProvider
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
import java.util.concurrent.ConcurrentHashMap

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

        /**
         * TEST-ONLY (Day 7 Phase D): the endpoint name to advertise/use while the
         * test connection allow-list is active, otherwise the production constant.
         */
        fun effectiveEndpointName(): String =
            RelayTestConfigProvider.config?.localPeerName ?: LOCAL_ENDPOINT_NAME

        /**
         * TEST-ONLY (Day 7 Phase D): true when a peer with [peerName] is permitted
         * by the test allow-list. When no test config is set, all peers are allowed
         * (exact current production behavior).
         */
        fun isPeerAllowed(peerName: String): Boolean =
            RelayTestConfigProvider.config?.allowedPeerNames?.contains(peerName) ?: true

        /**
         * Extracts the Nearby Connections status code from a [com.google.android.gms.common.api.ApiException].
         * Returns null if the exception is not an ApiException (e.g. SecurityException).
         */
        fun extractStatusCode(e: Exception): Int? =
            (e as? com.google.android.gms.common.api.ApiException)?.statusCode
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

    /**
     * Endpoints for which a connection attempt is currently pending or already connected.
     *
     * Guards against duplicate/simultaneous [requestConnection] attempts to the same
     * endpoint (a source of STATUS_ENDPOINT_IO_ERROR / 8012 races when both phones
     * discover each other at the same time). Thread-safe across Nearby callbacks.
     */
    private val pendingConnections: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Endpoints that are currently connected (STATUS_OK) and reachable for payload
     * propagation. Used by [propagateLocalSos] / [propagateSosToConnected] to push
     * newly-created or newly-received SOSRequests to peers without waiting for a
     * reconnect/manifest exchange. Thread-safe across Nearby callbacks.
     */
    private val connectedEndpoints: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * True while advertising + discovery are currently active.
     *
     * Guard for [startScanWindow]/[stopScanWindow] so the Day 5 duty cycler (and
     * repeated startRelay() calls) cannot double-start or double-stop the radio.
     * Only the advertising/discovery window is toggled — established connections,
     * pending connection state, and the SOS store are deliberately left untouched
     * when a scan window closes (battery sleep).
     */
    @Volatile
    private var scanningWindow = false

    // ── Connection lifecycle callbacks ────────────────────────────────────────

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {

        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // TEST-ONLY (Day 7 Phase D): reject peers outside the test allow-list.
            // This protects against the peer initiating toward us (onEndpointFound
            // already guards our own requestConnection direction).
            if (!isPeerAllowed(info.endpointName)) {
                Log.i(TAG, "TEST-ONLY filter: rejecting connection from ${info.endpointName} (endpointId=$endpointId)")
                connectionsClient.rejectConnection(endpointId)
                return
            }

            // Mark this endpoint as handled so onEndpointFound won't re-request a
            // connection to it while this connection is being established/accepted.
            pendingConnections.add(endpointId)
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
                    // Connected: keep the endpoint in pendingConnections so discovery
                    // won't attempt a duplicate requestConnection while it is connected.
                    connectedEndpoints.add(endpointId)
                    Log.i(TAG, "Connection established with endpoint: $endpointId. Initiating manifest exchange...")
                    relayScope.launch {
                        initiateManifestExchange(endpointId)
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    pendingConnections.remove(endpointId)
                    Log.w(TAG, "Connection rejected by endpoint: $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    pendingConnections.remove(endpointId)
                    Log.e(TAG, "Connection error with endpoint: $endpointId (status: ${result.status.statusCode})")
                }
                else -> {
                    pendingConnections.remove(endpointId)
                    Log.w(TAG, "Connection result ${result.status.statusCode} for endpoint: $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            // Endpoint is no longer connected — allow a future discovery event to reconnect.
            pendingConnections.remove(endpointId)
            connectedEndpoints.remove(endpointId)
            Log.d(TAG, "Disconnected from endpoint: $endpointId")
        }
    }

    // ── Endpoint discovery callback ───────────────────────────────────────────

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {

        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // TEST-ONLY (Day 7 Phase D): ignore peers outside the test allow-list.
            // Checked before pendingConnections.add so a blocked peer is never
            // marked as "handled" and can be re-evaluated if the config changes.
            if (!isPeerAllowed(info.endpointName)) {
                Log.i(TAG, "TEST-ONLY filter: ignoring discovery from ${info.endpointName} (endpointId=$endpointId)")
                return
            }

            // Guard: only initiate one connection attempt per endpoint. If a connection
            // is already pending or established, ignore the duplicate discovery event
            // to avoid simultaneous/duplicate requestConnection() races (8012).
            if (!pendingConnections.add(endpointId)) {
                Log.d(TAG, "Ignoring duplicate discovery for endpoint: $endpointId (already connected or connection pending)")
                return
            }
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName}). Requesting connection...")
            connectionsClient.requestConnection(
                effectiveEndpointName(),
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.d(TAG, "Connection request sent to endpoint: $endpointId")
            }.addOnFailureListener { e ->
                // P2P_CLUSTER bidirectional race: when both phones discover each other
                // simultaneously, both call requestConnection and the SDK fails the
                // "losing" side with 8012 (STATUS_ENDPOINT_IO_ERROR). In that case the
                // remote side's request may still reach us via onConnectionInitiated, so
                // we must NOT remove from pendingConnections here — doing so would allow
                // onEndpointFound to fire a duplicate requestConnection that conflicts
                // with the in-progress acceptConnection.
                //
                // Only remove on non-8012 errors (e.g. 8007 radio error) or when the
                // endpoint is unreachable; on 8012 the endpoint stays in pendingConnections
                // and will be cleared by onConnectionInitiated (accept), onDisconnected,
                // or onEndpointLost.
                val statusCode = extractStatusCode(e)
                if (statusCode != ConnectionsStatusCodes.STATUS_ENDPOINT_IO_ERROR) {
                    pendingConnections.remove(endpointId)
                    Log.e(TAG, "Failed to request connection to endpoint: $endpointId (non-8012, removed from pending)", e)
                } else {
                    Log.w(TAG, "requestConnection to $endpointId failed with 8012 (bidirectional race) — " +
                            "keeping in pendingConnections; remote onConnectionInitiated may still arrive", e)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            // Allow a future discovery event to retry this endpoint.
            pendingConnections.remove(endpointId)
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
                deviceId = effectiveEndpointName(),
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
     * Deserializes the [SOSRequest], applies hop accounting via [RelayHopLogic]
     * (increments relayHopCount, updates lastRelayedAt, transitions PENDING_LOCAL → IN_RELAY),
     * and passes the updated copy to [RelayDataSource.saveSosMessages].
     *
     * With the current stub DataSource the save is a no-op; the received SOS is logged
     * so the physical test is interpretable from Logcat.
     */
    private fun handleIncomingSos(endpointId: String, bodyBytes: ByteArray) {
        relayScope.launch {
            try {
                val sos = RelayPayloadCodec.decodeSos(bodyBytes)
                val relayed = RelayHopLogic.onRelayReceive(sos, isoNow())
                Log.i(TAG, "SOSRequest received from endpoint $endpointId: " +
                        "uuid=${relayed.uuid}, deviceId=${relayed.deviceId}, " +
                        "hopCount=${relayed.relayHopCount}, status=${relayed.status}")

                // Echo-loop guard: only propagate a received SOS onward if it is NEW
                // to this phone's store. Once a UUID is stored, later copies (echoes
                // from other peers) are dropped via idempotent save and not re-broadcast.
                val knownUuids = dataSource.getAllSosUuids()
                val isNew = relayed.uuid !in knownUuids

                dataSource.saveSosMessages(listOf(relayed))

                // Day 7 diagnostic: log the forwarding decision explicitly so the
                // physical stress test can distinguish "new SOS propagated onward"
                // from "duplicate received, idempotent save, propagation skipped".
                if (isNew) {
                    Log.i(TAG, "SOSRequest ${relayed.uuid} forwarded to DataSource.saveSosMessages() " +
                            "(hopCount=${relayed.relayHopCount}) — NEW, propagating to connected peers")
                    propagateSosToConnected(relayed, fromEndpointId = endpointId)
                } else {
                    Log.d(TAG, "SOSRequest ${relayed.uuid} is a DUPLICATE (already in store) — " +
                            "idempotent save, propagation skipped")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling incoming SOSRequest from endpoint: $endpointId", e)
            }
        }
    }

    /**
     * Day 4 multi-hop propagation: sends [sos] to every currently-connected peer,
     * optionally excluding the endpoint it was just received from ([fromEndpointId]).
     *
     * Excluding the source endpoint prevents a hop from sending a received SOS
     * straight back to the device it came from; the new-UUID guard in
     * [handleIncomingSos] already prevents indefinite echo loops.
     *
     * Same wire format and send mechanism as [sendMissingSos].
     */
    private fun propagateSosToConnected(sos: SOSRequest, fromEndpointId: String? = null) {
        for (endpointId in connectedEndpoints) {
            if (endpointId == fromEndpointId) continue
            relayScope.launch {
                try {
                    val encoded = RelayPayloadCodec.encodeSos(sos)
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(encoded))
                        .addOnSuccessListener {
                            Log.i(TAG, "SOSRequest ${sos.uuid} (hopCount=${sos.relayHopCount}) sent to endpoint: $endpointId (${encoded.size} bytes)")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to send SOSRequest ${sos.uuid} to endpoint: $endpointId", e)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Error encoding/sending SOSRequest ${sos.uuid} to endpoint: $endpointId", e)
                }
            }
        }
    }

    /**
     * Day 4: pushes a freshly-created LOCAL SOS to every currently-connected peer so it
     * propagates immediately (A→B→C) without requiring a reconnect/manifest exchange.
     *
     * Called by :app AFTER the SOS has been saved locally. The SOS is sent as-is
     * (relayHopCount = 0, status = PENDING_LOCAL); each receiving peer's
     * [handleIncomingSos] applies [RelayHopLogic] so the hop count increments there.
     */
    suspend fun propagateLocalSos(sos: SOSRequest) {
        if (connectedEndpoints.isEmpty()) {
            Log.i(TAG, "propagateLocalSos: no connected endpoints — SOS ${sos.uuid} stays local only")
            return
        }
        Log.i(TAG, "propagateLocalSos: broadcasting SOS ${sos.uuid} (hopCount=${sos.relayHopCount}, status=${sos.status}) " +
                "to ${connectedEndpoints.size} connected endpoint(s)")
        propagateSosToConnected(sos)
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
        Log.i(TAG, "startRelay() invoked. Starting relay (advertising + discovery)...")
        startScanWindow()
    }

    /**
     * Internal (Day 5): opens the scan/advertise window.
     *
     * Starts Nearby advertising + discovery (P2P_CLUSTER). Idempotent — guarded by
     * [scanningWindow] so the duty cycler and startRelay() cannot double-start.
     *
     * Established connections and the SOS store are NOT touched; closing a window
     * via [stopScanWindow] only silences the radio for battery.
     */
    internal fun startScanWindow() {
        if (scanningWindow) {
            Log.d(TAG, "startScanWindow() ignored — window already open")
            return
        }
        scanningWindow = true
        Log.i(TAG, "Opening scan window. Starting Nearby Connections advertising & discovery (P2P_CLUSTER)...")

        // Day 6 hardening: log clearly when runtime permissions are not yet
        // granted (the Nearby SDK throws SecurityException synchronously in that
        // case) and catch any synchronous startup throw so the duty cycler keeps
        // running and retries on the next window instead of crashing the service.
        val missingPermissions = RelayPermissionRequirements.missingPermissions(context)
        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "startScanWindow: missing runtime permissions that may prevent " +
                    "advertising/discovery from starting: $missingPermissions")
        }

        try {
            connectionsClient.startAdvertising(
                effectiveEndpointName(),
                SERVICE_ID,
                connectionLifecycleCallback,
                AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
            ).addOnSuccessListener {
                Log.i(TAG, "Advertising started successfully (serviceId=$SERVICE_ID)")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to start advertising", e)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "startAdvertising threw SecurityException (Bluetooth/Wi-Fi permission not " +
                    "granted?). Scan window aborted; duty cycle will retry on the next window.", e)
            scanningWindow = false
            return
        } catch (e: Exception) {
            Log.e(TAG, "startAdvertising failed to start", e)
            scanningWindow = false
            return
        }

        try {
            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
            ).addOnSuccessListener {
                Log.i(TAG, "Discovery started successfully (serviceId=$SERVICE_ID)")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to start discovery", e)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "startDiscovery threw SecurityException (Bluetooth/Wi-Fi permission not " +
                    "granted?). Stopping advertising to avoid a half-open scan window.", e)
            try {
                connectionsClient.stopAdvertising()
            } catch (ignore: Exception) {
                // best-effort cleanup only
            }
            scanningWindow = false
        } catch (e: Exception) {
            Log.e(TAG, "startDiscovery failed to start", e)
            try {
                connectionsClient.stopAdvertising()
            } catch (ignore: Exception) {
                // best-effort cleanup only
            }
            scanningWindow = false
        }
    }

    /**
     * Internal (Day 5): closes the scan/advertise window.
     *
     * Stops Nearby advertising + discovery so the radio is idle during the duty
     * cycle's sleep period. Idempotent — guarded by [scanningWindow].
     *
     * Deliberately does NOT stop endpoints: already-connected peers stay connected
     * across the sleep so SOS propagation over live links is uninterrupted, and
     * the mesh can discover new peers again when the next window opens.
     */
    internal fun stopScanWindow() {
        if (!scanningWindow) {
            Log.d(TAG, "stopScanWindow() ignored — window already closed")
            return
        }
        scanningWindow = false
        Log.i(TAG, "Closing scan window. Stopping Nearby advertising & discovery (connections kept)...")
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising/discovery", e)
        }
    }

    override fun stopRelay() {
        Log.i(TAG, "stopRelay() invoked. Cancelling relay coroutine scope...")
        relayScope.cancel()
        // Recreate immediately so startRelay() can be called again without constructing a new RelayManager.
        relayScope = newRelayScope()

        // Clear connection-state tracking so a subsequent startRelay() starts fresh.
        pendingConnections.clear()
        connectedEndpoints.clear()
        scanningWindow = false

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
