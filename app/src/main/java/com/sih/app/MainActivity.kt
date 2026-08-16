package com.sih.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.room.Room
import com.sih.data.db.AppDatabase
import com.sih.data.prefs.DevicePreferences
import com.sih.data.relay.RoomRelayDataSource
import com.sih.relay.RelayManager
import com.sih.relay.RelayPermissionRequirements
import com.sih.relay.model.EmergencyType
import com.sih.relay.model.SOSRequest
import com.sih.relay.model.SOSStatus
import com.sih.relay.model.SosLocation
import com.sih.relay.service.RelayDataSourceProvider
import com.sih.relay.service.RelayForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Temporary Day 2+ Activity scaffolding for testing Nearby Connections.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * TEMPORARY DAY 4/5/6 PHYSICAL-TEST SCAFFOLDING — NOT PRODUCTION CODE.
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Day 6 change (Room-backed persistence — Component C reuse):
 *   - The temporary [InMemoryRelayStore] is GONE. MainActivity now builds the
 *     Room AppDatabase (:data) and a RoomRelayDataSource (:data) that implements
 *     RelayDataSource, and sets it on RelayDataSourceProvider so the service's
 *     RelayManager reads/writes real persisted SOS records.
 *   - "Inject Test SOS" now uses Component C's stable device identity
 *     (DevicePreferences.getDeviceId() fallback to getOrCreateInstallationId())
 *     instead of the Day 4 literal "DAY4-TEST-DEVICE".
 *
 * Day 4/5 behavior preserved:
 *   - Start Relay / Stop Relay buttons drive RelayForegroundService (owns
 *     RelayManager + DutyCycler). The radio duty-cycles ~10s active / ~40s sleep.
 *   - Inject Test SOS creates an origin SOS (relayHopCount = 0, PENDING_LOCAL)
 *     and propagates it to connected peers for the A→B→C hop progression.
 *
 * Day 6 permission/startup UX:
 *   - "Start Relay" now runs a preflight first: it requests the missing A+B
 *     runtime permissions via [RelayPermissionRequirements] and only starts
 *     RelayForegroundService once they are granted (and Bluetooth is on).
 *   - Bluetooth enable is launched through the system
 *     [android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE] flow when the
 *     radio is off. Wi-Fi is a diagnostics-only prerequisite (Nearby escalates
 *     to Wi-Fi Direct automatically when needed); its runtime permission is part
 *     of the A+B permission set but the radio state is not force-enabled.
 *
 * No production architecture was changed. RelayApi and RelayDataSource are untouched.
 * Component C will replace this Activity with Jetpack Compose UI.
 * ═══════════════════════════════════════════════════════════════════════════════
 */
class MainActivity : Activity() {

    private companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private const val REQUEST_CODE_ENABLE_BLUETOOTH = 1002
    }

    private lateinit var relayManager: RelayManager

    /** Day 6: Room-backed relay store — shared with the service via the provider. */
    private lateinit var relayDataSource: RoomRelayDataSource

    /** Day 6: Component C's encrypted device identity (stable installation id). */
    private lateinit var devicePreferences: DevicePreferences

    /** Day 6 permission UX: set while a Start request is waiting on a permission/BT result. */
    private var pendingStartRequest = false

    /** Day 6 permission UX: status line showing the last preflight outcome on-screen. */
    private lateinit var statusText: TextView

    private var serviceBound = false
    private var bindRequested = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? RelayForegroundService.LocalBinder
            if (localBinder == null) {
                Log.e(TAG, "onServiceConnected: unexpected binder type")
                return
            }
            relayManager = localBinder.getRelayManager()
            serviceBound = true
            Log.i(TAG, "Bound to RelayForegroundService. RelayManager available.")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            Log.w(TAG, "Disconnected from RelayForegroundService")
        }
    }

    /** Scope for the temporary Day 4 inject button. Cancelled in onDestroy. */
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity onCreate starting...")

        // Day 6: build the Room-backed RelayDataSource and share it with the
        // service via the provider seam. RelayManager (inside the service) now
        // persists every SOS in Room via Component C's DAO.
        devicePreferences = DevicePreferences(applicationContext)
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
        relayDataSource = RoomRelayDataSource(db.sosRequestDao())
        RelayDataSourceProvider.dataSource = relayDataSource
        Log.i(TAG, "Room-backed RelayDataSource wired: ${AppDatabase.DATABASE_NAME}")

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
            text = "Day 6 Relay Test Scaffold (Room-backed)"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }

        statusText = TextView(this).apply {
            text = "Idle"
            setTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 0, 0, 16)
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
                // Day 6: preflight before starting the service — request any
                // missing runtime permissions, ensure Bluetooth is on, then start.
                startRelayWithPreflight()
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
                RelayForegroundService.stop(this@MainActivity)
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
                    relayDataSource.saveSosMessages(listOf(injected))
                    Log.i(TAG, "Room now holds ${relayDataSource.getAllSosUuids().size} SOS UUID(s)")
                    if (::relayManager.isInitialized) {
                        relayManager.propagateLocalSos(injected)
                    } else {
                        Log.w(TAG, "Inject skipped: RelayManager not yet bound")
                    }
                }
            }
        }

        layout.addView(tvTitle)
        layout.addView(statusText)
        layout.addView(btnStart)
        layout.addView(btnStop)
        layout.addView(btnInject)

        setContentView(layout)
        Log.i(TAG, "MainActivity onCreate completed successfully")
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "Binding to RelayForegroundService...")
        val serviceIntent = Intent(this, RelayForegroundService::class.java)
        bindRequested = true
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        testScope.cancel()
        if (bindRequested) {
            unbindService(serviceConnection)
            serviceBound = false
            bindRequested = false
            Log.i(TAG, "Unbound from RelayForegroundService")
        }
    }

    // ── Day 6 permission/startup preflight ───────────────────────────────────

    /**
     * Day 6: "Enable Emergency Mode" preflight for the test scaffold.
     *
     * Flow: request any missing A+B runtime permissions → once granted, ensure
     * Bluetooth is on (launch the system enable dialog if off) → only then start
     * [RelayForegroundService]. A failed step logs, updates [statusText], and
     * aborts — the service is never started without its prerequisites.
     *
     * The helper [RelayPermissionRequirements] owns the version-aware permission
     * matrix; this Activity only decides how to surface the requests/results.
     */
    private fun startRelayWithPreflight() {
        val missing = RelayPermissionRequirements.missingPermissions(this)
        if (missing.isNotEmpty()) {
            pendingStartRequest = true
            setStatus("Requesting permissions: ${missing.joinToString()}")
            Log.i(TAG, "Preflight: requesting missing permissions $missing")
            requestPermissions(missing.toTypedArray(), REQUEST_CODE_PERMISSIONS)
            return
        }
        ensureBluetoothAndStart()
    }

    /** Once permissions are granted, enable Bluetooth if needed, then start. */
    private fun ensureBluetoothAndStart() {
        if (!RelayPermissionRequirements.isBluetoothEnabled(this)) {
            val enableIntent = RelayPermissionRequirements.bluetoothEnableIntent()
            if (enableIntent != null) {
                pendingStartRequest = true
                setStatus("Requesting Bluetooth enable")
                Log.i(TAG, "Preflight: Bluetooth off — launching system enable dialog")
                startActivityForResult(enableIntent, REQUEST_CODE_ENABLE_BLUETOOTH)
                return
            }
            Log.w(TAG, "Preflight: no Bluetooth adapter — continuing anyway (Wi-Fi Direct only)")
        }
        startRelayService()
    }

    /** All prerequisites satisfied — start the foreground service + duty cycle. */
    private fun startRelayService() {
        pendingStartRequest = false
        val wifi = if (RelayPermissionRequirements.isWifiOn(this)) "on" else "off (auto-escalation)"
        setStatus("Prerequisites OK — starting relay (Wi-Fi: $wifi)")
        Log.i(TAG, "Preflight complete — starting RelayForegroundService")
        // The service owns RelayManager + DutyCycler: starting it begins
        // the relay AND the ~10s/~40s duty cycle in one action.
        RelayForegroundService.start(this@MainActivity)
    }

    private fun setStatus(message: String) {
        if (::statusText.isInitialized) {
            statusText.text = message
        }
        Log.i(TAG, "Status: $message")
    }

    @Suppress("DEPRECATION") // startActivityForResult/onActivityResult — framework mechanism, minSdk 23.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE_ENABLE_BLUETOOTH) return
        if (!pendingStartRequest) return
        if (RelayPermissionRequirements.isBluetoothEnabled(this)) {
            Log.i(TAG, "Preflight: Bluetooth enabled by user — starting relay")
            startRelayService()
        } else {
            pendingStartRequest = false
            setStatus("Bluetooth still off — relay not started")
            Log.w(TAG, "Preflight: Bluetooth not enabled — relay not started")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_PERMISSIONS) return
        if (!pendingStartRequest) return

        val stillMissing = RelayPermissionRequirements.missingPermissions(this)
        if (stillMissing.isNotEmpty()) {
            pendingStartRequest = false
            setStatus("Permissions denied: ${stillMissing.joinToString()} — relay not started")
            Log.w(TAG, "Preflight: permissions still missing $stillMissing — relay not started")
            return
        }
        Log.i(TAG, "Preflight: all required permissions granted")
        ensureBluetoothAndStart()
    }

    /**
     * TEMPORARY Day 4: constructs a fresh origin SOSRequest for the multi-hop test.
     * Day 6: deviceId now uses Component C's stable identity (backend-assigned
     * device id when registered, otherwise the persistent installation id).
     */
    private fun buildTestSos(): SOSRequest = SOSRequest(
        uuid = UUID.randomUUID().toString(),
        deviceId = devicePreferences.getDeviceId()
            ?: devicePreferences.getOrCreateInstallationId(),
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
}