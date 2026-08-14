package com.sih.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.sih.relay.RelayManager
import com.sih.relay.api.RelayDataSource
import com.sih.relay.model.EmergencyType
import com.sih.relay.model.SOSRequest
import com.sih.relay.model.SOSStatus
import com.sih.relay.model.SosLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Temporary Day 2+ Activity scaffolding for testing Nearby Connections.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * TEMPORARY DAY 4 PHYSICAL-TEST SCAFFOLDING — NOT PRODUCTION CODE.
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Day 4 additions (3-phone A→B→C relay test), all clearly temporary:
 *   - [InMemoryRelayStore]: a thread-safe in-memory [RelayDataSource] that actually
 *     retains received SOSRequests so a middle phone (B) can relay them onward.
 *     This is a test stand-in for the Room-backed store Component C delivers in Day 5+.
 *     It does NOT touch :data, Room, or :network — it lives entirely here in :app.
 *   - "Inject Test SOS" button: simulates Phone A creating a brand-new SOS
 *     (relayHopCount = 0, status = PENDING_LOCAL) so the A→B→C hop progression
 *     (0 → 1 → 2) can be physically verified.
 *
 * No production architecture was changed. RelayApi and RelayDataSource are untouched.
 * Existing Day 1–3 behavior (Start Relay / Stop Relay) is preserved verbatim.
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Component C will replace this Activity with Jetpack Compose UI.
 */
class MainActivity : Activity() {

    private companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var relayManager: RelayManager

    /** Temporary Day 4 in-memory store — test scaffolding only. */
    private lateinit var relayStore: InMemoryRelayStore

    /** Scope for the temporary Day 4 inject button. Cancelled in onDestroy. */
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity onCreate starting...")

        // Temporary Day 4 test store: retains received SOSRequests in memory so
        // multi-hop (A→B→C) relay works without :data / Room.
        relayStore = InMemoryRelayStore()
        relayManager = RelayManager(this, relayStore)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val tvTitle = TextView(this).apply {
            text = "Day 4 Relay Test Scaffold"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }

        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 16, 0, 16)
        }

        val btnStart = Button(this).apply {
            text = "Start Relay"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0D47A1"))
            textSize = 16f
            layoutParams = buttonParams
            setOnClickListener {
                Log.i(TAG, "Start Relay button clicked")
                relayManager.startRelay()
            }
        }

        val btnStop = Button(this).apply {
            text = "Stop Relay"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#B71C1C"))
            textSize = 16f
            layoutParams = buttonParams
            setOnClickListener {
                Log.i(TAG, "Stop Relay button clicked")
                relayManager.stopRelay()
            }
        }

        // TEMPORARY Day 4 test control: creates a new origin SOS on this phone
        // (relayHopCount = 0, status = PENDING_LOCAL) so the A→B→C hop progression
        // can be physically verified. Removed once Component C provides real SOS creation.
        val btnInject = Button(this).apply {
            text = "Inject Test SOS"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2E7D32"))
            textSize = 16f
            layoutParams = buttonParams
            setOnClickListener {
                val injected = buildTestSos()
                Log.i(TAG, "Inject Test SOS: uuid=${injected.uuid}, hopCount=${injected.relayHopCount}, status=${injected.status}")
                testScope.launch {
                    relayStore.saveSosMessages(listOf(injected))
                    Log.i(TAG, "Store now holds ${relayStore.getAllSosUuids().size} SOS UUID(s)")
                    relayManager.propagateLocalSos(injected)
                }
            }
        }

        layout.addView(tvTitle)
        layout.addView(btnStart)
        layout.addView(btnStop)
        layout.addView(btnInject)

        setContentView(layout)
        Log.i(TAG, "MainActivity onCreate completed successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        testScope.cancel()
        if (::relayManager.isInitialized) {
            relayManager.stopRelay()
        }
    }

    /** TEMPORARY Day 4: constructs a fresh origin SOSRequest for the multi-hop test. */
    private fun buildTestSos(): SOSRequest = SOSRequest(
        uuid = UUID.randomUUID().toString(),
        deviceId = "DAY4-TEST-DEVICE",
        createdAt = isoNow(),
        location = SosLocation(lat = 28.6139f, lng = 77.2090f),
        isQuickSos = true,
        emergencyType = EmergencyType.UNSPECIFIED,
        relayHopCount = 0,
        lastRelayedAt = isoNow(),
        status = SOSStatus.PENDING_LOCAL
    )

    private fun isoNow(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    /**
     * TEMPORARY Day 4 test scaffolding — a thread-safe in-memory [RelayDataSource].
     *
     * Stands in for the Room-backed store that Component C (:data) will implement in
     * Day 5+. It actually retains received SOSRequests so a middle phone (B) can
     * relay them onward to C, which is what makes the A→B→C hop test possible.
     *
     * Contract compliance (RelayDataSource):
     *   - getAllSosUuids(): current UUID set.
     *   - getMissingSos(knownUuids): SOSRequests whose UUID is not in the peer's list.
     *   - saveSosMessages(): idempotent by UUID (never overwrites an existing record).
     *   - observeAllSos(): live Flow over the current store contents.
     */
    private class InMemoryRelayStore : RelayDataSource {

        private val store = ConcurrentHashMap<String, SOSRequest>()
        private val _storeFlow = MutableStateFlow<List<SOSRequest>>(emptyList())

        override suspend fun getAllSosUuids(): List<String> = store.keys.toList()

        override suspend fun getMissingSos(knownUuids: List<String>): List<SOSRequest> =
            store.values.filter { it.uuid !in knownUuids }

        override suspend fun saveSosMessages(messages: List<SOSRequest>) {
            messages.forEach { msg ->
                store.putIfAbsent(msg.uuid, msg)
            }
            _storeFlow.value = store.values.sortedBy { it.createdAt }
        }

        override fun observeAllSos(): Flow<List<SOSRequest>> = _storeFlow
    }
}