package com.dnoel.binauralbeats.core.session

import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * T042: what the session record needs in order to make SC-005 checkable.
 *
 * SC-005 says every tone in a session lies inside the listener's saved range, "verifiable
 * from the retained record of the most recent session". That is only true if the record
 * says what was actually played. Because the scheduler is pure, the extremes can be
 * computed exactly from the session's own inputs rather than sampled from the audio
 * thread, which keeps the measurement out of the rendering path entirely.
 */
class ToneRangeTest {

    private val tuning = SessionTuning.MEASURED
    private val profile = ListenerProfile(150.0, 400.0, ProfileSource.CALIBRATED, 0L)

    private fun scheduler(seed: Long = 11L) =
        SessionScheduler(profile, SessionConfiguration(), tuning, seed)

    @Test
    fun `the reported range lies inside the listener's profile`() {
        val range = scheduler().toneRangeOver(8.hours)
        assertTrue(range.start >= profile.lowHz, "reported low ${range.start} is below the profile")
        assertTrue(range.endInclusive <= profile.highHz, "reported high ${range.endInclusive} is above")
    }

    @Test
    fun `the reported range covers the tones, not just the carrier centres`() {
        // A pair splits around its centre, and SC-005 is about tones. The lowest tone is
        // half a beat below the lowest centre.
        val scheduler = scheduler()
        val range = scheduler.toneRangeOver(2.hours)

        var at = kotlin.time.Duration.ZERO
        while (at <= 2.hours) {
            for (pair in scheduler.parametersAt(at).pairs) {
                assertTrue(pair.leftHz >= range.start - 0.01, "a tone at $at was below the reported range")
                assertTrue(pair.rightHz <= range.endInclusive + 0.01, "a tone at $at was above it")
            }
            at += 97.seconds
        }
    }

    @Test
    fun `a longer session visits at least as much as a shorter one`() {
        val short = scheduler().toneRangeOver(10.minutes)
        val long = scheduler().toneRangeOver(10.hours)
        assertTrue(long.start <= short.start + 0.01)
        assertTrue(long.endInclusive >= short.endInclusive - 0.01)
    }

    @Test
    fun `a zero length session reports the range it started at, not an empty one`() {
        val range = scheduler().toneRangeOver(kotlin.time.Duration.ZERO)
        assertTrue(range.start < range.endInclusive, "a session that barely ran still played something")
    }

    @Test
    fun `the reported range is reproducible from the seed`() {
        assertTrue(scheduler(seed = 5L).toneRangeOver(4.hours) == scheduler(seed = 5L).toneRangeOver(4.hours))
    }
}
