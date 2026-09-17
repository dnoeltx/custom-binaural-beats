package com.dnoel.binauralbeats.core.session

import com.dnoel.binauralbeats.core.model.BeatArc
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * T022: the beat rate arc over a night.
 *
 * Shape comes from the listening session (research.md M004): 1 to 2 Hz settles, 3 is
 * acceptable, 4 is poor and 6 is unsettling. So the default descends from near 3 into the
 * 1.5 to 2 band and holds, and nothing anywhere reaches 4.
 */
class BeatArcTest {

    private val tuning = SessionTuning.MEASURED
    private val profile = ListenerProfile(150.0, 400.0, ProfileSource.CALIBRATED, 0L)

    @Test
    fun `the default arc starts near the top of the acceptable band`() {
        val start = scheduler(BeatArc.DESCEND_THEN_HOLD).parametersAt(tuning.fadeDuration).beatRateHz
        assertEquals(tuning.openingBeatRateHz, start, 0.15)
        assertTrue(start < tuning.maxBeatRateHz, "the opening rate must stay under the measured ceiling")
    }

    @Test
    fun `the default arc descends without ever rising`() {
        val scheduler = scheduler(BeatArc.DESCEND_THEN_HOLD)
        var previous = scheduler.parametersAt(0.seconds).beatRateHz
        var at = 30.seconds
        while (at <= tuning.beatDescentDuration) {
            val now = scheduler.parametersAt(at).beatRateHz
            assertTrue(now <= previous + 1e-9, "beat rate rose from $previous to $now at $at")
            previous = now
            at += 30.seconds
        }
    }

    @Test
    fun `the descent completes on schedule and then holds steady all night`() {
        val scheduler = scheduler(BeatArc.DESCEND_THEN_HOLD)
        val settled = scheduler.parametersAt(tuning.beatDescentDuration).beatRateHz

        assertEquals(tuning.holdBeatRateHz, settled, 0.05)
        assertTrue(settled in 2.5..3.5, "settled rate $settled is outside the band the listener actually sleeps to")

        for (hour in 1..10) {
            assertEquals(settled, scheduler.parametersAt(hour.hours).beatRateHz, 1e-6)
        }
    }

    @Test
    fun `the constant arc does not move at all`() {
        val scheduler = scheduler(BeatArc.CONSTANT)
        val rate = scheduler.parametersAt(1.minutes).beatRateHz
        for (hour in 1..10) {
            assertEquals(rate, scheduler.parametersAt(hour.hours).beatRateHz, 1e-9)
        }
        assertTrue(rate in 2.5..3.5, "a constant night should sit in the settling band, was $rate")
    }

    @Test
    fun `the varying arc stays inside the acceptable band and changes slowly`() {
        val scheduler = scheduler(BeatArc.DESCEND_THEN_VARY)
        var previous = scheduler.parametersAt(tuning.beatDescentDuration).beatRateHz
        var at = tuning.beatDescentDuration + 30.seconds
        while (at <= 10.hours) {
            val now = scheduler.parametersAt(at).beatRateHz
            assertTrue(now in 2.0..3.9, "varying arc reached $now at $at")
            assertTrue(
                abs(now - previous) / 30.0 <= tuning.maxBeatRateChangePerSecond + 1e-9,
                "beat rate moved from $previous to $now in 30s at $at",
            )
            previous = now
            at += 30.seconds
        }
    }

    @Test
    fun `no arc ever reaches the rate judged unsettling`() {
        for (arc in BeatArc.entries) {
            val scheduler = scheduler(arc)
            var at = 0.seconds
            while (at <= 10.hours) {
                assertTrue(scheduler.parametersAt(at).beatRateHz < 4.0, "$arc reached 4 Hz at $at")
                at += 1.minutes
            }
        }
    }

    private fun scheduler(arc: BeatArc) = SessionScheduler(
        profile = profile,
        configuration = SessionConfiguration(beatArc = arc),
        tuning = tuning,
        seed = 7L,
    )
}
