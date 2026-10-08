package com.dnoel.binauralbeats.core.diagnostics

import com.dnoel.binauralbeats.core.model.EndReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FR-029a: a bounded record of how recent sessions ended.
 *
 * This exists because a real night ran for nearly nine hours and the one number worth
 * having, the underrun count, had rotated out of the system log before morning. Twice.
 * A capped list of endings is not a sleep diary; it is the only way the person using the
 * app can tell whether it kept its promise overnight.
 */
class SessionDiagnosticsTest {

    private fun ending(startMillis: Long, minutes: Long, reason: EndReason = EndReason.STOPPED_BY_LISTENER) =
        SessionEnding(
            startedAtEpochMillis = startMillis,
            endedAtEpochMillis = startMillis + minutes * 60_000L,
            endReason = reason,
            underrunCount = 0,
        )

    @Test
    fun `an ending is added to the front, newest first`() {
        val first = ending(1_000L, 30)
        val second = ending(9_000L, 45)

        val log = SessionDiagnostics.record(SessionDiagnostics.record(emptyList(), first), second)

        assertEquals(listOf(second, first), log)
    }

    @Test
    fun `the log never grows past its cap, discarding the oldest`() {
        var log = emptyList<SessionEnding>()
        repeat(30) { index -> log = SessionDiagnostics.record(log, ending(index * 1_000L, 10)) }

        assertEquals(SessionDiagnostics.MAX_ENTRIES, log.size)
        assertEquals(29_000L, log.first().startedAtEpochMillis, "newest entry was lost")
        assertTrue(log.none { it.startedAtEpochMillis == 0L }, "the oldest entry was not discarded")
    }

    @Test
    fun `an ending knows how long the session ran`() {
        assertEquals(537L, ending(0L, 537).durationMinutes)
    }

    @Test
    fun `a session still running has no duration rather than a misleading zero`() {
        val running = SessionEnding(
            startedAtEpochMillis = 1_000L,
            endedAtEpochMillis = null,
            endReason = null,
            underrunCount = 0,
        )
        assertEquals(null, running.durationMinutes)
    }

    @Test
    fun `underruns are carried, because they are the reason this exists`() {
        val glitchy = ending(0L, 480).copy(underrunCount = 7)
        val log = SessionDiagnostics.record(emptyList(), glitchy)
        assertEquals(7, log.single().underrunCount)
    }

    @Test
    fun `a long clean night is distinguishable from a short one at a glance`() {
        val night = ending(0L, 537).copy(underrunCount = 0)
        val stub = ending(0L, 1).copy(underrunCount = 0)

        assertTrue(night.isFullNight)
        assertTrue(!stub.isFullNight)
    }

    @Test
    fun `an ending that was not the listener's choice is visible as such`() {
        val lost = ending(0L, 120, EndReason.OUTPUT_LOST)
        assertTrue(!lost.endedByListener)
        assertTrue(ending(0L, 120).endedByListener)
    }
}
