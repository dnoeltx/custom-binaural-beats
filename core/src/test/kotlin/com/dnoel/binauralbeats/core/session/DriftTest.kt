package com.dnoel.binauralbeats.core.session

import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * T023: slow drift (FR-004a).
 *
 * The listening session (research.md M005) put audibility near 60 Hz per minute, and the
 * limit at 10. Drift must therefore be real enough that the sound is not a fixture, slow
 * enough to be imperceptible, and bounded, since it has to stay inside the range and
 * therefore reverse. The two-minute "up then back" file that shipped this design was
 * judged "nothing remarkable".
 */
class DriftTest {

    private val tuning = SessionTuning.MEASURED
    private val profile = ListenerProfile(150.0, 400.0, ProfileSource.CALIBRATED, 0L)

    private fun scheduler(seed: Long = 5L) =
        SessionScheduler(profile, SessionConfiguration(), tuning, seed)

    @Test
    fun `carriers actually move over a night`() {
        // The opposite failure to drifting too fast: a drift so small it is decorative.
        val scheduler = scheduler()
        val first = scheduler.parametersAt(1.hours).pairs.map { it.centreHz }
        val later = scheduler.parametersAt(4.hours).pairs.map { it.centreHz }
        val moved = first.zip(later).map { (a, b) -> abs(a - b) }
        assertTrue(moved.any { it > 5.0 }, "carriers barely moved in three hours: $moved")
    }

    @Test
    fun `drift never exceeds the measured limit`() {
        val scheduler = scheduler()
        val step = 15.seconds
        var at = Duration.ZERO
        var previous = scheduler.parametersAt(at).pairs.map { it.centreHz }
        while (at < 10.hours) {
            at += step
            val now = scheduler.parametersAt(at).pairs.map { it.centreHz }
            for (i in now.indices) {
                val hzPerMinute = abs(now[i] - previous[i]) * (60.0 / step.inWholeSeconds)
                assertTrue(
                    hzPerMinute <= tuning.maxDriftHzPerMinute + 1e-6,
                    "carrier $i drifted at $hzPerMinute Hz per minute at $at",
                )
            }
            previous = now
        }
    }

    @Test
    fun `drift reverses inside the range rather than running to an edge`() {
        val scheduler = scheduler()
        // Sampled every 23 seconds rather than every minute: a sine sampled on a period
        // that happens to divide its own can alias into looking almost static, which is a
        // property of the sampling and not of the audio.
        val centres = (0..1560).map { scheduler.parametersAt((it * 23).seconds).pairs.first().centreHz }

        assertTrue(
            centres.max() - centres.min() > 2.0,
            "carrier barely moved across the night: span ${centres.max() - centres.min()}",
        )
        assertTrue(centres.zipWithNext().any { (a, b) -> b > a + 0.01 }, "never drifted upward")
        assertTrue(
            centres.zipWithNext().any { (a, b) -> b < a - 0.01 },
            "never reversed direction, so it must be pinned at an edge",
        )
        assertTrue(
            centres.all { it >= profile.lowHz && it <= profile.highHz },
            "drift left the profile range",
        )
    }

    @Test
    fun `drift keeps the measured spacing between carriers at all times`() {
        val scheduler = scheduler()
        var at = Duration.ZERO
        while (at <= 10.hours) {
            val centres = scheduler.parametersAt(at).pairs.map { it.centreHz }.sorted()
            for (i in 1 until centres.size) {
                assertTrue(
                    centres[i] - centres[i - 1] >= tuning.minCarrierSpacingHz - 0.001,
                    "drift pushed carriers to ${centres[i - 1]} and ${centres[i]} at $at",
                )
            }
            at += 31.seconds
        }
    }

    @Test
    fun `drift is reproducible from the seed`() {
        val a = scheduler(seed = 77L).parametersAt(6.hours).pairs
        val b = scheduler(seed = 77L).parametersAt(6.hours).pairs
        assertTrue(a == b, "the same seed gave different drift")
    }
}
