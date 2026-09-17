package com.dnoel.binauralbeats.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * FR-024: what happens when the earbuds go away mid-session.
 *
 * The rules the owner chose: pause and wait, never fall back to the phone speaker, resume
 * with a fade if the output returns inside a grace period, and end the session cleanly if
 * it does not. A dead battery must not leave a session claiming to run all night.
 */
class OutputLossPolicyTest {

    private val grace = 3.minutes

    @Test
    fun `losing output pauses rather than continuing`() {
        assertEquals(
            OutputAction.PAUSE_AND_WAIT,
            OutputLossPolicy.onOutputLost(playing = true),
        )
    }

    @Test
    fun `losing output when already paused changes nothing`() {
        assertEquals(
            OutputAction.NONE,
            OutputLossPolicy.onOutputLost(playing = false),
        )
    }

    @Test
    fun `output returning inside the grace period resumes with a fade`() {
        assertEquals(
            OutputAction.RESUME_WITH_FADE,
            OutputLossPolicy.onOutputReturned(awayFor = 30.seconds, grace = grace),
        )
    }

    @Test
    fun `output returning exactly at the grace period still resumes`() {
        // A boundary that rejects is a boundary that will annoy someone at 3am.
        assertEquals(
            OutputAction.RESUME_WITH_FADE,
            OutputLossPolicy.onOutputReturned(awayFor = grace, grace = grace),
        )
    }

    @Test
    fun `output returning after the grace period does not resurrect the session`() {
        assertEquals(
            OutputAction.END_SESSION,
            OutputLossPolicy.onOutputReturned(awayFor = grace + 1.seconds, grace = grace),
        )
    }

    @Test
    fun `the grace period expiring while still absent ends the session`() {
        assertEquals(
            OutputAction.END_SESSION,
            OutputLossPolicy.onGraceExpired(),
        )
    }

    @Test
    fun `a session never falls back to another output`() {
        // Stated as a test because it is the one behaviour with a safety edge: waking to
        // a phone speaker playing tones at 3am is exactly the surprise this app forbids.
        assertEquals(false, OutputLossPolicy.MAY_FALL_BACK_TO_ANOTHER_OUTPUT)
    }
}
