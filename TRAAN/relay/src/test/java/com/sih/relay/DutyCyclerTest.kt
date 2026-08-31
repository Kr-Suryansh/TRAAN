package com.sih.relay

import com.sih.relay.DutyCycler.DutyState
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic JVM tests for [DutyCycler] using a virtual clock
 * (kotlinx-coroutines-test TestScope), so timing assertions do not sleep.
 *
 * Contract target from antigravity/day1-contracts §6.1:
 *   ~10s active window every 45-60s period → sleep ~35-50s.
 * These tests use small synthetic values to prove ordering & state transitions.
 */
class DutyCyclerTest {

    private data class Cycle(
        val activeMillis: Long,
        val sleepMillis: Long
    )

    @Test
    fun `cycle - starts ACTIVE and fires onWindowStart first`() = runTest {
        val events = mutableListOf<String>()
        val cycler = DutyCycler(
            activeWindowMillis = 10_000,
            sleepWindowMillis = 40_000,
            onWindowStart = { events.add("START") },
            onWindowStop = { events.add("STOP") },
            scope = this
        )

        cycler.start()
        runCurrent()

        assertEquals(DutyState.ACTIVE, cycler.state)
        assertTrue(cycler.isRunning)
        assertEquals(listOf("START"), events)
        cycler.stop()
    }

    @Test
    fun `cycle - window closes after active duration then sleeps`() = runTest {
        val events = mutableListOf<String>()
        val cycler = DutyCycler(
            activeWindowMillis = 10_000,
            sleepWindowMillis = 40_000,
            onWindowStart = { events.add("START") },
            onWindowStop = { events.add("STOP") },
            scope = this
        )

        cycler.start()
        runCurrent()

        // Advance past the active window → STOP fires, state becomes SLEEPING.
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(DutyState.SLEEPING, cycler.state)
        assertEquals(listOf("START", "STOP"), events)
        cycler.stop()
    }

    @Test
    fun `cycle - next window opens after the sleep duration`() = runTest {
        val events = mutableListOf<String>()
        val cycler = DutyCycler(
            activeWindowMillis = 10_000,
            sleepWindowMillis = 40_000,
            onWindowStart = { events.add("START") },
            onWindowStop = { events.add("STOP") },
            scope = this
        )

        cycler.start()
        runCurrent()
        advanceTimeBy(10_000) // active window elapses
        runCurrent()
        advanceTimeBy(40_000) // sleep elapses
        runCurrent()

        assertEquals(DutyState.ACTIVE, cycler.state)
        assertEquals(listOf("START", "STOP", "START"), events)
        cycler.stop()
    }

    @Test
    fun `full cycle - repeat two full windows`() = runTest {
        val events = mutableListOf<String>()
        val cycler = DutyCycler(
            activeWindowMillis = 5_000,
            sleepWindowMillis = 20_000,
            onWindowStart = { events.add("START") },
            onWindowStop = { events.add("STOP") },
            scope = this
        )

        cycler.start()
        runCurrent()

        // Two full periods: each = active(5s) + sleep(20s).
        repeat(2) {
            advanceTimeBy(5_000)
            runCurrent()
            advanceTimeBy(20_000)
            runCurrent()
        }

        assertEquals(listOf("START", "STOP", "START", "STOP", "START"), events)
        cycler.stop()
    }

    @Test
    fun `state stays ACTIVE during the open window`() = runTest {
        val cycler = DutyCycler(
            activeWindowMillis = 10_000,
            sleepWindowMillis = 40_000,
            onWindowStart = {},
            onWindowStop = {},
            scope = this
        )

        cycler.start()
        runCurrent()
        // Partway through the active window, the window is still open.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(DutyState.ACTIVE, cycler.state)
        cycler.stop()
    }

    @Test
    fun `start is idempotent - second start does not restart the loop`() = runTest {
        var startCalls = 0
        val cycler = DutyCycler(
            activeWindowMillis = 10_000,
            sleepWindowMillis = 40_000,
            onWindowStart = { startCalls++ },
            onWindowStop = {},
            scope = this
        )

        cycler.start()
        runCurrent()
        cycler.start() // second start should be a no-op
        runCurrent()

        assertEquals(1, startCalls)
        cycler.stop()
    }

    @Test
    fun `stop halts the loop and returns state to STOPPED`() = runTest {
        var startCalls = 0
        val cycler = DutyCycler(
            activeWindowMillis = 1_000,
            sleepWindowMillis = 1_000,
            onWindowStart = { startCalls++ },
            onWindowStop = {},
            scope = this
        )

        cycler.start()
        runCurrent()
        cycler.stop()

        assertFalse(cycler.isRunning)
        assertEquals(DutyState.STOPPED, cycler.state)

        // Advance time; no further windows should fire after stop.
        val callsBefore = startCalls
        advanceTimeBy(100_000)
        runCurrent()
        assertEquals(callsBefore, startCalls)
    }

    @Test
    fun `isRunning is false before start`() {
        val cycler = DutyCycler(
            activeWindowMillis = 10_000,
            sleepWindowMillis = 40_000,
            onWindowStart = {},
            onWindowStop = {}
        )
        assertFalse(cycler.isRunning)
        assertEquals(DutyState.STOPPED, cycler.state)
    }
}