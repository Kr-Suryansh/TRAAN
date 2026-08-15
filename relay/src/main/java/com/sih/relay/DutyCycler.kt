package com.sih.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Duty-cycles a Nearby Connections radio workload: it repeatedly opens a short
 * active scan/advertise window, holds it open for [activeWindowMillis], closes
 * it, then sleeps for [sleepWindowMillis] before the next window.
 *
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │  Day 5 — the primary battery lever.                                         │
 * │  Contract source: antigravity-build-prompts -.md — Component Prompt 1,      │
 * │  and day1-contracts-and-repo-setup.md §6.1: ~10s scan window every 45-60s.  │
 * │  Continuous advertising/discovery is the largest battery cost in the mesh.  │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * The cycler is deliberately decoupled from [RelayManager]:
 *   - It knows NOTHING about SOS state, connections, or the relay store.
 *   - It ONLY toggles a window open/closed via [onWindowStart]/[onWindowStop].
 *   - This keeps the SOS store and connection state untouched across windows —
 *     the relay does not lose data when a scan window closes.
 *
 * The timing is injected as constructor values (and the delay is injectable) so
 * the component is deterministic under JVM tests (see DutyCyclerTest) and the
 * schedule can be tuned later (Day 10) without rewriting RelayManager.
 *
 * States:
 *   - [DutyState.STOPPED]  : cycler not running.
 *   - [DutyState.ACTIVE]   : window open (onWindowStart just fired; onWindowStop pending).
 *   - [DutyState.SLEEPING] : window closed; waiting for the next open.
 *
 * @param activeWindowMillis How long each scan/advertise window stays open (ms).
 *                           Contract target ~10_000.
 * @param sleepWindowMillis  How long the radio is idle between windows (ms).
 *                           Contract target ~35_000-50_000 (45-60s total period).
 * @param onWindowStart      Called when a window opens (starts advertising+discovery).
 * @param onWindowStop       Called when a window closes (stops advertising+discovery).
 * @param delayFn            Injectable delay used by the loop (for tests).
 */
class DutyCycler(
    private val activeWindowMillis: Long,
    private val sleepWindowMillis: Long,
    private val onWindowStart: suspend () -> Unit,
    private val onWindowStop: suspend () -> Unit,
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    enum class DutyState { STOPPED, ACTIVE, SLEEPING }

    private val scope = scope

    @Volatile
    var state: DutyState = DutyState.STOPPED
        private set

    private var job: Job? = null

    /** True while the cycler loop is running. */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Starts the duty-cycle loop. Safe to call more than once — subsequent calls
     * are no-ops while a loop is already active.
     */
    fun start() {
        if (job?.isActive == true) {
            return
        }
        state = DutyState.STOPPED
        job = scope.launch { runLoop() }
    }

    /**
     * Stops the duty-cycle loop. The current window is NOT force-closed here;
     * callers that need a clean close invoke [onWindowStop] themselves (e.g. via
     * the owning service's stopRelay()). The loop simply stops scheduling new
     * windows.
     */
    fun stop() {
        job?.cancel()
        job = null
        state = DutyState.STOPPED
    }

    private suspend fun runLoop() {
        // The loop is cancelled via the injected scope's cancellation; delay()
        // is cancellable so a cancel() on the job throws CancellationException
        // here and unwinds without scheduling further windows.
        while (true) {
            state = DutyState.ACTIVE
            onWindowStart()
            delayFn(activeWindowMillis)

            onWindowStop()
            state = DutyState.SLEEPING
            delayFn(sleepWindowMillis)
        }
    }
}