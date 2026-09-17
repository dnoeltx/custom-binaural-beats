package com.dnoel.binauralbeats.core.playback

import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.EndReason
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import com.dnoel.binauralbeats.core.model.SessionRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * T043 and FR-025: the app must never present a session as running when it is not.
 *
 * The case this exists for: the process is killed overnight, by the system or by the
 * listener swiping the app away. Nothing gets to run any cleanup, so the stored record
 * still says the session is in progress. On next open that record has to be closed
 * honestly rather than left implying a session is still playing.
 */
class SessionReconcilerTest {

    private val profile = ListenerProfile(150.0, 400.0, ProfileSource.CALIBRATED, 0L)

    private fun runningRecord() = SessionRecord(
        startedAtEpochMillis = 1_000L,
        configuration = SessionConfiguration(),
        profile = profile,
        renderSeed = 7L,
    )

    @Test
    fun `a record left running with no service is closed as unknown`() {
        val state = AppState(lastSession = runningRecord())

        val reconciled = SessionReconciler.reconcile(state, serviceRunning = false, nowMillis = 9_000L)

        val record = reconciled.lastSession!!
        assertFalse(record.isRunning)
        assertEquals(EndReason.UNKNOWN, record.endReason)
        assertEquals(9_000L, record.endedAtEpochMillis)
    }

    @Test
    fun `a record left running with the service alive is untouched`() {
        val state = AppState(lastSession = runningRecord())
        val reconciled = SessionReconciler.reconcile(state, serviceRunning = true, nowMillis = 9_000L)
        assertSame(state, reconciled)
    }

    @Test
    fun `a record that already ended is never rewritten`() {
        // Overwriting a real ending with UNKNOWN would destroy the only evidence of how
        // the night actually finished.
        val ended = runningRecord().copy(
            endedAtEpochMillis = 5_000L,
            endReason = EndReason.STOPPED_BY_LISTENER,
        )
        val state = AppState(lastSession = ended)

        val reconciled = SessionReconciler.reconcile(state, serviceRunning = false, nowMillis = 9_000L)

        assertSame(state, reconciled)
        assertEquals(EndReason.STOPPED_BY_LISTENER, reconciled.lastSession!!.endReason)
    }

    @Test
    fun `a state with no session at all is untouched`() {
        val state = AppState()
        assertSame(state, SessionReconciler.reconcile(state, serviceRunning = false, nowMillis = 9_000L))
    }

    @Test
    fun `reconciling twice changes nothing the second time`() {
        val once = SessionReconciler.reconcile(
            AppState(lastSession = runningRecord()),
            serviceRunning = false,
            nowMillis = 9_000L,
        )
        val twice = SessionReconciler.reconcile(once, serviceRunning = false, nowMillis = 20_000L)
        assertSame(once, twice)
        assertEquals(9_000L, twice.lastSession!!.endedAtEpochMillis)
    }

    @Test
    fun `the rest of the stored state survives reconciliation`() {
        val state = AppState(
            profile = profile,
            settings = SessionConfiguration(carrierCount = 2),
            lastSession = runningRecord(),
        )

        val reconciled = SessionReconciler.reconcile(state, serviceRunning = false, nowMillis = 9_000L)

        assertEquals(profile, reconciled.profile)
        assertEquals(2, reconciled.settings.carrierCount)
    }
}
