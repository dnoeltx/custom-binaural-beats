package com.dnoel.binauralbeats.core.session

import com.dnoel.binauralbeats.core.model.BeatArc
import com.dnoel.binauralbeats.core.model.EndBehavior
import com.dnoel.binauralbeats.core.model.ListenerProfile
import com.dnoel.binauralbeats.core.model.PerceptualBounds
import com.dnoel.binauralbeats.core.model.ProfileSource
import com.dnoel.binauralbeats.core.model.SessionConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * T021: the scheduler turns a profile, a configuration and elapsed time into the audio's
 * parameters right now. It is pure, so a ten hour night is a loop rather than a wait.
 *
 * Every limit it is held to comes from the listening session recorded in research.md,
 * and is supplied as [SessionTuning] rather than written into the code, because those
 * numbers are one listener's thresholds (see "These values must be injected").
 */
class SessionSchedulerTest {

    private val tuning = SessionTuning.MEASURED
    private val wideProfile = profile(150.0, 400.0)   // 250 Hz wide, holds three at 100 Hz
    private val narrowProfile = profile(150.0, 280.0) // 130 Hz wide, holds two
    private val tightProfile = profile(180.0, 260.0)  // 80 Hz wide, holds only one

    @Test
    fun `every carrier stays inside the listener's range`() {
        val scheduler = scheduler(wideProfile)
        forEachSample(10.hours) { at ->
            for (pair in scheduler.parametersAt(at).pairs) {
                assertTrue(
                    pair.leftHz >= wideProfile.lowHz && pair.rightHz <= wideProfile.highHz,
                    "pair ${pair.leftHz}/${pair.rightHz} left the range at $at",
                )
            }
        }
    }

    @Test
    fun `every carrier stays inside the perceptible bound`() {
        forEachSample(10.hours) { at ->
            for (pair in scheduler(wideProfile).parametersAt(at).pairs) {
                assertTrue(PerceptualBounds.isPairPerceptible(pair.leftHz, pair.rightHz))
            }
        }
    }

    @Test
    fun `all active pairs share one beat rate`() {
        val scheduler = scheduler(wideProfile)
        forEachSample(10.hours) { at ->
            val params = scheduler.parametersAt(at)
            val rates = params.pairs.map { abs(it.rightHz - it.leftHz) }
            for (rate in rates) {
                assertEquals(params.beatRateHz, rate, 0.0001, "pairs disagreed at $at")
            }
        }
    }

    @Test
    fun `simultaneous carriers never sit closer than the measured minimum spacing`() {
        val scheduler = scheduler(wideProfile)
        forEachSample(10.hours) { at ->
            val centres = scheduler.parametersAt(at).pairs.map { it.centreHz }.sorted()
            for (i in 1 until centres.size) {
                assertTrue(
                    centres[i] - centres[i - 1] >= tuning.minCarrierSpacingHz - 0.001,
                    "carriers ${centres[i - 1]} and ${centres[i]} were too close at $at",
                )
            }
        }
    }

    @Test
    fun `a range wide enough gets three carriers and a narrow one gets two`() {
        // Derived from the range, not chosen by the listener: research.md M003.
        assertEquals(3, scheduler(wideProfile).parametersAt(1.minutes).pairs.size)
        assertEquals(2, scheduler(narrowProfile).parametersAt(1.minutes).pairs.size)
    }

    @Test
    fun `a range too tight for two carriers falls back to one rather than crowding them`() {
        // 80 Hz of range cannot hold two carriers 100 Hz apart. Spacing is a measured
        // acoustic threshold and carrier count is a preference, so the count gives way.
        // FR-020a now requires calibration never to produce a range this tight, so in
        // practice the fallback should not be reached. It stays tested because a profile
        // can also be hand-edited in settings, and because a scheduler that crowded
        // carriers instead would undo the measured spacing.
        assertEquals(1, scheduler(tightProfile).parametersAt(1.minutes).pairs.size)
    }

    @Test
    fun `the configured carrier count is a maximum, not a demand`() {
        val twoRequested = scheduler(wideProfile, SessionConfiguration(carrierCount = 2))
        assertEquals(2, twoRequested.parametersAt(1.minutes).pairs.size)
    }

    @Test
    fun `nothing changes faster than the measured limits`() {
        val scheduler = scheduler(wideProfile)
        val step = 250.milliseconds
        var previous = scheduler.parametersAt(Duration.ZERO)
        var at = step
        while (at < 10.hours) {
            val now = scheduler.parametersAt(at)
            val seconds = step.inWholeMilliseconds / 1000.0

            assertTrue(
                abs(now.masterGain - previous.masterGain) / seconds <= tuning.maxGainChangePerSecond + 1e-6,
                "gain moved too fast at $at",
            )
            for (i in now.pairs.indices) {
                val moved = abs(now.pairs[i].centreHz - previous.pairs[i].centreHz)
                assertTrue(
                    moved / seconds * 60.0 <= tuning.maxDriftHzPerMinute + 1e-6,
                    "carrier $i moved ${moved}Hz in ${seconds}s at $at",
                )
            }
            previous = now
            at += step
        }
    }

    @Test
    fun `a session opens from silence over the measured fade`() {
        val scheduler = scheduler(wideProfile)
        assertEquals(0.0, scheduler.parametersAt(Duration.ZERO).masterGain, 1e-9)
        assertTrue(scheduler.parametersAt(tuning.fadeDuration / 2).masterGain in 0.1..0.9)
        assertEquals(1.0, scheduler.parametersAt(tuning.fadeDuration).masterGain, 1e-6)
        assertEquals(1.0, scheduler.parametersAt(3.hours).masterGain, 1e-6)
    }

    @Test
    fun `a session with a duration fades back to silence exactly at the end`() {
        val eightHours = 8.hours
        val scheduler = scheduler(
            wideProfile,
            SessionConfiguration(endBehavior = EndBehavior.AfterDuration(eightHours.inWholeMilliseconds)),
        )

        assertEquals(1.0, scheduler.parametersAt(eightHours - tuning.fadeDuration).masterGain, 1e-6)
        assertTrue(scheduler.parametersAt(eightHours - tuning.fadeDuration / 2).masterGain in 0.1..0.9)
        assertEquals(0.0, scheduler.parametersAt(eightHours).masterGain, 1e-9)
        assertEquals(0.0, scheduler.parametersAt(eightHours + 1.minutes).masterGain, 1e-9)
    }

    @Test
    fun `a session that runs until stopped never fades on its own`() {
        val scheduler = scheduler(wideProfile)
        assertEquals(1.0, scheduler.parametersAt(12.hours).masterGain, 1e-6)
    }

    @Test
    fun `the same inputs always produce the same parameters`() {
        val a = scheduler(wideProfile, seed = 99L).parametersAt(4.hours)
        val b = scheduler(wideProfile, seed = 99L).parametersAt(4.hours)
        assertEquals(a, b)
    }

    @Test
    fun `a different seed produces a different night`() {
        val a = scheduler(wideProfile, seed = 1L)
        val b = scheduler(wideProfile, seed = 2L)
        val differs = (1..60).any { minute ->
            a.parametersAt(minute.minutes).pairs != b.parametersAt(minute.minutes).pairs
        }
        assertTrue(differs, "two seeds produced identical carrier movement")
    }

    private fun scheduler(
        profile: ListenerProfile,
        configuration: SessionConfiguration = SessionConfiguration(),
        seed: Long = 42L,
    ) = SessionScheduler(profile, configuration, tuning, seed)

    private fun profile(lowHz: Double, highHz: Double) =
        ListenerProfile(lowHz, highHz, ProfileSource.CALIBRATED, 0L)

    /** Samples a long session at a coarse interval, for properties that must hold always. */
    private fun forEachSample(over: Duration, every: Duration = 17.seconds, check: (Duration) -> Unit) {
        var at = Duration.ZERO
        while (at <= over) {
            check(at)
            at += every
        }
    }

    @Test
    fun `beat rate follows the measured arc and never reaches four`() {
        val scheduler = scheduler(wideProfile, SessionConfiguration(beatArc = BeatArc.DESCEND_THEN_HOLD))
        forEachSample(10.hours) { at ->
            val rate = scheduler.parametersAt(at).beatRateHz
            assertTrue(rate < 4.0, "beat rate reached $rate at $at, which was judged unsettling")
            assertTrue(rate > 0.5, "beat rate fell to $rate at $at")
        }
    }
}
