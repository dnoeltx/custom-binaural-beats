package com.dnoel.binauralbeats.core.playback

import com.dnoel.binauralbeats.core.model.AppState
import com.dnoel.binauralbeats.core.model.EndReason
import com.dnoel.binauralbeats.core.model.SessionEnding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FR-029a: the bounded record of how recent sessions ended.
 *
 * This exists because of a real failure. A session stopped after about two hours overnight
 * and nothing survived to say why: the single most-recent-session record had been
 * overwritten by a later test, and the system log had rotated. Twenty endings cost almost
 * nothing and turn "it stopped, we think" into a timestamp and a reason.
 */
class SessionDiagnosticsTest {

    private fun ending(
        startedAt: Long,
        endedAt: Long = startedAt + 60_000L,
        reason: EndReason = EndReason.STOPPED_BY_LISTENER,
        underruns: Int = 0,
    ) = SessionEnding(startedAt, endedAt, reason, underruns)

    @Test
    fun `an ending is appended to an empty record`() {
        val state = SessionDiagnostics.record(AppState(), ending(1_000L))
        assertEquals(1, state.recentEndings.size)
        assertEquals(EndReason.STOPPED_BY_LISTENER, state.recentEndings.first().endReason)
    }

    @Test
    fun `endings accumulate, newest last`() {
        var state = AppState()
        state = SessionDiagnostics.record(state, ending(1_000L))
        state = SessionDiagnostics.record(state, ending(2_000L))
        state = SessionDiagnostics.record(state, ending(3_000L))

        assertEquals(listOf(1_000L, 2_000L, 3_000L), state.recentEndings.map { it.startedAtEpochMillis })
    }

    @Test
    fun `the record is capped, dropping the oldest`() {
        // Unbounded growth would turn a diagnostics aid into the sleep diary FR-029 refuses.
        var state = AppState()
        repeat(SessionDiagnostics.MAX_ENTRIES + 5) { index ->
            state = SessionDiagnostics.record(state, ending(index.toLong() * 1_000L))
        }

        assertEquals(SessionDiagnostics.MAX_ENTRIES, state.recentEndings.size)
        assertEquals(5_000L, state.recentEndings.first().startedAtEpochMillis)
    }

    @Test
    fun `starting a new session does not clear the record`() {
        // The whole point: the most recent session record is overwritten every night, and
        // that is what destroyed the evidence last time.
        var state = SessionDiagnostics.record(AppState(), ending(1_000L))
        state = state.copy(lastSession = null)
        assertEquals(1, state.recentEndings.size)
    }

    @Test
    fun `an underrun count is kept, because it is the number an overnight run exists to produce`() {
        val state = SessionDiagnostics.record(AppState(), ending(1_000L, underruns = 7))
        assertEquals(7, state.recentEndings.first().underrunCount)
    }

    @Test
    fun `the rest of the stored state is untouched`() {
        val before = AppState()
        val after = SessionDiagnostics.record(before, ending(1_000L))
        assertEquals(before.profile, after.profile)
        assertEquals(before.settings, after.settings)
        assertEquals(before.schemaVersion, after.schemaVersion)
    }

    @Test
    fun `a session's duration can be read back from an entry`() {
        val state = SessionDiagnostics.record(AppState(), ending(1_000L, endedAt = 7_200_000L))
        assertEquals(7_199_000L, state.recentEndings.first().durationMillis)
    }

    @Test
    fun `an ending that arrives out of order is still kept`() {
        // Clocks move. Dropping or reordering entries would hide exactly the oddity worth
        // seeing, so the record is append-only and reports what it was given.
        var state = SessionDiagnostics.record(AppState(), ending(5_000L))
        state = SessionDiagnostics.record(state, ending(2_000L))
        assertEquals(listOf(5_000L, 2_000L), state.recentEndings.map { it.startedAtEpochMillis })
    }

    @Test
    fun `a summary reads as one line per ending, newest first`() {
        var state = AppState()
        state = SessionDiagnostics.record(state, ending(1_000L, 3_600_000L, EndReason.OUTPUT_LOST, 2))
        state = SessionDiagnostics.record(state, ending(4_000_000L, 5_000_000L, EndReason.UNKNOWN, 0))

        val lines = SessionDiagnostics.summarize(state).lines().filter { it.isNotBlank() }

        assertEquals(2, lines.size)
        assertTrue(lines.first().contains("UNKNOWN"), "newest should come first: ${lines.first()}")
        assertTrue(lines.any { it.contains("OUTPUT_LOST") })
        assertTrue(lines.any { it.contains("underruns=2") }, "underruns missing from $lines")
    }
}
