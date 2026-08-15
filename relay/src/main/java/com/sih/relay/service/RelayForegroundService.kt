package com.sih.relay.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.sih.relay.DutyCycler
import com.sih.relay.RelayManager
import com.sih.relay.api.RelayDataSource
import com.sih.relay.model.SOSRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Day 5 — the foreground service that keeps the relay mesh alive in the background
 * and drives the battery-saving duty cycle.
 *
 * Ownership:
 *   - [RelayManager]: the Nearby Connections relay (advertising, discovery, manifest
 *     exchange, SOS propagation).
 *   - [DutyCycler]: toggles [RelayManager.startScanWindow] / [stopScanWindow] so the
 *     radio is only active ~10s out of every ~50s period.
 *   - The [RelayDataSource] comes from [RelayDataSourceProvider], set by :app with
 *     its temporary InMemoryRelayStore. Defaults to null — the service logs clearly
 *     if :app failed to set it before starting the service.
 *
 * Behavior:
 *   - [START_STICKY] so the OS recreates the service if it is killed, keeping the
 *     relay running between app launches.
 *   - [startForeground] with [RelayNotification] so the OS does not kill the process
 *     while the radio duty-cycle runs.
 *   - Exposes a [LocalBinder] so :app's MainActivity can reach the manager + data
 *     source for the temporary Start/Stop/Inject test controls.
 *
 * START_STICKY + process death (exact behavior, Day 5 correctness review):
 *   1. The OS kills the process (or the user's device reclaims memory).
 *   2. Android recreates this service via START_STICKY. onCreate() runs again. All
 *      static state is gone, so RelayDataSourceProvider.dataSource is null and the
 *      service falls back to [InMemoryFallbackStore] (a functional empty store).
 *   3. onStartCommand(null intent) is delivered → the relay + duty cycle restart.
 *   4. Later, if :app's MainActivity is reopened, it sets a NEW InMemoryRelayStore
 *      on the provider and binds to this service. The service KEEPS the fallback
 *      store it built in onCreate (it does not re-read the provider).
 *   This is a documented limitation of the temporary in-memory store: Inject writes
 *   land in MainActivity's store while received SOS land in the fallback store, and
 *   the two diverge for the service lifetime. No persistence is introduced (per Day 5
 *   review decision); Component C's Room-backed store resolves this.
 *
 * Intentionally no Hilt / DI — Component C owns that. Day 5 uses the provider seam.
 */
class RelayForegroundService : Service() {

    companion object {
        private const val TAG = "RelayForegroundService"
        const val ACTION_START = "com.sih.relay.action.START"
        const val ACTION_STOP = "com.sih.relay.action.STOP"

        /**
         * Day 5 contract target from day1-contracts §6.1: ~10s active scan window
         * every 45-60s → ~10s active + ~40s sleep.
         */
        const val ACTIVE_WINDOW_MILLIS = 10_000L
        const val SLEEP_WINDOW_MILLIS = 40_000L

        fun start(context: Context) {
            val intent = Intent(context, RelayForegroundService::class.java)
                .setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Requests a graceful stop.
         *
         * Delivers [ACTION_STOP] via a start command (NOT stopService): MainActivity
         * binds to this service with BIND_AUTO_CREATE, so a plain stopService() would
         * leave the relay running — a bound-but-stopped service is not destroyed until
         * the last client unbinds. Delivering ACTION_STOP makes onStartCommand stop the
         * duty cycle + relay immediately and then stopSelf().
         */
        fun stop(context: Context) {
            val intent = Intent(context, RelayForegroundService::class.java)
                .setAction(ACTION_STOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var relayManager: RelayManager
    private lateinit var dutyCycler: DutyCycler

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RelayForegroundService = this@RelayForegroundService
        fun getRelayManager(): RelayManager = relayManager
        fun getRelayDataSource(): RelayDataSource? = RelayDataSourceProvider.dataSource
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate — creating RelayManager + DutyCycler")

        RelayNotification.createChannel(this)

        val dataSource = RelayDataSourceProvider.dataSource
        if (dataSource == null) {
            Log.e(TAG, "RelayDataSourceProvider.dataSource is null — :app did not set it " +
                    "before the service was created (e.g. START_STICKY redelivery after process " +
                    "death). Using an empty in-memory fallback store. NOTE: the fallback is NOT " +
                    "the store :app's MainActivity uses — see class doc.")
        }

        relayManager = RelayManager(this, dataSource ?: InMemoryFallbackStore())
        dutyCycler = DutyCycler(
            activeWindowMillis = ACTIVE_WINDOW_MILLIS,
            sleepWindowMillis = SLEEP_WINDOW_MILLIS,
            onWindowStart = { relayManager.startScanWindow() },
            onWindowStop = { relayManager.stopScanWindow() },
            scope = serviceScope
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "onStartCommand action=$action")

        startForeground(
            RelayNotification.NOTIFICATION_ID,
            RelayNotification.build(this)
        )

        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stopping relay service (ACTION_STOP)")
                dutyCycler.stop()
                relayManager.stopRelay()
                // stopForeground(int) and STOP_FOREGROUND_REMOVE are API 24+; minSdk is 23.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Log.i(TAG, "Starting relay + duty cycle")
                relayManager.startRelay()
                dutyCycler.start()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(TAG, "onDestroy — stopping duty cycle and relay")
        dutyCycler.stop()
        relayManager.stopRelay()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Fallback store used when [RelayDataSourceProvider] has not been set.
     *
     * WHY this is a functional in-memory store and NOT a no-op stub:
     *   - After the OS kills our process, START_STICKY recreates this service. All
     *     static state — including RelayDataSourceProvider.dataSource (set by :app's
     *     MainActivity in its onCreate) — is wiped. The recreated service therefore
     *     always lands here.
     *   - A no-op stub would silently DROP every SOS received from peers and always
     *     report an empty UUID set, so RelayManager would treat every incoming SOS
     *     as "new" and re-broadcast it indefinitely (echo-loop risk, defeating the
     *     Day 4 echo guard in handleIncomingSos).
     *   - An empty in-memory store retains messages for the new process lifetime,
     *     restoring normal relay + echo-guard behavior.
     *
     * LIMITATION (temporary InMemoryRelayStore scaffolding, by design):
     *   - This fallback is NOT the same instance as the InMemoryRelayStore that
     *     MainActivity's "Inject Test SOS" writes into. After a process-death +
     *     START_STICKY redelivery, :app's MainActivity will set a NEW store on the
     *     provider, but this service keeps the fallback it built in onCreate.
     *   - Consequence: Inject writes go to MainActivity's store; received SOS go to
     *     this fallback store. The two diverge for that service lifetime. This is
     *     acceptable for Day 5 scaffolding (stores are ephemeral anyway) and is
     *     resolved when Component C replaces the temporary store with Room.
     */
    private class InMemoryFallbackStore : RelayDataSource {
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